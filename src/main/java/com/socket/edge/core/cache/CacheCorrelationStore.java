package com.socket.edge.core.cache;

import com.socket.edge.model.CorrelationEntry;

import java.util.concurrent.*;

public class CacheCorrelationStore implements CorrelationStore {

    private static class Entry {
        final CorrelationEntry channel;
        final long expireAt;

        Entry(CorrelationEntry ch, long ttlMs) {
            this.channel = ch;
            this.expireAt = System.nanoTime()
                    + TimeUnit.MILLISECONDS.toNanos(ttlMs);
        }

        boolean expired(long now) {
            return now > expireAt;
        }
    }

    private final long ttlMs;
    private final ConcurrentHashMap<String, Entry> store = new ConcurrentHashMap<>();
    private final ScheduledExecutorService cleaner;

    public CacheCorrelationStore(long ttlMs) {
        this.ttlMs = ttlMs;
        this.cleaner = Executors.newSingleThreadScheduledExecutor(
                r -> new Thread(r, "correlation-cleaner")
        );
        startCleanup();
    }

    @Override
    public void put(String key, CorrelationEntry inbound) {
        store.put(key, new Entry(inbound, ttlMs));
    }

    @Override
    public CorrelationEntry get(String key) {
        Entry e = store.get(key);
        if (e == null) return null;

        long now = System.nanoTime();
        if (e.expired(now)) {
            store.remove(key, e);
            return null;
        }
        return e.channel;
    }

    @Override
    public void remove(String key) {
        store.remove(key);
    }

    private void startCleanup() {
        long interval = Math.min(ttlMs, TimeUnit.SECONDS.toMillis(30));

        cleaner.scheduleAtFixedRate(() -> {
            long now = System.nanoTime();
            store.entrySet().removeIf(e -> e.getValue().expired(now));
        }, interval, interval, TimeUnit.MILLISECONDS);
    }

    public void shutdown() {
        cleaner.shutdown();
    }

    @Override
    public int size() {
        return store.size();
    }
}

