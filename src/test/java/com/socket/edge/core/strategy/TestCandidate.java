package com.socket.edge.core.strategy;

import com.socket.edge.core.LoadAware;

import java.util.concurrent.atomic.AtomicInteger;

class TestCandidate implements WeightedCandidate, LoadAware {

    private final String id;
    private final int weight;
    private final int priority;
    private final AtomicInteger inflight = new AtomicInteger();

    TestCandidate(String id, int weight, int priority, int inflight) {
        this.id = id;
        this.weight = weight;
        this.priority = priority;
        this.inflight.set(inflight);
    }

    @Override public int getWeight() { return weight; }
    @Override public int getPriority() { return priority; }

    @Override public int inflight() {
        return inflight.get();
    }

    @Override public void increment() {
        inflight.incrementAndGet();
    }

    @Override public void decrement() {
        inflight.decrementAndGet();
    }

    @Override public String toString() { return id; }
}