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

        int highestPriority = candidates.stream()
                .mapToInt(WeightedCandidate::getPriority)
                .max()
                .orElseThrow();

        List<T> list = candidates.stream()
                .filter(c -> c.getPriority() == highestPriority)
                .toList();

        int totalWeight = list.stream()
                .mapToInt(c -> Math.max(1, c.getWeight()))
                .sum();

        int pos = Math.floorMod(index.getAndIncrement(), totalWeight);

        for (T c : list) {
            pos -= Math.max(1, c.getWeight());
            if (pos < 0) return c;
        }

        throw new IllegalStateException("Weighted selection failed");
    }
}
