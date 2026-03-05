package com.socket.edge.core.socket;

import com.socket.edge.constant.NodeRole;
import com.socket.edge.core.CompletionCallback;
import com.socket.edge.core.MessageContextProcess;
import com.socket.edge.core.TelemetryRegistry;
import com.socket.edge.model.SocketEndpoint;
import com.socket.edge.constant.SocketState;
import com.socket.edge.constant.SocketType;
import com.socket.edge.utils.ByteDecoder;
import com.socket.edge.utils.ByteEncoder;
import com.socket.edge.utils.IsoParser;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import io.netty.handler.codec.LengthFieldPrepender;
import io.netty.util.concurrent.DefaultThreadFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Default implementation of a client-side socket using Netty.
 *
 * <p>{@code DefaultClientSocket} is responsible for establishing and maintaining
 * an outbound TCP connection to a remote endpoint. It supports:
 * <ul>
 *   <li>Automatic reconnect with exponential backoff</li>
 *   <li>Cluster-aware activation (MASTER only)</li>
 *   <li>Channel pooling</li>
 *   <li>Telemetry registration</li>
 *   <li>ISO message decoding and forwarding</li>
 * </ul>
 *
 * <p>In cluster mode, the socket will only actively connect and send traffic
 * when the node role is {@link NodeRole#MASTER}. When running as
 * {@link NodeRole#SLAVE}, the socket stays in {@link SocketState#STANDBY}.</p>
 *
 * <p>Thread safety:
 * <ul>
 *   <li>Lifecycle methods are synchronized</li>
 *   <li>{@link #socketState} and {@link #running} use volatile visibility</li>
 *   <li>Reconnect coordination uses {@link AtomicBoolean}</li>
 * </ul>
 *
 *  @author Ari Patriana
 *  @since 1.0.0
 */
public class DefaultClientSocket extends AbstractSocket {

    private static final Logger log = LoggerFactory.getLogger(DefaultClientSocket.class);

    /**
     * Current socket state.
     */
    private volatile SocketState socketState = SocketState.DOWN;

    /**
     * Remote host.
     */
    private final String host;

    /**
     * Remote port.
     */
    private final int port;

    /**
     * Active Netty channel.
     */
    private Channel channel;

    /**
     * Netty event loop group.
     */
    private EventLoopGroup group;

    /**
     * Scheduler for reconnect attempts.
     */
    private ScheduledExecutorService scheduler;

    /**
     * Netty bootstrap configuration.
     */
    private Bootstrap bootstrap;

    /**
     * Indicates whether a reconnect attempt is currently scheduled.
     */
    private final AtomicBoolean reconnecting = new AtomicBoolean(false);

    /**
     * Indicates whether the socket is running.
     */
    private volatile boolean running = false;

    /**
     * Current reconnect retry count.
     */
    private int retryCount = 0;

    /**
     * Maximum reconnect backoff in seconds.
     */
    private static final int MAX_BACKOFF_SECONDS = 30;

    /**
     * ISO message parser.
     */
    private IsoParser parser;

    /**
     * Message forwarder to processing pipeline.
     */
    private MessageContextProcess forward;

    /**
     * Channel pool associated with this socket.
     */
    private SocketChannelPooling channelPool;

    /**
     * Socket type.
     */
    private final SocketType type = SocketType.CLIENT;

    /**
     * Telemetry registry.
     */
    private TelemetryRegistry telemetryRegistry;

    private SocketManager sm;

    /**
     * Creates a new client socket.
     *
     * @param cluster           whether cluster mode is enabled
     * @param name              socket name
     * @param se                remote socket endpoint
     * @param telemetryRegistry telemetry registry
     * @param parser            ISO message parser
     * @param forward           message forwarding processor
     */
    public DefaultClientSocket(
            boolean cluster,
            String name,
            SocketEndpoint se,
            SocketManager sm,
            TelemetryRegistry telemetryRegistry,
            IsoParser parser,
            MessageContextProcess forward
    ) {
        super(
                cluster,
                String.format("%s-client-%s-%d", name, se.host(), se.port()),
                name,
                se.host(),
                se.port(),
                telemetryRegistry
        );

        this.host = se.host();
        this.port = se.port();
        this.parser = parser;
        this.forward = forward;

        registerEndpoint(se);

        this.sm = sm;
        this.telemetryRegistry = telemetryRegistry;
        this.channelPool = new SocketChannelPooling(this);

        this.group = new NioEventLoopGroup(
                1,
                new DefaultThreadFactory(String.format("%s-client-el", name))
        );

        this.scheduler = Executors.newSingleThreadScheduledExecutor(r ->
                new Thread(r, String.format("%s-client", name))
        );

        bootstrap = new Bootstrap()
                .group(group)
                .channel(NioSocketChannel.class)
                .option(ChannelOption.SO_KEEPALIVE, true)
                .handler(new ChannelInitializer<>() {
                    @Override
                    protected void initChannel(Channel ch) {
                        ch.pipeline().addLast(new ChannelInboundAdapter(channelPool));
                        ch.pipeline().addLast(new LengthFieldBasedFrameDecoder(
                                Integer.MAX_VALUE, 0, 2, 0, 2
                        ));
                        ch.pipeline().addLast(new ByteDecoder());
                        ch.pipeline().addLast(new ClientInboundHandler(
                                sm,DefaultClientSocket.this, parser, forward
                        ));
                        ch.pipeline().addLast(new ByteEncoder());
                        ch.pipeline().addLast(new LengthFieldPrepender(2));
                    }
                });
    }

    /**
     * Starts the client socket.
     *
     * <p>In cluster mode, the socket will only connect immediately
     * if the node role is {@link NodeRole#MASTER}. Otherwise, it
     * enters standby mode.</p>
     */
    @Override
    public synchronized void start() {
        if (running) {
            log.warn("{} already running", getId());
            return;
        }

        if (!isCluster() || getRole() == NodeRole.MASTER) {


            log.debug(
                    isCluster()
                            ? "Start client socket app id={} as {}"
                            : "Start client socket app id={}",
                    getId(),
                    getRole()
            );

            running = true;
            startTime = System.currentTimeMillis();

            socketState = SocketState.WAIT;
        } else {
            socketState = SocketState.STANDBY;
            log.info("{} started in standby mode", getId());
        }
    }

    /**
     * Stops the socket and closes all connections.
     */
    @Override
    public synchronized void stop() {
        if (!running) {
            log.warn("{} already stopped", getId());
            return;
        }

        try {
            running = false;
            startTime = 0;
            reconnecting.set(false);
            retryCount = 0;

            channelPool.closeAll();
            if (channel != null) {
                channel.close();
                channel = null;
            }

            changeState(SocketState.DOWN);
            log.info("{} stopped", getId());
        } catch (Exception e) {
            changeState(SocketState.ERROR);
            log.error("Failed to demote socket {}", getId(), e);
        }
    }

    /**
     * Gracefully shuts down the socket and releases all resources.
     */
    @Override
    public synchronized void shutdown() throws InterruptedException {
        stop();
        super.shutdown();

        if (scheduler != null) {
            scheduler.shutdownNow();
        }
        if (group != null) {
            group.shutdownGracefully();
        }

        log.info("{} shutdown", getId());
    }

    /**
     * Attempts to establish a connection to the remote endpoint.
     */
    public synchronized void connect(CompletionCallback callback) {
        if (!running) {
            log.warn("Skip connect: component is not running");
            return;
        }

        if (getState() == SocketState.ACTIVE) {
            log.warn("Skip connect: socket already ACTIVE");
            return;
        }

        if (isCluster() && getRole() != NodeRole.MASTER) {
            log.warn("Skip connect: node role is {}, only MASTER can start", getRole());
            return;
        }

        if (group.isShutdown() || group.isTerminated()) {
            log.warn(
                    "Skip connect: event loop group is {}",
                    group.isShutdown() ? "SHUTDOWN" : "TERMINATED"
            );
            return;
        }

        retryCount = 0;
        reconnecting.set(false);

        connectForChannel(callback);
    }

    public void connectForChannel(CompletionCallback<DefaultClientSocket> callback) {
        bootstrap.connect(host, port)
                .addListener((ChannelFutureListener) future -> {
                    reconnecting.set(false);

                    if (!future.isSuccess()) {
                        callback.onFailure(this);
                        return;
                    }

                    log.info("{} connected", getId());

                    Channel ch = future.channel();

                    this.channel = ch;
                    retryCount = 0;

                    changeState(SocketState.ACTIVE);
                    callback.onComplete(this);
                });
    }


    /**
     * Schedules a reconnect attempt using exponential backoff.
     */
    public synchronized void scheduleReconnect() {
        if (!running) {
            log.warn("Skip reconnect scheduling: component is not running");
            return;
        }

        if (isCluster() && getRole() != NodeRole.MASTER) {
            log.warn(
                    "Skip reconnect scheduling: node role is {}, only MASTER can schedule reconnect",
                    getRole()
            );
            return;
        }

        if (!reconnecting.compareAndSet(false, true)) {
            log.warn("Skip reconnect scheduling: reconnect is already in progress");
            return;
        }

        int delaySeconds = Math.min(
                MAX_BACKOFF_SECONDS,
                1 << retryCount
        );

        retryCount++;

        log.info(
                "{} reconnect to {}:{} in {}s (retry={})",
                getId(), host, port, delaySeconds, retryCount
        );

        scheduler.schedule(() -> connect(new CompletionCallback<DefaultClientSocket>() {
                @Override
                public void onComplete(DefaultClientSocket client) {
                    log.info("{} reconnect successful", getId());
                }

                @Override
                public void onFailure(DefaultClientSocket client) {
                    log.warn("{} reconnect failed", getId());
                    scheduleReconnect();
                }
        }), delaySeconds, TimeUnit.SECONDS);
    }

    @Override
    public SocketType getType() {
        return type;
    }

    @Override
    public SocketState getState() {
        return socketState;
    }

    @Override
    public SocketChannelPooling channelPool() {
        return channelPool;
    }

    public void changeState(SocketState socketState) {
        this.socketState = socketState;
    }
}

