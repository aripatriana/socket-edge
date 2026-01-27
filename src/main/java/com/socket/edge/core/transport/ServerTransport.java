package com.socket.edge.core.transport;

import com.socket.edge.constant.SocketState;
import com.socket.edge.core.MessageContext;
import com.socket.edge.core.socket.AbstractSocket;
import com.socket.edge.core.socket.SocketChannel;
import com.socket.edge.core.strategy.SelectionStrategy;
import com.socket.edge.model.VersionedCandidates;

import java.util.List;

public final class ServerTransport implements Transport {

    private final AbstractSocket socket;
    private final SelectionStrategy<SocketChannel> strategy;

    public ServerTransport(
            AbstractSocket socket,
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

        long version = socket.channelPool().getVersion().get();
        SocketChannel channel = strategy.next(new VersionedCandidates<>(version, actives), ctx);
        channel.increment();

        ctx.addProperty("back_forward_channel", channel);
        channel.send(ctx.getRawBytes());
    }

    @Override
    public boolean isActive() {
        boolean stateActive = socket.getState() == SocketState.ACTIVE;
        boolean hasActiveChannel = !socket.channelPool()
                .activeChannels()
                .isEmpty();

        return stateActive && hasActiveChannel;
    }

    @Override
    public void shutdown() {

    }
}

