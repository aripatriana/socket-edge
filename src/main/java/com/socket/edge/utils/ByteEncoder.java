package com.socket.edge.utils;

import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageEncoder;

import java.util.List;

public final class ByteEncoder
        extends MessageToMessageEncoder<byte[]> {

    @Override
    protected void encode(
            ChannelHandlerContext ctx,
            byte[] msg,
            List<Object> out
    ) {
        out.add(Unpooled.wrappedBuffer(msg));
    }
}