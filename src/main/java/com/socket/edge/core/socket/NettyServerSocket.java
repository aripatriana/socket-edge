package com.socket.edge.core.socket;

import com.socket.edge.constant.SocketState;
import com.socket.edge.constant.SocketType;
import com.socket.edge.core.ForwardService;
import com.socket.edge.core.SocketTelemetry;
import com.socket.edge.core.TelemetryRegistry;
import com.socket.edge.model.SocketEndpoint;
import com.socket.edge.utils.ByteDecoder;
import com.socket.edge.utils.ByteEncoder;
import com.socket.edge.utils.IsoParser;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import io.netty.handler.codec.LengthFieldPrepender;
import io.netty.util.concurrent.DefaultThreadFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class NettyServerSocket extends AbstractSocket {

    private static final Logger log = LoggerFactory.getLogger(NettyServerSocket.class);

    private final int port;
    private EventLoopGroup boss;
    private EventLoopGroup worker;
    private Channel serverChannel;
    private volatile boolean running = false;
    private IsoParser parser;
    private ForwardService forward;
    private SocketChannelPool channelPool;
    private SocketType type = SocketType.SOCKET_SERVER;
    private SocketTelemetry counter;

    public NettyServerSocket(String name, int port, List<SocketEndpoint> allowlist, TelemetryRegistry metrics, IsoParser parser, ForwardService forward) {
        super(String.format("%s-server-%d",name, port), name);
        this.port = port;
        this.parser = parser;
        this.forward = forward;
        this.channelPool = new SocketChannelPool(getId(), type, allowlist);
        this.counter = metrics.register(this);
    }

    @Override
    public synchronized void start() throws InterruptedException {
        if (running) {
            log.warn("{} already running", getId());
            return;
        }

        log.info("Start socket server id={}", getId());
        running = true;

        boss = new NioEventLoopGroup(
                1,
                new DefaultThreadFactory(
                        String.format("%s-server-eventloop-boss", getName())
                )
        );
        worker = new NioEventLoopGroup(
                new DefaultThreadFactory(
                        String.format("%s-server-eventloop-worker", getName())
                )
        );

        serverChannel = new ServerBootstrap()
                .group(boss, worker)
                .channel(NioServerSocketChannel.class)
                .option(ChannelOption.SO_REUSEADDR, true)
                .childOption(ChannelOption.SO_KEEPALIVE, true)
                .childOption(ChannelOption.TCP_NODELAY, true)
                .childHandler(new ChannelInitializer<io.netty.channel.socket.SocketChannel>() {
                    @Override
                    protected void initChannel(io.netty.channel.socket.SocketChannel ch) {
                        ch.pipeline().addLast(new ChannelInboundAdapter(channelPool));
                        ch.pipeline().addLast(new LengthFieldBasedFrameDecoder(Integer.MAX_VALUE, 0,2, 0, 2));
                        ch.pipeline().addLast(new ByteDecoder());
                        ch.pipeline().addLast(new ServerInboundHandler(NettyServerSocket.this,counter, parser, forward));
                        ch.pipeline().addLast(new ByteEncoder());
                        ch.pipeline().addLast(new LengthFieldPrepender(2));
                    }
                })
                .bind(port)
                .sync().channel();

        startTime = System.currentTimeMillis();
        log.info("{} listening on {}", getId(), this.port);
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
        log.info("Stop socket server id={}", getId());

        running = false;
        startTime = 0;

        channelPool.closeAll();

        if (serverChannel != null) {
            serverChannel.close();
            serverChannel = null;
        }
    }

    @Override
    public synchronized void shutdown() throws InterruptedException {
        stop();

        if (boss != null) {
            boss.shutdownGracefully();
        }

        if (worker != null) {
            worker.shutdownGracefully();
        }
    }

    @Override
    public SocketChannelPool channelPool() {
        return channelPool;
    }

    @Override
    public SocketState getState() {
        return serverChannel != null && serverChannel.isActive() ? SocketState.UP : SocketState.DOWN;
    }
}
