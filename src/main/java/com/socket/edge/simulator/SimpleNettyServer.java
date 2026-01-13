package com.socket.edge.simulator;

import com.socket.edge.utils.ByteDecoder;
import com.socket.edge.utils.ByteEncoder;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import io.netty.handler.codec.LengthFieldPrepender;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

public class SimpleNettyServer {

    private String node;

    public SimpleNettyServer(String node) {
        this.node = node;
    }

    public void start(int port) throws Exception {

        EventLoopGroup boss = new NioEventLoopGroup(1);
        EventLoopGroup worker = new NioEventLoopGroup();

        try {
            ServerBootstrap b = new ServerBootstrap();

            b.group(boss, worker)
                    .channel(NioServerSocketChannel.class)
                    .option(ChannelOption.SO_REUSEADDR, true)
                    .option(ChannelOption.SO_KEEPALIVE, true)
                    .childHandler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch) {
                            ch.pipeline().addLast(new SimpleChannelAdapter());
                            ch.pipeline().addLast(new LengthFieldBasedFrameDecoder(Integer.MAX_VALUE, 0,2, 0, 2));
                            ch.pipeline().addLast(new ByteDecoder());
                            ch.pipeline().addLast(new SimpleServerHandler());
                            ch.pipeline().addLast(new ByteEncoder());
                            ch.pipeline().addLast(new LengthFieldPrepender(2));
                        }
                    });

            ChannelFuture f = b.bind(port).sync();
            System.out.println("[Server-A] listening on " + port);
            System.out.println("Type message and press ENTER (type 'exit' to quit)");

            BufferedReader reader =
                    new BufferedReader(new InputStreamReader(System.in));
            String line;
            while ((line = reader.readLine()) != null) {

                if ("exit".equalsIgnoreCase(line)) {
                    System.out.println("[Server-" + node + "] closing connection");
                    for (Channel ch : SimpleChannelAdapter.channels()) {
                        ch.close();
                    }
                    break;
                }

                if (SimpleChannelAdapter.channels.isEmpty()) {
                    System.out.println("No channel active");
                } else {
                    for (Channel ch : SimpleChannelAdapter.channels()) {
                        ch.writeAndFlush(
                                Unpooled.copiedBuffer(line, StandardCharsets.US_ASCII)
                        );
                    }
                    ;

                    System.out.println("[Server-" + node + "] send " + line);
                    System.out.println("------------------------------------------");
                }
            }

            f.channel().closeFuture().sync();
            System.out.println("Close");
        } finally {
            boss.shutdownGracefully();
            worker.shutdownGracefully();
        }
    }

    static class SimpleServerHandler extends SimpleChannelInboundHandler<byte[]> {

        @Override
        protected void channelRead0(ChannelHandlerContext ctx, byte[] msg) {
            String data = new String(msg);
            System.out.println("[Backend-Server-A] received: " + data);
            System.out.println("------------------------------------------");

            if (data.startsWith("0200")) {
                String text = new String("0210F23AC4118EE18080000000001400002019936008276919868770926100000000890000001111000000533861659570111011201115817011C0000000008936008270636000125597992753654757500519862          19868770       TechTahi                 JAKARTA UTARAID049PI04Q001CD30ANTON PRATAMA                 MC03UME36007561051445062620123519862-100000009223587602120852156474130305314890706519862089360049019936004905822023824002046220533864622053386");

                // kirim balik (echo)
                ctx.channel().writeAndFlush(Unpooled.copiedBuffer(text, StandardCharsets.US_ASCII));
                System.out.println("[Backend-Server-A] send: " + text);
                System.out.println("------------------------------------------");
            }
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            cause.printStackTrace();
            ctx.close();
        }
    }
}