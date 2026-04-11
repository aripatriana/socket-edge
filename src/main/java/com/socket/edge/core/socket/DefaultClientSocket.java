package com.socket.edge.core.socket;

import com.socket.edge.constant.NodeRole;
import com.socket.edge.constant.SocketState;
import com.socket.edge.constant.SocketType;
import com.socket.edge.core.CompletionCallback;
import com.socket.edge.core.MessageContextProcess;
import com.socket.edge.core.SystemConfig;
import com.socket.edge.core.TelemetryRegistry;
import com.socket.edge.model.SocketEndpoint;
import com.socket.edge.utils.ByteDecoder;
import com.socket.edge.utils.ByteEncoder;
import com.socket.edge.utils.IsoParser;
import com.socket.edge.utils.PciMaskUtil;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import io.netty.handler.codec.LengthFieldPrepender;
import io.netty.handler.timeout.IdleStateEvent;
import io.netty.handler.timeout.IdleStateHandler;
import io.netty.util.concurrent.DefaultThreadFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Client-side TCP socket using Netty.
 *
 * <p>v3.0: All TCP parameters from config, connect-timeout, idle handler.</p>
 *
 * @author Ari Patriana
 * @since 3.0.0
 */
public class DefaultClientSocket extends AbstractSocket {

    private static final Logger log = LoggerFactory.getLogger(DefaultClientSocket.class);
    private static final int MAX_BACKOFF_SECONDS = 30;

    private volatile SocketState socketState = SocketState.DOWN;
    private final String host;
    private final int port;
    private Channel channel;
    private EventLoopGroup group;
    private ScheduledExecutorService scheduler;
    private Bootstrap bootstrap;
    private final AtomicBoolean reconnecting = new AtomicBoolean(false);
    private volatile boolean running = false;
    private int retryCount = 0;

    private final SystemConfig.TcpConfig tcpConfig;
    private final SocketLifecycleCoordinator coordinator;
    private final IsoParser parser;
    private final MessageContextProcess forward;
    private final SocketChannelPooling channelPool;
    private final SocketType type = SocketType.CLIENT;
    private final TelemetryRegistry telemetryRegistry;
    private final PciMaskUtil pciMaskUtil;

    public DefaultClientSocket(
            boolean cluster, String name, SocketEndpoint se,
            SystemConfig.TcpConfig tcpConfig,
            SocketLifecycleCoordinator coordinator,
            TelemetryRegistry telemetryRegistry,
            IsoParser parser, MessageContextProcess forward,
            PciMaskUtil pciMaskUtil
    ) {
        super(cluster, String.format("%s-client-%s-%d", name, se.host(), se.port()),
                name, se.host(), se.port(), telemetryRegistry);

        this.host = se.host();
        this.port = se.port();
        this.tcpConfig = tcpConfig;
        this.parser = parser;
        this.forward = forward;
        this.coordinator = coordinator;
        this.telemetryRegistry = telemetryRegistry;
        this.pciMaskUtil = pciMaskUtil;

        registerEndpoint(se);
        this.channelPool = new SocketChannelPooling(this);

        this.group = new NioEventLoopGroup(1,
                new DefaultThreadFactory(String.format("%s-client-el", name)));
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r ->
                new Thread(r, String.format("%s-client-reconnect", name)));

        int idleSeconds = tcpConfig.idleTimeout();
        int maxFrame = tcpConfig.maxFrameLength();

        Bootstrap b = new Bootstrap()
                .group(group)
                .channel(NioSocketChannel.class)
                .option(ChannelOption.SO_KEEPALIVE, tcpConfig.soKeepAlive())
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, tcpConfig.connectTimeout());

        if (tcpConfig.soRcvBuf() > 0)
            b.option(ChannelOption.SO_RCVBUF, tcpConfig.soRcvBuf());
        if (tcpConfig.soSndBuf() > 0)
            b.option(ChannelOption.SO_SNDBUF, tcpConfig.soSndBuf());

        b.handler(new ChannelInitializer<>() {
            @Override
            protected void initChannel(Channel ch) {
                ch.pipeline().addLast(new ChannelInboundAdapter(channelPool));

                if (idleSeconds > 0) {
                    ch.pipeline().addLast(new IdleStateHandler(
                            idleSeconds, 0, 0, TimeUnit.SECONDS));
                    ch.pipeline().addLast(new IdleCloseHandler(DefaultClientSocket.this));
                }

                ch.pipeline().addLast(new LengthFieldBasedFrameDecoder(
                        maxFrame, 0, 2, 0, 2));
                ch.pipeline().addLast(new ByteDecoder());
                ch.pipeline().addLast(new ClientInboundHandler(
                        DefaultClientSocket.this, parser, forward,
                        coordinator, pciMaskUtil));
                ch.pipeline().addLast(new ByteEncoder());
                ch.pipeline().addLast(new LengthFieldPrepender(2));
            }
        });

        this.bootstrap = b;
    }

    @Override
    public synchronized void start() {
        if (running) { log.warn("{} already running", getId()); return; }
        if (!isCluster() || getRole() == NodeRole.MASTER) {
            running = true;
            startTime = System.currentTimeMillis();
            socketState = SocketState.WAIT;
        } else {
            socketState = SocketState.STANDBY;
            log.info("{} started in standby mode", getId());
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
            log.error("Failed to stop socket {}", getId(), e);
        }
    }

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

    public synchronized void connect(CompletionCallback<DefaultClientSocket> callback) {
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
            log.warn("Skip connect: event loop group is {}",
                    group.isShutdown() ? "SHUTDOWN" : "TERMINATED");
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
                    this.channel = future.channel();
                    retryCount = 0;
                    changeState(SocketState.ACTIVE);
                    callback.onComplete(this);
                });
    }

    public synchronized void scheduleReconnect() {
        if (!running) {
            log.warn("Skip reconnect scheduling: component is not running");
            return;
        }

        if (isCluster() && getRole() != NodeRole.MASTER) {
            log.warn("Skip reconnect scheduling: node role is {}", getRole());
            return;
        }

        if (!reconnecting.compareAndSet(false, true)) {
            log.warn("Skip reconnect scheduling: reconnect already in progress");
            return;
        }

        int delaySeconds = Math.min(MAX_BACKOFF_SECONDS, 1 << retryCount);
        retryCount++;

        log.info("{} reconnect to {}:{} in {}s (retry={})",
                getId(), host, port, delaySeconds, retryCount);

        scheduler.schedule(() -> connect(new CompletionCallback<>() {
            @Override public void onComplete(DefaultClientSocket c) {
                log.info("{} reconnect successful", getId());
            }
            @Override public void onFailure(DefaultClientSocket c) {
                log.warn("{} reconnect failed", getId());
                scheduleReconnect();
            }
        }), delaySeconds, TimeUnit.SECONDS);
    }

    @Override public SocketType getType() { return type; }
    @Override public SocketState getState() { return socketState; }
    @Override public SocketChannelPooling channelPool() { return channelPool; }
    public void changeState(SocketState s) { this.socketState = s; }

    static class IdleCloseHandler extends ChannelInboundHandlerAdapter {
        private final DefaultClientSocket socket;
        IdleCloseHandler(DefaultClientSocket socket) { this.socket = socket; }
        @Override
        public void userEventTriggered(ChannelHandlerContext ctx, Object evt) {
            if (evt instanceof IdleStateEvent) {
                log.info("{} closing idle channel: {}",
                        socket.getId(), ctx.channel().remoteAddress());
                ctx.close();
            }
        }
    }
}
