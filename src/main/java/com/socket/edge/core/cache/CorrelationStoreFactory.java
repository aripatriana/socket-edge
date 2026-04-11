package com.socket.edge.core.cache;

import com.hazelcast.core.HazelcastInstance;
import com.socket.edge.model.CorrelationEntry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Factory for creating the appropriate {@link CorrelationStore} based on server mode.
 *
 * <p>Cache selection is automatic — no manual {@code engine.cache.type} needed:</p>
 * <ul>
 *   <li><b>STANDALONE</b> → {@link CaffeineCorrelationStore} (in-memory, single JVM)</li>
 *   <li><b>CLUSTER</b> → {@link HazelcastCorrelationStore} (distributed, cross-node)</li>
 * </ul>
 *
 * <p>This eliminates the config field that was always overridden anyway in cluster mode.</p>
 *
 * @author Ari Patriana
 * @since 3.0.0
 */
public final class CorrelationStoreFactory {

    private static final Logger log = LoggerFactory.getLogger(CorrelationStoreFactory.class);

    private CorrelationStoreFactory() {}

    /**
     * Creates an in-memory correlation store for standalone mode.
     *
     * @param ttlMs time-to-live for entries in milliseconds
     * @return Caffeine-backed correlation store
     */
    public static CorrelationStore standalone(long ttlMs) {
        log.info("Creating Caffeine correlation store (standalone mode), ttl={}ms", ttlMs);
        return new CaffeineCorrelationStore(ttlMs);
    }

    /**
     * Creates an in-memory correlation store with custom max size.
     *
     * @param ttlMs   time-to-live in milliseconds
     * @param maxSize maximum number of entries
     * @return Caffeine-backed correlation store
     */
    public static CorrelationStore standalone(long ttlMs, long maxSize) {
        log.info("Creating Caffeine correlation store (standalone mode), ttl={}ms, maxSize={}",
                ttlMs, maxSize);
        return new CaffeineCorrelationStore(ttlMs, maxSize);
    }

    /**
     * Creates a distributed correlation store for cluster mode.
     *
     * @param hazelcast Hazelcast instance
     * @param mapName   name of the distributed map
     * @param ttlMs     time-to-live for entries in milliseconds
     * @return Hazelcast-backed correlation store
     */
    public static CorrelationStore cluster(HazelcastInstance hazelcast, String mapName, long ttlMs) {
        log.info("Creating Hazelcast correlation store (cluster mode), map={}, ttl={}ms",
                mapName, ttlMs);
        return new HazelcastCorrelationStore(
                hazelcast.<String, CorrelationEntry>getMap(mapName),
                ttlMs
        );
    }
}
