package com.socket.edge.core.cache;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.RemovalCause;
import com.socket.edge.model.CorrelationEntry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;

/**
 * In-memory {@link CorrelationStore} backed by Caffeine cache.
 *
 * <p>Replaces the previous {@code CacheCorrelationStore} which used
 * {@code ConcurrentHashMap} + manual TTL eviction via
 * {@code ScheduledExecutorService}.</p>
 *
 * <h3>Why Caffeine over manual ConcurrentHashMap:</h3>
 * <ul>
 *   <li><b>O(1) amortized eviction</b> — Caffeine uses TimerWheel internally,
 *       not full-table scan. Entries are evicted incrementally during normal
 *       cache operations.</li>
 *   <li><b>Size-bounded</b> — prevents OOM if downstream dies and responses
 *       never arrive. Oldest entries are evicted when max size is reached.</li>
 *   <li><b>No extra thread</b> — eviction piggybacks on cache operations.
 *       No ScheduledExecutorService needed for cleanup.</li>
 *   <li><b>Near-zero overhead</b> — ~5ns per operation, consistently
 *       outperforms ConcurrentHashMap with manual TTL in benchmarks.</li>
 * </ul>
 *
 * <h3>Usage pattern in ISO 8583 flow:</h3>
 * <pre>
 *   INBOUND:  put(correlationKey, entry)    — store when request arrives
 *   OUTBOUND: get(correlationKey)           — lookup when response arrives
 *             remove(correlationKey)         — cleanup after match
 *   TIMEOUT:  automatic eviction by TTL     — safety net for unmatched entries
 * </pre>
 *
 * @author Ari Patriana
 * @since 3.0.0
 */
public class CaffeineCorrelationStore implements CorrelationStore {

    private static final Logger log = LoggerFactory.getLogger(CaffeineCorrelationStore.class);

    /**
     * Default maximum entries as safety bound.
     * Prevents unbounded memory growth when downstream is unresponsive.
     * At ~200 bytes per CorrelationEntry, 500K entries ≈ 100MB.
     */
    private static final long DEFAULT_MAX_SIZE = 500_000;

    private final Cache<String, CorrelationEntry> cache;

    /**
     * Creates a new Caffeine-backed correlation store.
     *
     * @param ttlMs   time-to-live for entries in milliseconds
     * @param maxSize maximum number of entries (0 = use default 500K)
     */
    public CaffeineCorrelationStore(long ttlMs, long maxSize) {
        this.cache = Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofMillis(ttlMs))
                .maximumSize(maxSize > 0 ? maxSize : DEFAULT_MAX_SIZE)
                .removalListener((String key, CorrelationEntry value, RemovalCause cause) -> {
                    if (cause == RemovalCause.EXPIRED) {
                        log.warn("Correlation entry expired: key={}, created={}",
                                key, value != null ? value.createdAt() : "?");
                    } else if (cause == RemovalCause.SIZE) {
                        log.error("Correlation entry evicted by size limit: key={} — "
                                + "this indicates downstream is not responding. "
                                + "Consider increasing cache TTL or investigating downstream health.",
                                key);
                    }
                })
                .build();
    }

    /**
     * Creates a new Caffeine-backed correlation store with default max size.
     *
     * @param ttlMs time-to-live for entries in milliseconds
     */
    public CaffeineCorrelationStore(long ttlMs) {
        this(ttlMs, DEFAULT_MAX_SIZE);
    }

    @Override
    public void put(String key, CorrelationEntry entry) {
        cache.put(key, entry);
    }

    @Override
    public CorrelationEntry get(String key) {
        return cache.getIfPresent(key);
    }

    @Override
    public void remove(String key) {
        cache.invalidate(key);
    }

    @Override
    public int size() {
        cache.cleanUp();
        return (int) cache.estimatedSize();
    }

    @Override
    public void shutdown() {
        cache.invalidateAll();
        cache.cleanUp();
        log.info("CaffeineCorrelationStore shutdown, final size={}", cache.estimatedSize());
    }
}
