package com.socket.edge.core.transport;

import com.socket.edge.core.MessageContext;
import com.socket.edge.core.socket.NettyServerSocket;
import com.socket.edge.core.socket.SocketChannel;
import com.socket.edge.core.strategy.SelectionStrategy;

import java.util.List;

public final class ServerTransport implements Transport {

    private final NettyServerSocket socket;
    private final SelectionStrategy<SocketChannel> strategy;

    public ServerTransport(
            NettyServerSocket socket,
            SelectionStrategy<SocketChannel> strategy
    ) {
        this.socket = socket;
        this.strategy = strategy;
    }

    @Override
    public void send(MessageContext ctx) {

        List<SocketChannel> actives =
                socket.channelPool().activeChannels();

        if (actives.isEmpty()) {
            throw new IllegalStateException(
                    "No active socket channel"
            );
        }

        SocketChannel channel = strategy.next(actives, ctx);
        channel.increment();

        ctx.addProperty("back_forward_channel", channel);
        channel.send(ctx.getRawBytes());
    }

    @Override
    public boolean isUp() {
        return !socket.channelPool().activeChannels().isEmpty();
    }
}

