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
        if (candidates == null || candidates.isEmpty()) {
            throw new IllegalStateException("No candidates available");
        }

        // 1. Cari priority tertinggi
        int highestPriority = candidates.stream()
                .mapToInt(WeightedCandidate::getPriority)
                .max()
                .orElseThrow();

        // 2. Filter kandidat dengan priority tertinggi
        List<T> priorityCandidates = candidates.stream()
                .filter(c -> c.getPriority() == highestPriority)
                .toList();

        // 3. Bangun weighted list
        List<T> weightedList = new ArrayList<>();
        for (T candidate : priorityCandidates) {
            int weight = Math.max(1, candidate.getWeight());
            for (int i = 0; i < weight; i++) {
                weightedList.add(candidate);
            }
        }

        // 4. Round robin
        int i = Math.abs(index.getAndIncrement());
        return weightedList.get(i % weightedList.size());
    }
}
