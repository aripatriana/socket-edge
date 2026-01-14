package com.socket.edge.core.socket;

import com.socket.edge.core.ForwardService;
import com.socket.edge.core.SocketTelemetry;
import com.socket.edge.core.TelemetryRegistry;
import com.socket.edge.model.SocketEndpoint;
import com.socket.edge.model.SocketState;
import com.socket.edge.model.SocketType;
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

import java.util.Collections;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class NettyClientSocket extends AbstractSocket {

    private static final Logger log = LoggerFactory.getLogger(NettyClientSocket.class);

    private final String host;
    private final int port;
    private Channel channel;
    private EventLoopGroup group;
    private ScheduledExecutorService scheduler;
    private Bootstrap bootstrap;
    private final AtomicBoolean reconnecting = new AtomicBoolean(false);
    private volatile boolean running = true;
    private int retryCount = 0;
    private static final int MAX_BACKOFF_SECONDS = 30;
    private IsoParser parser;
    private ForwardService forward;
    private SocketChannelPool channelPool;
    private SocketType type = SocketType.SOCKET_CLIENT;
    private SocketTelemetry socketTelemetry;

    public NettyClientSocket(String name, SocketEndpoint se, TelemetryRegistry metricService, IsoParser parser, ForwardService forward) {
        super(String.format("%s-client-%s-%d",name, se.host(),se.port()), name);
        this.host = se.host();
        this.port = se.port();
        this.group = new NioEventLoopGroup(
                1,
                new DefaultThreadFactory(
                        String.format("%s-client-eventloop", name)
                )
        );
        scheduler = Executors.newSingleThreadScheduledExecutor(r ->
                new Thread(
                        r,
                        String.format("%s-client-reconnect", name)
                )
        );
        this.parser = parser;
        this.forward = forward;
        this.channelPool = new SocketChannelPool(getId(), type, Collections.singletonList(se));
        this.socketTelemetry = metricService.register(this);
    }

    @Override
    public void start() {
        running = true;

        bootstrap = new Bootstrap()
                .group(group)
                .channel(NioSocketChannel.class)
                .option(ChannelOption.SO_KEEPALIVE, true)
                .handler(new ChannelInitializer<>() {
                    @Override
                    protected void initChannel(Channel ch) {
                        ch.pipeline().addLast(new ChannelInboundAdapter(channelPool));
                        ch.pipeline().addLast(new LengthFieldBasedFrameDecoder(Integer.MAX_VALUE, 0,2, 0, 2));
                        ch.pipeline().addLast(new ByteDecoder());
                        ch.pipeline().addLast(new ClientInboundHandler(NettyClientSocket.this, socketTelemetry, parser, forward));
                        ch.pipeline().addLast(new ByteEncoder());
                        ch.pipeline().addLast(new LengthFieldPrepender(2));
                    }
                });

        connect();
        startTime = System.currentTimeMillis();
    }

    @Override
    public SocketType getType() {
        return type;
    }

    @Override
    public void stop() {
        startTime = 0;

        if (channel != null) {
            channel.close();
            channel = null;
        }

        scheduler.shutdownNow();
        group.shutdownGracefully();
    }

    private void connect() {
        if (!running) {
            return;
        }

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
        return channel != null && channel.isActive() ? SocketState.UP : SocketState.DOWN;
    }

    @Override
    public SocketChannelPool channelPool() {
        return channelPool;
    }

    public void onDisconnect(Channel disconnected) {
        if (this.channel != disconnected) {
            return;
        }

        this.channel = null;

        log.info("{} disconnected", getId());

        scheduleReconnect();
    }

    private void scheduleReconnect() {
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
