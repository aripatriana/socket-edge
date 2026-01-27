package com.socket.edge.core.cache;

import com.socket.edge.model.CorrelationEntry;

public interface CorrelationStore {
    void put(String key, CorrelationEntry inbound);
    CorrelationEntry get(String key);
    void remove(String key);
    int size();
}
