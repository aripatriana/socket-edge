package com.socket.edge.core.socket;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;


public class ChannelInboundAdapter extends ChannelInboundHandlerAdapter {

    private static final Logger log = LoggerFactory.getLogger(ChannelInboundAdapter.class);

    private SocketChannelPooling channelPool;

    public ChannelInboundAdapter(SocketChannelPooling channelPool) {
        this.channelPool = channelPool;
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        super.channelRead(ctx, msg);
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        Channel ch = ctx.channel();
        InetSocketAddress remote =
                (InetSocketAddress) ch.remoteAddress();
        InetSocketAddress local =
                (InetSocketAddress) ch.localAddress();
        if (channelPool.addChannel(ch)) {
            super.channelActive(ctx);
            log.info(
                    "CHANNEL ACTIVE | id={} | remote={}:{} | local={}:{} | thread={}",
                    ch.id().asShortText(),
                    remote.getHostString(),
                    remote.getPort(),
                    local.getHostString(),
                    local.getPort(),
                    Thread.currentThread().getName()
            );
        } else {
            log.warn(
                    "CHANNEL NOT ALLOWED | id={} | remote={}:{} | local={}:{} | thread={}",
                    ch.id().asShortText(),
                    remote.getHostString(),
                    remote.getPort(),
                    local.getHostString(),
                    local.getPort(),
                    Thread.currentThread().getName()
            );

            ch.close();
        }
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        super.channelInactive(ctx);
        Channel ch = ctx.channel();
        channelPool.removeChannel(ch);

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