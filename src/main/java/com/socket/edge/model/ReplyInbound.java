package com.socket.edge.model;

import com.socket.edge.core.socket.SocketChannel;
import io.netty.channel.Channel;

public record ReplyInbound(
    String correlationKey,
    String socketId,
    SocketChannel socketChannel){
}
