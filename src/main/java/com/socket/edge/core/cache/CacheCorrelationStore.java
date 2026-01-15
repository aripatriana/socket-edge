package com.socket.edge.core.cache;

import com.socket.edge.model.ReplyInbound;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class CacheCorrelationStore implements CorrelationStore {

    private static class Entry {
        final ReplyInbound channel;
        final long expireAt;

        Entry(ReplyInbound ch, long ttlMs) {
            this.channel = ch;
            this.expireAt = System.currentTimeMillis() + ttlMs;
        }
    }

    private final long ttlMs;
    private final ConcurrentHashMap<String, Entry> store =
            new ConcurrentHashMap<>();

    public CacheCorrelationStore(long ttlMs) {
        this.ttlMs = ttlMs;
        startCleanup();
    }

    @Override
    public void put(String key, ReplyInbound inbound) {
        store.put(key, new Entry(inbound, ttlMs));
    }

    @Override
    public ReplyInbound get(String key) {
        Entry e = store.get(key);
        if (e == null) return null;

        if (System.currentTimeMillis() > e.expireAt) {
            store.remove(key);
            return null;
        }
        return e.channel;
    }

    @Override
    public void remove(String key) {
        store.remove(key);
    }

    private void startCleanup() {

        Executors.newSingleThreadScheduledExecutor()
                .scheduleAtFixedRate(() -> {
                    long now = System.currentTimeMillis();
                    store.entrySet().removeIf(
                            e -> now > e.getValue().expireAt
                    );
                }, ttlMs, ttlMs, TimeUnit.MILLISECONDS);
    }
}
