package com.socket.edge.core.socket;

import com.socket.edge.core.ForwardService;
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
import java.util.Set;
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
    private SocketType socketType = SocketType.SOCKET_SERVER;

    public NettyServerSocket(String name, int port, List<SocketEndpoint> allowlist, IsoParser parser, ForwardService forward) {
        super(String.format("%s_server-%d",name, port), name);
        this.port = port;
        this.parser = parser;
        this.forward = forward;
        this.channelPool = new SocketChannelPool(getId(), socketType, allowlist);
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
                        ch.pipeline().addLast(new ServerInboundHandler(NettyServerSocket.this, parser, forward));
                        ch.pipeline().addLast(new ByteEncoder());
                        ch.pipeline().addLast(new LengthFieldPrepender(2));
                    }
                })
                .bind(port)
                .sync().channel();

        log.info("{} listening on {}", getId(), this.port);
    }

    @Override
    public void stop() {
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
