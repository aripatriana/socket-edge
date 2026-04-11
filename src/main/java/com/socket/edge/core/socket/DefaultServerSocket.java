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
 * <p>v3.0 changes:
 * <ul>
 *   <li>Replaced {@code SocketManager} dependency with {@link SocketLifecycleCoordinator}</li>
 *   <li>Fixed: Frame decoder now has {@code MAX_FRAME_LENGTH} limit (was {@code Integer.MAX_VALUE})</li>
 *   <li>Handler no longer orchestrates client lifecycle directly</li>
 * </ul>
 *
 * @author Ari Patriana
 * @since 3.0.0
 */
public class DefaultServerSocket extends AbstractSocket {

    private static final Logger log = LoggerFactory.getLogger(DefaultServerSocket.class);

    /**
     * Maximum ISO 8583 message frame size (8 KB).
     * Prevents OOM from malicious oversized frames.
     */
    private static final int MAX_FRAME_LENGTH = 8192;

    private volatile SocketState socketState = SocketState.DOWN;
    private final int port;
    private EventLoopGroup boss;
    private EventLoopGroup worker;
    private Channel serverChannel;
    private volatile boolean running = false;

    private final SocketLifecycleCoordinator coordinator;
    private final IsoParser parser;
    private final MessageContextProcess forward;
    private final SocketChannelPooling channelPool;
    private final SocketType type = SocketType.SERVER;
    private final TelemetryRegistry telemetryRegistry;

    public DefaultServerSocket(
            boolean cluster,
            String name,
            String host,
            int port,
            List<SocketEndpoint> allowlist,
            SocketLifecycleCoordinator coordinator,
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
        this.coordinator = coordinator;
        this.telemetryRegistry = telemetryRegistry;

        allowlist.forEach(this::registerEndpoint);

        this.channelPool = new SocketChannelPooling(this);

        this.boss = new NioEventLoopGroup(
                1,
                new DefaultThreadFactory(String.format("%s-server-el-b", getName()))
        );

        this.worker = new NioEventLoopGroup(
                new DefaultThreadFactory(String.format("%s-server-el-w", getName()))
        );
    }

    @Override
    public synchronized void start() throws InterruptedException {
        if (running) {
            log.warn("{} already running", getId());
            return;
        }

        if (!isCluster() || getRole() == NodeRole.MASTER) {
            log.debug(
                    isCluster()
                            ? "Start socket app id={} as {}"
                            : "Start socket app id={}",
                    getId(),
                    getRole()
            );

            running = true;
            startTime = System.currentTimeMillis();
            startServer();
        } else {
            changeState(SocketState.STANDBY);
            log.info("{} started in standby mode", getId());
        }
    }

    private synchronized void startServer() throws InterruptedException {
        if (!running) {
            log.warn("Skip start: {} not running", getId());
            return;
        }

        if (getState() == SocketState.LISTEN || getState() == SocketState.ACTIVE) {
            log.warn("Skip start: {} already {}", getId(), getState());
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
                                    MAX_FRAME_LENGTH, 0, 2, 0, 2
                            ));
                            ch.pipeline().addLast(new ByteDecoder());
                            ch.pipeline().addLast(new ServerInboundHandler(
                                    DefaultServerSocket.this, parser, forward, coordinator
                            ));
                            ch.pipeline().addLast(new ByteEncoder());
                            ch.pipeline().addLast(new LengthFieldPrepender(2));
                        }
                    })
                    .bind(port)
                    .sync();

            serverChannel = future.channel();
            changeState(SocketState.LISTEN);

            log.info("{} listening on {}", getId(), this.port);
        } catch (Exception e) {
            changeState(SocketState.ERROR);
            log.error("Failed to bind server socket {}", getId(), e);
            throw e;
        }
    }

    @Override
    public synchronized void stop() {
        if (!running) {
            log.warn("{} already stopped", getId());
            return;
        }

        try {
            running = false;
            startTime = 0;
            changeState(SocketState.DOWN);

            channelPool.closeAll();
            if (serverChannel != null) {
                serverChannel.close();
                serverChannel = null;
            }

            log.info("{} stopped", getId());
        } catch (Exception e) {
            changeState(SocketState.ERROR);
            log.error("Failed to stop socket {}", getId(), e);
        }
    }

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

    public void changeState(SocketState socketState) {
        this.socketState = socketState;
    }
}
