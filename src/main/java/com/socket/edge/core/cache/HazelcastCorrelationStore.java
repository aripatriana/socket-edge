package com.socket.edge.core.cache;

import com.hazelcast.map.IMap;
import com.socket.edge.model.CorrelationEntry;

import java.util.concurrent.TimeUnit;

public class HazelcastCorrelationStore implements CorrelationStore {

    private final long ttlMs;
    private final IMap<String, CorrelationEntry> store;

    public HazelcastCorrelationStore(IMap<String, CorrelationEntry> store, long ttlMs) {
        this.store = store;
        this.ttlMs = ttlMs;
    }

    @Override
    public void put(String key, CorrelationEntry entry) {
        store.put(key, entry, ttlMs, TimeUnit.MILLISECONDS);
    }

    @Override
    public CorrelationEntry get(String key) {
        return store.get(key);
    }

    @Override
    public void remove(String key) {
        store.remove(key);
    }

    @Override
    public int size() {
        return store.size();
    }
}
