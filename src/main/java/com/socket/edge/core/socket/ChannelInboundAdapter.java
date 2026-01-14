package com.socket.edge.core.socket;

import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;


public class ChannelInboundAdapter extends ChannelInboundHandlerAdapter {

    private static final Logger log = LoggerFactory.getLogger(ChannelInboundAdapter.class);

    private SocketChannelPool channelPool;

    public ChannelInboundAdapter(SocketChannelPool channelPool) {
        this.channelPool = channelPool;
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        super.channelRead(ctx, msg);
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        super.channelActive(ctx);
        Channel ch = ctx.channel();
        ChannelFuture channelFuture = ch.closeFuture();
        if(!channelFuture.channel().isActive()){
            return;
        }

        InetSocketAddress remote =
                (InetSocketAddress) ch.remoteAddress();
        InetSocketAddress local =
                (InetSocketAddress) ch.localAddress();
        if (channelPool.register(ch)) {
            log.info(
                    "CHANNEL ACTIVE | id={} | remote={}:{} | local={}:{} | thread={}",
                    ch.id().asShortText(),
                    remote.getAddress().getHostAddress(),
                    remote.getPort(),
                    local.getAddress().getHostAddress(),
                    local.getPort(),
                    Thread.currentThread().getName()
            );
        } else {
            log.info(
                    "CHANNEL NOT ALLOWED | id={} | remote={}:{} | local={}:{} | thread={}",
                    ch.id().asShortText(),
                    remote.getAddress().getHostAddress(),
                    remote.getPort(),
                    local.getAddress().getHostAddress(),
                    local.getPort(),
                    Thread.currentThread().getName()
            );
        }
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        super.channelInactive(ctx);
        Channel ch = ctx.channel();
        channelPool.unregister(ch);

        InetSocketAddress remote =
                (InetSocketAddress) ch.remoteAddress();

        log.info(
                "CHANNEL INACTIVE | id={} | remote={}:{}",
                ch.id().asShortText(),
                remote.getAddress().getHostAddress(),
                remote.getPort()
        );
    }
}