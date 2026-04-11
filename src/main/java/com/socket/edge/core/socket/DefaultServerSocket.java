package com.socket.edge.core.socket;

import com.socket.edge.constant.NodeRole;
import com.socket.edge.constant.SocketState;
import com.socket.edge.constant.SocketType;
import com.socket.edge.core.MessageContextProcess;
import com.socket.edge.core.SystemConfig;
import com.socket.edge.core.TelemetryRegistry;
import com.socket.edge.model.SocketEndpoint;
import com.socket.edge.utils.ByteDecoder;
import com.socket.edge.utils.ByteEncoder;
import com.socket.edge.utils.IsoParser;
import com.socket.edge.utils.PciMaskUtil;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import io.netty.handler.codec.LengthFieldPrepender;
import io.netty.handler.timeout.IdleStateEvent;
import io.netty.handler.timeout.IdleStateHandler;
import io.netty.util.concurrent.DefaultThreadFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Server-side TCP socket using Netty.
 *
 * <p>v3.0: All TCP parameters read from {@link SystemConfig.TcpConfig}:
 * max-frame-length, idle-timeout, so-rcvbuf, so-sndbuf,
 * keepalive, nodelay. Added {@link IdleStateHandler} for idle channel cleanup.</p>
 *
 * @author Ari Patriana
 * @since 3.0.0
 */
public class DefaultServerSocket extends AbstractSocket {

    private static final Logger log = LoggerFactory.getLogger(DefaultServerSocket.class);

    private volatile SocketState socketState = SocketState.DOWN;
    private final int port;
    private EventLoopGroup boss;
    private EventLoopGroup worker;
    private Channel serverChannel;
    private volatile boolean running = false;

    private final SystemConfig.TcpConfig tcpConfig;
    private final SocketLifecycleCoordinator coordinator;
    private final IsoParser parser;
    private final MessageContextProcess forward;
    private final SocketChannelPooling channelPool;
    private final SocketType type = SocketType.SERVER;
    private final TelemetryRegistry telemetryRegistry;
    private final PciMaskUtil pciMaskUtil;

    public DefaultServerSocket(
            boolean cluster, String name, String host, int port,
            List<SocketEndpoint> allowlist,
            SystemConfig.TcpConfig tcpConfig,
            SocketLifecycleCoordinator coordinator,
            TelemetryRegistry telemetryRegistry,
            IsoParser parser, MessageContextProcess forward,
            PciMaskUtil pciMaskUtil
    ) {
        super(cluster, String.format("%s-server-%d", name, port),
                name, host, port, telemetryRegistry);

        this.port = port;
        this.tcpConfig = tcpConfig;
        this.parser = parser;
        this.forward = forward;
        this.coordinator = coordinator;
        this.telemetryRegistry = telemetryRegistry;
        this.pciMaskUtil = pciMaskUtil;

        allowlist.forEach(this::registerEndpoint);

        this.channelPool = new SocketChannelPooling(this);
        this.boss = new NioEventLoopGroup(1,
                new DefaultThreadFactory(String.format("%s-server-el-b", getName())));
        this.worker = new NioEventLoopGroup(
                new DefaultThreadFactory(String.format("%s-server-el-w", getName())));
    }

    @Override
    public synchronized void start() throws InterruptedException {
        if (running) { log.warn("{} already running", getId()); return; }

        if (!isCluster() || getRole() == NodeRole.MASTER) {
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
            ServerBootstrap sb = new ServerBootstrap()
                    .group(boss, worker)
                    .channel(NioServerSocketChannel.class)
                    .option(ChannelOption.SO_REUSEADDR, true)
                    .childOption(ChannelOption.SO_KEEPALIVE, tcpConfig.soKeepAlive())
                    .childOption(ChannelOption.TCP_NODELAY, tcpConfig.tcpNoDelay());

            if (tcpConfig.soRcvBuf() > 0)
                sb.childOption(ChannelOption.SO_RCVBUF, tcpConfig.soRcvBuf());
            if (tcpConfig.soSndBuf() > 0)
                sb.childOption(ChannelOption.SO_SNDBUF, tcpConfig.soSndBuf());

            int idleSeconds = tcpConfig.idleTimeout();
            int maxFrame = tcpConfig.maxFrameLength();

            sb.childHandler(new ChannelInitializer<io.netty.channel.socket.SocketChannel>() {
                @Override
                protected void initChannel(io.netty.channel.socket.SocketChannel ch) {
                    ch.pipeline().addLast(new ChannelInboundAdapter(channelPool));

                    if (idleSeconds > 0) {
                        ch.pipeline().addLast(new IdleStateHandler(
                                idleSeconds, 0, 0, TimeUnit.SECONDS));
                        ch.pipeline().addLast(new IdleCloseHandler(DefaultServerSocket.this));
                    }

                    ch.pipeline().addLast(new LengthFieldBasedFrameDecoder(
                            maxFrame, 0, 2, 0, 2));
                    ch.pipeline().addLast(new ByteDecoder());
                    ch.pipeline().addLast(new ServerInboundHandler(
                            DefaultServerSocket.this, parser, forward,
                            coordinator, pciMaskUtil));
                    ch.pipeline().addLast(new ByteEncoder());
                    ch.pipeline().addLast(new LengthFieldPrepender(2));
                }
            });

            ChannelFuture future = sb.bind(port).sync();
            serverChannel = future.channel();
            changeState(SocketState.LISTEN);
            log.info("{} listening on port {} (maxFrame={}, idle={}s)",
                    getId(), port, maxFrame, idleSeconds);
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

    public Channel getServerChannel() { return serverChannel; }
    @Override public SocketChannelPooling channelPool() { return channelPool; }
    @Override public SocketType getType() { return type; }
    @Override public SocketState getState() { return socketState; }
    public void changeState(SocketState s) { this.socketState = s; }

    /**
     * Closes channels that have been idle beyond the configured timeout.
     */
    static class IdleCloseHandler extends ChannelInboundHandlerAdapter {
        private final DefaultServerSocket socket;
        IdleCloseHandler(DefaultServerSocket socket) { this.socket = socket; }

        @Override
        public void userEventTriggered(ChannelHandlerContext ctx, Object evt) {
            if (evt instanceof IdleStateEvent) {
                log.info("{} closing idle channel: {}", socket.getId(),
                        ctx.channel().remoteAddress());
                ctx.close();
            }
        }
    }
}
