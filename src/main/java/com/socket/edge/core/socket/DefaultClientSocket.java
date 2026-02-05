package com.socket.edge.core.socket;

import com.socket.edge.constant.NodeRole;
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

public class DefaultClientSocket extends AbstractSocket {

    private static final Logger log = LoggerFactory.getLogger(DefaultClientSocket.class);

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
    private static final int MAX_BACKOFF_SECONDS = 30;
    private IsoParser parser;
    private MessageContextProcess forward;
    private SocketChannelPooling channelPool;
    private SocketType type = SocketType.CLIENT;
    private TelemetryRegistry telemetryRegistry;

    public DefaultClientSocket(boolean cluster, String name, SocketEndpoint se, TelemetryRegistry telemetryRegistry, IsoParser parser, MessageContextProcess forward) {
        super(cluster, String.format("%s-client-%s-%d",name, se.host(),se.port()), name, se.host(), se.port(), telemetryRegistry);

        this.host = se.host();
        this.port = se.port();
        this.parser = parser;
        this.forward = forward;

        registerEndpoint(se);

        this.telemetryRegistry = telemetryRegistry;
        this.channelPool = new SocketChannelPooling(this);

        this.group = new NioEventLoopGroup(
                1,
                new DefaultThreadFactory(
                        String.format("%s-client-el", name)
                )
        );
        scheduler = Executors.newSingleThreadScheduledExecutor(r ->
                new Thread(
                        r,
                        String.format("%s-client", name)
                )
        );
    }

    @Override
    public synchronized void start() {
        if (running) {
            log.warn("{} already running", getId());
            return;
        }

        if (isCluster()) {
            log.debug("Start client socket app id={} as {}", getId(), getRole());
        } else {
            log.debug("Start client socket app id={}", getId());
        }

        bootstrap = new Bootstrap()
                .group(group)
                .channel(NioSocketChannel.class)
                .option(ChannelOption.SO_KEEPALIVE, true)
                .handler(new ChannelInitializer<>() {
                    @Override
                    protected void initChannel(Channel ch) {
                        ch.pipeline().addLast(new ChannelInboundAdapter(channelPool));
                        ch.pipeline().addLast(new LengthFieldBasedFrameDecoder(Integer.MAX_VALUE, 0, 2, 0, 2));
                        ch.pipeline().addLast(new ByteDecoder());
                        ch.pipeline().addLast(new ClientInboundHandler(DefaultClientSocket.this, parser, forward));
                        ch.pipeline().addLast(new ByteEncoder());
                        ch.pipeline().addLast(new LengthFieldPrepender(2));
                    }
                });

        running = true;
        startTime = System.currentTimeMillis();

        if (!isCluster() || getRole() == NodeRole.MASTER) {
            activate();
        } else {
            socketState = SocketState.STANDBY;
            log.info("{} started in standby mode", getId());
        }
    }

    public synchronized void activate() {
        if (!running) {
            log.warn("{} not running, cannot activate", getId());
            return;
        }

        if (socketState == SocketState.ACTIVE) {
            return;
        }

        socketState = SocketState.ACTIVE;

        retryCount = 0;
        reconnecting.set(false);

        connect();
    }

    public synchronized void standby() {
        if (socketState == SocketState.STANDBY) {
            return;
        }

        try {
            socketState = SocketState.STANDBY;

            reconnecting.set(false);
            retryCount = 0;

            channelPool.closeAll();
            if (channel != null) {
                channel.close();
                channel = null;
            }

            log.info("{} standby", getId());
        } catch (Exception e) {
            socketState = SocketState.ERROR;
            log.error("Failed to demote socket {}", getId(), e);
        }
    }

    @Override
    public SocketType getType() {
        return type;
    }

    @Override
    public synchronized void stop() {
        if (!running) {
            log.warn("{} already stopped", getId());
            return;
        }

        running = false;
        startTime = 0;
        reconnecting.set(false);
        retryCount = 0;

        channelPool.closeAll();
        if (channel != null) {
            channel.close();
            channel = null;
        }

        log.info("{} stopped", getId());
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

    private synchronized void connect() {
        if (!running) return;
        if (isCluster() && getRole() != NodeRole.MASTER) return;
        if (group.isShutdown() || group.isTerminated()) return;

        bootstrap.connect(host, port)
                .addListener((ChannelFutureListener) future -> {
                    reconnecting.set(false);
                    if (!future.isSuccess()) {
                        scheduleReconnect();
                        return;
                    }

                    log.info("{} connected", getId());

                    Channel ch = future.channel();
                    this.channel = ch;

                    retryCount = 0;

                    ch.closeFuture().addListener(cf ->
                            onDisconnect(ch)
                    );
                });
    }

    @Override
    public SocketState getState() {
        return socketState;
    }

    @Override
    public SocketChannelPooling channelPool() {
        return channelPool;
    }

    public synchronized void onDisconnect(Channel disconnected) {
        if (this.channel != disconnected) {
            return;
        }

        this.channel = null;
        channelPool.removeChannel(disconnected);

        log.info("{} disconnected", getId());

        if (!running) return;
        scheduleReconnect();
    }

    private synchronized void scheduleReconnect() {
        if (!running) return;
        if (isCluster() && getRole() != NodeRole.MASTER) return;

        if (!reconnecting.compareAndSet(false, true)) {
            return;
        }

        int delaySeconds = Math.min(
                MAX_BACKOFF_SECONDS,
                1 << retryCount   // 1,2,4,8,16,30
        );

        retryCount++;

        log.info("{} reconnect to {}:{} in {}s (retry={})", getId(), host, port, delaySeconds, retryCount);

        scheduler.schedule(() -> {
            connect();
        }, delaySeconds, TimeUnit.SECONDS);
    }

}
