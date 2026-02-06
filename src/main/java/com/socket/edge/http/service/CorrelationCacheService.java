package com.socket.edge.http.service;

import com.socket.edge.core.cache.CorrelationStore;

public class CorrelationCacheService {

    private final CorrelationStore correlationStore;

    public CorrelationCacheService(CorrelationStore correlationStore) {
        this.correlationStore = correlationStore;
    }

    public int countSize() {
        return correlationStore.size();
    }
}
