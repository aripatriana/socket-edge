package com.socket.edge.utils;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageDecoder;

import java.util.List;

public final class ByteDecoder
        extends MessageToMessageDecoder<ByteBuf> {

    @Override
    protected void decode(
            ChannelHandlerContext ctx,
            ByteBuf msg,
            List<Object> out
    ) {
        byte[] bytes = new byte[msg.readableBytes()];
        msg.readBytes(bytes);

        out.add(bytes);
    }
}