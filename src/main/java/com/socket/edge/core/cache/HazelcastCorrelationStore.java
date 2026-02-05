package com.socket.edge.core.cache;

import com.hazelcast.map.IMap;
import com.socket.edge.model.CorrelationEntry;

import java.util.concurrent.TimeUnit;

/**
 * Distributed {@link CorrelationStore} implementation backed by Hazelcast.
 *
 * <p>{@code HazelcastCorrelationStore} stores correlation entries in a
 * Hazelcast {@link IMap}, enabling request–response correlation
 * across multiple nodes in a cluster.</p>
 *
 * <p>Key characteristics:
 * <ul>
 *   <li>Distributed and cluster-safe storage</li>
 *   <li>TTL-based eviction managed by Hazelcast</li>
 *   <li>High availability and data replication</li>
 *   <li>Thread-safe access</li>
 * </ul>
 *
 * <p>This implementation is suitable for:
 * <ul>
 *   <li>Active–active or active–passive cluster setups</li>
 *   <li>Failover-safe correlation handling</li>
 *   <li>Horizontally scalable ISO 8583 engines</li>
 * </ul>
 *
 * <p>TTL handling is delegated to Hazelcast, eliminating the need
 * for a local cleanup scheduler.</p>
 *
 * @author Ari Patriana
 * @since 1.0.0
 */
public class HazelcastCorrelationStore implements CorrelationStore {

    /**
     * Time-to-live for correlation entries in milliseconds.
     */
    private final long ttlMs;

    /**
     * Distributed map storing correlation entries.
     */
    private final IMap<String, CorrelationEntry> store;

    /**
     * Creates a new Hazelcast-backed correlation store.
     *
     * @param store distributed Hazelcast map
     * @param ttlMs time-to-live for entries in milliseconds
     */
    public HazelcastCorrelationStore(
            IMap<String, CorrelationEntry> store,
            long ttlMs
    ) {
        this.store = store;
        this.ttlMs = ttlMs;
    }

    /**
     * Stores a correlation entry with TTL.
     *
     * @param key   correlation key
     * @param entry correlation entry
     */
    @Override
    public void put(String key, CorrelationEntry entry) {
        store.put(key, entry, ttlMs, TimeUnit.MILLISECONDS);
    }

    /**
     * Retrieves a correlation entry by key.
     *
     * @param key correlation key
     * @return correlation entry or {@code null} if not found or expired
     */
    @Override
    public CorrelationEntry get(String key) {
        return store.get(key);
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
     * Returns the number of correlation entries in the store.
     *
     * <p>Note: In a distributed environment, this value reflects
     * the current cluster-wide map size.</p>
     *
     * @return store size
     */
    @Override
    public int size() {
        return store.size();
    }
}
