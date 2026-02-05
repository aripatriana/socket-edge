package com.socket.edge.core.socket;

import com.socket.edge.constant.NodeRole;
import com.socket.edge.constant.SocketState;
import com.socket.edge.constant.SocketType;
import com.socket.edge.core.MessageContextProcess;
import com.socket.edge.core.SocketTelemetry;
import com.socket.edge.core.TelemetryRegistry;
import com.socket.edge.model.SocketEndpoint;
import com.socket.edge.utils.ByteDecoder;
import com.socket.edge.utils.ByteEncoder;
import com.socket.edge.utils.IsoParser;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import io.netty.handler.codec.LengthFieldPrepender;
import io.netty.util.concurrent.DefaultThreadFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Default implementation of a server-side socket using Netty.
 *
 * <p>{@code DefaultServerSocket} listens for inbound TCP connections and
 * processes incoming messages through a configurable Netty pipeline.
 * It supports:
 * <ul>
 *   <li>Cluster-aware activation (MASTER only)</li>
 *   <li>Dynamic allowlist of permitted endpoints</li>
 *   <li>Channel pooling and lifecycle management</li>
 *   <li>Telemetry integration</li>
 *   <li>ISO message decoding and forwarding</li>
 * </ul>
 *
 * <p>In cluster mode, the server socket binds to the port and accepts
 * connections only when the node role is {@link NodeRole#MASTER}.
 * When running as {@link NodeRole#SLAVE}, the socket remains in
 * {@link SocketState#STANDBY}.</p>
 *
 * <p>Thread safety:
 * <ul>
 *   <li>Lifecycle methods are synchronized</li>
 *   <li>{@link #socketState} and {@link #running} use volatile visibility</li>
 *   <li>Netty event loops manage I/O concurrency</li>
 * </ul>
 *
 * @author Ari Patriana
 * @since 1.0.0
 */
public class DefaultServerSocket extends AbstractSocket {

    private static final Logger log = LoggerFactory.getLogger(DefaultServerSocket.class);

    /**
     * Current socket state.
     */
    private volatile SocketState socketState = SocketState.DOWN;

    /**
     * Server listening port.
     */
    private final int port;

    /**
     * Netty boss event loop group (acceptor).
     */
    private EventLoopGroup boss;

    /**
     * Netty worker event loop group (I/O workers).
     */
    private EventLoopGroup worker;

    /**
     * Server channel bound to the listening port.
     */
    private Channel serverChannel;

    /**
     * Indicates whether the socket is running.
     */
    private volatile boolean running = false;

    /**
     * ISO message parser.
     */
    private IsoParser parser;

    /**
     * Message forwarding processor.
     */
    private MessageContextProcess forward;

    /**
     * Channel pool associated with this socket.
     */
    private SocketChannelPooling channelPool;

    /**
     * Socket type.
     */
    private final SocketType type = SocketType.SERVER;

    /**
     * Socket telemetry.
     */
    private SocketTelemetry socketTelemetry;

    /**
     * Telemetry registry.
     */
    private TelemetryRegistry telemetryRegistry;

    /**
     * Creates a new server socket.
     *
     * @param cluster           whether cluster mode is enabled
     * @param name              socket name
     * @param host              bind host
     * @param port              bind port
     * @param allowlist         list of allowed remote endpoints
     * @param telemetryRegistry telemetry registry
     * @param parser            ISO message parser
     * @param forward           message forwarding processor
     */
    public DefaultServerSocket(
            boolean cluster,
            String name,
            String host,
            int port,
            List<SocketEndpoint> allowlist,
            TelemetryRegistry telemetryRegistry,
            IsoParser parser,
            MessageContextProcess forward
    ) {
        super(
                cluster,
                String.format("%s-server-%d", name, port),
                name,
                host,
                port,
                telemetryRegistry
        );

        this.port = port;
        this.parser = parser;
        this.forward = forward;

        allowlist.forEach(this::registerEndpoint);

        this.telemetryRegistry = telemetryRegistry;
        this.channelPool = new SocketChannelPooling(this);

        this.boss = new NioEventLoopGroup(
                1,
                new DefaultThreadFactory(
                        String.format("%s-server-el-b", getName())
                )
        );

        this.worker = new NioEventLoopGroup(
                new DefaultThreadFactory(
                        String.format("%s-server-el-w", getName())
                )
        );
    }

    /**
     * Starts the server socket.
     *
     * <p>In cluster mode, the socket will only bind and listen
     * if the node role is {@link NodeRole#MASTER}.</p>
     */
    @Override
    public synchronized void start() throws InterruptedException {
        if (running) {
            log.warn("{} already running", getId());
            return;
        }

        log.debug(
                isCluster()
                        ? "Start socket app id={} as {}"
                        : "Start socket app id={}",
                getId(),
                getRole()
        );

        running = true;
        startTime = System.currentTimeMillis();

        if (!isCluster() || getRole() == NodeRole.MASTER) {
            activate();
        } else {
            socketState = SocketState.STANDBY;
            log.info("{} started in standby mode", getId());
        }
    }

    /**
     * Activates the server socket by binding to the configured port
     * and accepting incoming connections.
     *
     * @throws InterruptedException if bind is interrupted
     */
    @Override
    public synchronized void activate() throws InterruptedException {
        if (!running) {
            log.warn("{} not running, cannot activate", getId());
            return;
        }

        if (socketState == SocketState.ACTIVE) {
            log.warn("{} already active", getId());
            return;
        }

        try {
            ChannelFuture future = new ServerBootstrap()
                    .group(boss, worker)
                    .channel(NioServerSocketChannel.class)
                    .option(ChannelOption.SO_REUSEADDR, true)
                    .childOption(ChannelOption.SO_KEEPALIVE, true)
                    .childOption(ChannelOption.TCP_NODELAY, true)
                    .childHandler(new ChannelInitializer<io.netty.channel.socket.SocketChannel>() {
                        @Override
                        protected void initChannel(io.netty.channel.socket.SocketChannel ch) {
                            ch.pipeline().addLast(new ChannelInboundAdapter(channelPool));
                            ch.pipeline().addLast(new LengthFieldBasedFrameDecoder(
                                    Integer.MAX_VALUE, 0, 2, 0, 2
                            ));
                            ch.pipeline().addLast(new ByteDecoder());
                            ch.pipeline().addLast(new ServerInboundHandler(
                                    DefaultServerSocket.this, parser, forward
                            ));
                            ch.pipeline().addLast(new ByteEncoder());
                            ch.pipeline().addLast(new LengthFieldPrepender(2));
                        }
                    })
                    .bind(port)
                    .sync();

            serverChannel = future.channel();
            socketState = SocketState.ACTIVE;

            log.info("{} listening on {}", getId(), this.port);
        } catch (Exception e) {
            socketState = SocketState.ERROR;
            log.error("Failed to bind server socket {}", getId(), e);
            throw e;
        }
    }

    /**
     * Puts the server socket into standby mode and stops
     * accepting new connections.
     */
    @Override
    public synchronized void standby() {
        if (socketState == SocketState.STANDBY) {
            return;
        }

        try {
            socketState = SocketState.STANDBY;

            channelPool.closeAll();
            if (serverChannel != null) {
                serverChannel.close();
                serverChannel = null;
            }

            log.info("{} standby", getId());
        } catch (Exception e) {
            socketState = SocketState.ERROR;
            log.error("Failed to demote socket {}", getId(), e);
        }
    }

    /**
     * Stops the server socket and closes all active connections.
     */
    @Override
    public synchronized void stop() {
        if (!running) {
            log.warn("{} already stopped", getId());
            return;
        }

        running = false;
        startTime = 0;
        socketState = SocketState.DOWN;

        channelPool.closeAll();
        if (serverChannel != null) {
            serverChannel.close();
            serverChannel = null;
        }

        log.info("{} stopped", getId());
    }

    /**
     * Gracefully shuts down the server socket and releases
     * all Netty resources.
     *
     * @throws InterruptedException if shutdown is interrupted
     */
    @Override
    public synchronized void shutdown() throws InterruptedException {
        stop();
        super.shutdown();

        if (boss != null) {
            boss.shutdownGracefully();
        }
        if (worker != null) {
            worker.shutdownGracefully();
        }

        log.info("{} shutdown", getId());
    }

    /**
     * Returns the server channel.
     *
     * @return server channel or {@code null} if not active
     */
    public Channel getServerChannel() {
        return serverChannel;
    }

    @Override
    public SocketChannelPooling channelPool() {
        return channelPool;
    }

    @Override
    public SocketType getType() {
        return type;
    }

    @Override
    public SocketState getState() {
        return socketState;
    }
}

