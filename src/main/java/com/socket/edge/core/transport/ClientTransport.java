package com.socket.edge.core.transport;

import com.socket.edge.core.MessageContext;
import com.socket.edge.core.socket.AbstractSocket;
import com.socket.edge.core.socket.SocketChannel;
import com.socket.edge.core.strategy.SelectionStrategy;
import com.socket.edge.constant.SocketState;
import com.socket.edge.model.VersionedCandidates;

import java.util.List;
import java.util.Objects;

public final class ClientTransport implements Transport {

    private final List<AbstractSocket> sockets;
    private final SelectionStrategy<SocketChannel> strategy;

    public ClientTransport(
            List<AbstractSocket> sockets,
            SelectionStrategy<SocketChannel> strategy
    ) {
        this.sockets = sockets;
        this.strategy = strategy;
    }

    public List<AbstractSocket> getSockets() {
        return sockets;
    }

    public void addSocket(AbstractSocket socket) {
        this.sockets.add(socket);
    }

    public void removeSocket(AbstractSocket socket) {
        this.sockets.remove(socket);
    }

    @Override
    public void send(MessageContext ctx) {

        List<SocketChannel> actives = sockets.stream()
                .map(AbstractSocket::channelPool)
                .filter(Objects::nonNull)
                .flatMap(p -> p.activeChannels().stream())
                .filter(Objects::nonNull)
                .filter(SocketChannel::isActive)
                .toList();

        if (actives.isEmpty()) {
            throw new IllegalStateException("No active client socket");
        }

        long version = sockets.stream()
                .map(AbstractSocket::channelPool)
                .filter(Objects::nonNull)
                .mapToLong(p -> p.getVersion().get())
                .max()
                .orElse(-1L);

        SocketChannel channel = strategy.next(new VersionedCandidates<>(version, actives), ctx);
        channel.increment();

        ctx.addProperty("back_forward_channel", channel);
        channel.send(ctx.getRawBytes());
    }

    @Override
    public boolean isUp() {
        return sockets.stream()
                .anyMatch(socket -> socket.getState() == SocketState.UP);
    }

    @Override
    public void shutdown() {

    }
}
