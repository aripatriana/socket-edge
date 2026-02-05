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

public class DefaultServerSocket extends AbstractSocket {

    private static final Logger log = LoggerFactory.getLogger(DefaultServerSocket.class);

    private volatile SocketState socketState = SocketState.DOWN;

    private final int port;
    private EventLoopGroup boss;
    private EventLoopGroup worker;
    private Channel serverChannel;
    private volatile boolean running = false;
    private IsoParser parser;
    private MessageContextProcess forward;
    private SocketChannelPooling channelPool;
    private SocketType type = SocketType.SERVER;
    private SocketTelemetry socketTelemetry;
    private TelemetryRegistry telemetryRegistry;

    public DefaultServerSocket(boolean cluster, String name, String host, int port, List<SocketEndpoint> allowlist, TelemetryRegistry telemetryRegistry, IsoParser parser, MessageContextProcess forward) {
        super(cluster, String.format("%s-server-%d",name, port), name, host, port, telemetryRegistry);

        this.port = port;
        this.parser = parser;
        this.forward = forward;


        allowlist.forEach(se -> {
            registerEndpoint(se);
        });

        this.telemetryRegistry = telemetryRegistry;
        this.channelPool = new SocketChannelPooling(this);

        boss = new NioEventLoopGroup(
                1,
                new DefaultThreadFactory(
                        String.format("%s-server-el-b", getName())
                )
        );
        worker = new NioEventLoopGroup(
                new DefaultThreadFactory(
                        String.format("%s-server-el-w", getName())
                )
        );
    }

    @Override
    public synchronized void start() throws InterruptedException {
        if (running) {
            log.warn("{} already running", getId());
            return;
        }

        if (isCluster()) {
            log.debug("Start socket app id={} as {}", getId(), getRole());
        } else {
            log.debug("Start socket app id={}", getId());
        }

        running = true;
        startTime = System.currentTimeMillis();

        if (!isCluster() || getRole() == NodeRole.MASTER) {
            activate();
        } else {
            socketState = SocketState.STANDBY;
            log.info("{} started in standby mode", getId());
        }
    }

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
                            ch.pipeline().addLast(new LengthFieldBasedFrameDecoder(Integer.MAX_VALUE, 0, 2, 0, 2));
                            ch.pipeline().addLast(new ByteDecoder());
                            ch.pipeline().addLast(new ServerInboundHandler(DefaultServerSocket.this, parser, forward));
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


    @Override
    public SocketType getType() {
        return type;
    }

    public Channel getServerChannel() {
        return serverChannel;
    }

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

    @Override
    public SocketChannelPooling channelPool() {
        return channelPool;
    }

    @Override
    public SocketState getState() {
        return socketState;
    }

}
