package com.socket.edge.core.transport;

import com.socket.edge.core.MessageContext;
import com.socket.edge.core.socket.NettyClientSocket;
import com.socket.edge.core.socket.SocketChannel;
import com.socket.edge.core.strategy.SelectionStrategy;

import java.util.List;
import java.util.Objects;

public final class ClientTransport implements Transport {

    private final List<NettyClientSocket> sockets;
    private final SelectionStrategy<SocketChannel> strategy;

    public ClientTransport(
            List<NettyClientSocket> sockets,
            SelectionStrategy<SocketChannel> strategy
    ) {
        this.sockets = sockets;
        this.strategy = strategy;
    }

    @Override
    public void send(MessageContext ctx) {

        List<SocketChannel> actives = sockets.stream()
                .map(NettyClientSocket::channelPool)
                .filter(Objects::nonNull)
                .flatMap(p -> p.activeChannels().stream())
                .filter(Objects::nonNull)
                .filter(SocketChannel::isActive)
                .toList();

        if (actives.isEmpty()) {
            throw new IllegalStateException("No active client socket");
        }

        SocketChannel channel = strategy.next(actives, ctx);
        channel.increment();

        ctx.addProperty("back_forward_channel", channel);
        channel.send(ctx.getRawBytes());
    }

    @Override
    public boolean isUp() {
        return sockets.stream().anyMatch(NettyClientSocket::isUp);
    }
}
