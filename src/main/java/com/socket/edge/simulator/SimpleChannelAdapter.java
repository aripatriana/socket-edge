package com.socket.edge.simulator;

import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;


public class SimpleChannelAdapter extends ChannelInboundHandlerAdapter {

    public static List<Channel> channels = new ArrayList<>();

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        super.channelRead(ctx, msg);
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        Channel ch = ctx.channel();

        channels.add(ch);

        ChannelFuture channelFuture = ch.closeFuture();
        if(!channelFuture.channel().isActive()){
            return;
        }
        InetSocketAddress remote =
                (InetSocketAddress) ch.remoteAddress();
        InetSocketAddress local =
                (InetSocketAddress) ch.localAddress();

        System.out.printf(
                "CHANNEL ACTIVE | id=%s | remote=%s:%d | local=%s:%d | thread=%s%n",
                ch.id().asShortText(),
                remote.getAddress().getHostAddress(),
                remote.getPort(),
                local.getAddress().getHostAddress(),
                local.getPort(),
                Thread.currentThread().getName()
        );
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        Channel ch = ctx.channel();
        channels.remove(ch);
        InetSocketAddress remote =
                (InetSocketAddress) ch.remoteAddress();

        System.out.printf(
                "CHANNEL INACTIVE | id=%s | remote=%s:%d%n",
                ch.id().asShortText(),
                remote.getAddress().getHostAddress(),
                remote.getPort()
        );
    }

    public static List<Channel> channels() {
        return channels;
    }
}