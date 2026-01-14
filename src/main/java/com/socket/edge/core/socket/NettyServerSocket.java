package com.socket.edge.core.socket;

import com.socket.edge.core.ForwardService;
import com.socket.edge.http.service.socket.MetricsCounter;
import com.socket.edge.http.service.socket.MetricsService;
import com.socket.edge.model.SocketEndpoint;
import com.socket.edge.model.SocketType;
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
import java.util.concurrent.CopyOnWriteArrayList;

public class NettyServerSocket extends AbstractSocket {

    private static final Logger log = LoggerFactory.getLogger(NettyServerSocket.class);

    private final int port;
    private final List<SocketChannel> activeChannels = new CopyOnWriteArrayList<>();
    private EventLoopGroup boss;
    private EventLoopGroup worker;
    private Channel serverChannel;
    private IsoParser parser;
    private ForwardService forward;
    private SocketChannelPool channelPool;
    private SocketType type = SocketType.SOCKET_SERVER;
    private MetricsCounter counter;

    public NettyServerSocket(String name, int port, List<SocketEndpoint> allowlist, MetricsService metrics, IsoParser parser, ForwardService forward) {
        super(String.format("%s_server-%d",name, port), name);
        this.port = port;
        this.parser = parser;
        this.forward = forward;
        this.channelPool = new SocketChannelPool(getId(), type, allowlist);
        this.counter = metrics.register(getId(), name, type.name());
    }

    @Override
    public void start() throws InterruptedException {
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
    public void stop() {
        startTime = 0;
        activeChannels.forEach(SocketChannel::close);

        if (serverChannel != null) {
            serverChannel.close();
        }

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
    public boolean isUp() {
        return serverChannel != null && serverChannel.isActive();
    }
}
