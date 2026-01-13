package com.socket.edge.model;

import com.socket.edge.core.strategy.WeightedCandidate;

public record SocketEndpoint(
        String host,
        int port,
        int weight,
        int priority
) implements WeightedCandidate {
    @Override
    public int getWeight() {
        return weight;
    }

    @Override
    public int getPriority() {
        return priority;
    }
}