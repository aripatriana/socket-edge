package com.socket.edge.simulator;

import com.socket.edge.utils.ByteDecoder;
import com.socket.edge.utils.ByteEncoder;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import io.netty.handler.codec.LengthFieldPrepender;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

public class SimpleNettyClient {

    private String node;

    public SimpleNettyClient(String node) {
        this.node = node;
    }

    public void start(String host, int port) throws Exception {

        EventLoopGroup group = new NioEventLoopGroup();

        try {
            Bootstrap b = new Bootstrap();

            b.group(group)
                    .channel(NioSocketChannel.class)
                    .option(ChannelOption.SO_KEEPALIVE, true)
                    .handler(new ChannelInitializer<Channel>() {
                        @Override
                        protected void initChannel(Channel ch) {
                            ch.pipeline().addLast(new SimpleChannelAdapter());
                            ch.pipeline().addLast(new LengthFieldBasedFrameDecoder(Integer.MAX_VALUE,0, 2,0,2));
                            ch.pipeline().addLast(new ByteDecoder());
                            ch.pipeline().addLast(new SimpleClientHandler(node));
                            ch.pipeline().addLast(new ByteEncoder());
                            ch.pipeline().addLast(new LengthFieldPrepender(2));
                        }
                    });

            Channel ch = b.connect(host, port).sync().channel();
            System.out.println("[Member-" + node + "] connected to " + host + ":" + port);
            System.out.println("Type message and press ENTER (type 'exit' to quit)");

            BufferedReader reader =
                    new BufferedReader(new InputStreamReader(System.in));

            String line;
            while ((line = reader.readLine()) != null) {

                if ("exit".equalsIgnoreCase(line)) {
                    System.out.println("[Member-" + node + "] closing connection");
                    ch.close();
                    break;
                }

                ch.writeAndFlush(
                        Unpooled.copiedBuffer(line, StandardCharsets.US_ASCII)
                );


                System.out.println("[Member-" + node + "] send " + line);
                System.out.println("------------------------------------------");
            }

            ch.closeFuture().sync();
            System.out.println("Close");
        } finally {
            group.shutdownGracefully();
        }
    }

    static class SimpleClientHandler extends SimpleChannelInboundHandler<byte[]> {

        private String node;

        public SimpleClientHandler(String node) {
            this.node = node;
        }

        @Override
        protected void channelRead0(ChannelHandlerContext ctx, byte[] msg) {
            String data = new String(msg);
            System.out.println("[Member-" + node + "] received: " + data);
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