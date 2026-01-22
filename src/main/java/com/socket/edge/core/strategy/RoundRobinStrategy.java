package com.socket.edge.core.strategy;

import com.socket.edge.core.MessageContext;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public final class RoundRobinStrategy<T extends WeightedCandidate>
        implements SelectionStrategy<T> {

    private final AtomicInteger index = new AtomicInteger(0);

    @Override
    public T next(List<T> candidates, MessageContext messageContext) {
        validate(candidates);

        int highestPriority = Integer.MIN_VALUE;
        int totalWeight = 0;

        // pass 1: find highest priority
        for (T c : candidates) {
            highestPriority = Math.max(highestPriority, c.getPriority());
        }

        // pass 2: total weight only for highest priority
        for (T c : candidates) {
            if (c.getPriority() == highestPriority) {
                totalWeight += Math.max(1, c.getWeight());
            }
        }

        int pos = Math.floorMod(index.getAndIncrement(), totalWeight);

        // pass 3: select
        for (T c : candidates) {
            if (c.getPriority() != highestPriority) continue;
            pos -= Math.max(1, c.getWeight());
            if (pos < 0) return c;
        }

        throw new IllegalStateException("Weighted selection failed");
    }
}
