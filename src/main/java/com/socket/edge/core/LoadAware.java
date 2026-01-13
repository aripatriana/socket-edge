package com.socket.edge.core;

public interface LoadAware {
    int inflight();
    void increment();
    void decrement();
}
