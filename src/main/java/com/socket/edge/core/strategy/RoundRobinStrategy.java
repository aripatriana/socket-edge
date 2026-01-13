package com.socket.edge.core.strategy;

import com.socket.edge.core.MessageContext;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public final class RoundRobinStrategy<T>
        implements SelectionStrategy<T> {

    private final AtomicInteger index = new AtomicInteger(0);

    @Override
    public T next(List<T> candidates, MessageContext messageContext) {
        if (candidates.isEmpty()) {
            throw new IllegalStateException("No candidates available");
        }
        int i = Math.abs(index.getAndIncrement());
        return candidates.get(i % candidates.size());
    }
}
