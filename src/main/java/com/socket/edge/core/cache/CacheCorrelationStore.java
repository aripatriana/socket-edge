package com.socket.edge.core.cache;

import com.socket.edge.model.CorrelationEntry;

import java.util.concurrent.*;

/**
 * In-memory implementation of {@link CorrelationStore} with TTL-based eviction.
 *
 * <p>{@code CacheCorrelationStore} stores correlation entries for
 * request–response matching in asynchronous message flows.
 * Each entry is associated with a time-to-live (TTL) to prevent
 * memory leaks when responses never arrive.</p>
 *
 * <p>Key characteristics:
 * <ul>
 *   <li>Thread-safe access using {@link ConcurrentHashMap}</li>
 *   <li>Per-entry TTL enforcement</li>
 *   <li>Background cleanup using a scheduled executor</li>
 *   <li>Non-blocking read/write operations</li>
 * </ul>
 *
 * <p>Expiration strategy:
 * <ul>
 *   <li>Entries are lazily evicted on {@link #get(String)}</li>
 *   <li>Entries are eagerly evicted by a periodic cleanup task</li>
 * </ul>
 *
 * <p>This implementation is suitable for:
 * <ul>
 *   <li>Single-node or active–passive cluster setups</li>
 *   <li>Short-lived ISO 8583 request–response correlations</li>
 *   <li>High-throughput, low-latency routing engines</li>
 * </ul>
 *
 * @author Ari Patriana
 * @since 1.0.0
 */
public class CacheCorrelationStore implements CorrelationStore {

    /**
     * Internal cache entry with expiration metadata.
     */
    private static class Entry {

        /**
         * Correlation entry.
         */
        final CorrelationEntry channel;

        /**
         * Expiration timestamp in nanoseconds.
         */
        final long expireAt;

        Entry(CorrelationEntry ch, long ttlMs) {
            this.channel = ch;
            this.expireAt = System.nanoTime()
                    + TimeUnit.MILLISECONDS.toNanos(ttlMs);
        }

        /**
         * Checks whether this entry has expired.
         *
         * @param now current time in nanoseconds
         * @return {@code true} if expired
         */
        boolean expired(long now) {
            return now > expireAt;
        }
    }

    /**
     * Time-to-live for correlation entries in milliseconds.
     */
    private final long ttlMs;

    /**
     * Correlation store keyed by correlation key.
     */
    private final ConcurrentHashMap<String, Entry> store = new ConcurrentHashMap<>();

    /**
     * Scheduled executor for periodic cleanup.
     */
    private final ScheduledExecutorService cleaner;

    /**
     * Creates a new correlation store with the given TTL.
     *
     * @param ttlMs time-to-live for entries in milliseconds
     */
    public CacheCorrelationStore(long ttlMs) {
        this.ttlMs = ttlMs;
        this.cleaner = Executors.newSingleThreadScheduledExecutor(
                r -> new Thread(r, "correlation-cleaner")
        );
        startCleanup();
    }

    /**
     * Stores a correlation entry.
     *
     * @param key     correlation key
     * @param inbound correlation entry
     */
    @Override
    public void put(String key, CorrelationEntry inbound) {
        store.put(key, new Entry(inbound, ttlMs));
    }

    /**
     * Retrieves a correlation entry by key.
     *
     * <p>If the entry has expired, it will be removed
     * and {@code null} will be returned.</p>
     *
     * @param key correlation key
     * @return correlation entry or {@code null} if not found or expired
     */
    @Override
    public CorrelationEntry get(String key) {
        Entry e = store.get(key);
        if (e == null) {
            return null;
        }

        long now = System.nanoTime();
        if (e.expired(now)) {
            store.remove(key, e);
            return null;
        }

        return e.channel;
    }

    /**
     * Removes a correlation entry by key.
     *
     * @param key correlation key
     */
    @Override
    public void remove(String key) {
        store.remove(key);
    }

    /**
     * Starts the background cleanup task.
     *
     * <p>The cleanup interval is the smaller of:
     * <ul>
     *   <li>Configured TTL</li>
     *   <li>30 seconds</li>
     * </ul>
     */
    private void startCleanup() {
        long interval = Math.min(ttlMs, TimeUnit.SECONDS.toMillis(30));

        cleaner.scheduleAtFixedRate(() -> {
            long now = System.nanoTime();
            store.entrySet().removeIf(e -> e.getValue().expired(now));
        }, interval, interval, TimeUnit.MILLISECONDS);
    }

    /**
     * Shuts down the cleanup executor.
     */
    @Override
    public void shutdown() {
        cleaner.shutdown();
    }

    /**
     * Returns the current number of entries in the store.
     *
     * @return store size
     */
    @Override
    public int size() {
        return store.size();
    }
}

