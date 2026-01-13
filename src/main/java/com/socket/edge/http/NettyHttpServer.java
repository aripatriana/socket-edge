package com.socket.edge.http;

import com.socket.edge.http.service.HttpServiceHandler;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.util.concurrent.DefaultThreadFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class NettyHttpServer {

    private static final Logger log = LoggerFactory.getLogger(NettyHttpServer.class);

    private final String name;
    private final int port;
    private List<HttpServiceHandler> services;
    private EventLoopGroup boss;
    private EventLoopGroup worker;

    public NettyHttpServer(String name, int port,
                           List<HttpServiceHandler> services) {
        this.name = name;
        this.port = port;
        this.services = services;
    }

    public void start() throws InterruptedException {
        boss = new NioEventLoopGroup(1,
                new DefaultThreadFactory(name + "-boss")
        );

        worker = new NioEventLoopGroup(1,
                new DefaultThreadFactory(name + "-worker")
        );

        ServerBootstrap bootstrap = new ServerBootstrap();
        bootstrap.group(boss, worker)
                .channel(NioServerSocketChannel.class)
                .childHandler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) {
                        ChannelPipeline p = ch.pipeline();
                        p.addLast(new HttpServerCodec());
                        p.addLast(new HttpObjectAggregator(8 * 1024));
                        p.addLast(new HttpDispatchHandler(services));
                    }
                });

        bootstrap.bind(port).sync();
        log.info("Httpserver {} listen on {}", name, port);
    }

    public void stop() {
        if (boss != null) boss.shutdownGracefully();
        if (worker != null) worker.shutdownGracefully();
    }
}
