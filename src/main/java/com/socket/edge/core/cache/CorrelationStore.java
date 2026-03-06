package com.socket.edge.core.cache;

import com.socket.edge.model.CorrelationEntry;

/**
 * Abstraction for storing and resolving correlation entries.
 *
 * <p>{@code CorrelationStore} is used to manage request–response
 * correlation in asynchronous messaging flows, such as ISO 8583
 * processing pipelines.</p>
 *
 * <p>Typical responsibilities:
 * <ul>
 *   <li>Store correlation data when an inbound request is received</li>
 *   <li>Resolve correlation data when an outbound response arrives</li>
 *   <li>Remove correlation entries after completion or timeout</li>
 * </ul>
 *
 * <p>Implementations must be thread-safe.</p>
 *
 * @author Ari Patriana
 * @since 1.0.0
 */
public interface CorrelationStore {

    /**
     * Stores a correlation entry.
     *
     * @param key     correlation key
     * @param inbound correlation entry
     */
    void put(String key, CorrelationEntry inbound);

    /**
     * Retrieves a correlation entry by key.
     *
     * @param key correlation key
     * @return correlation entry or {@code null} if not found
     */
    CorrelationEntry get(String key);

    /**
     * Removes a correlation entry by key.
     *
     * @param key correlation key
     */
    void remove(String key);

    /**
     * Returns the current number of correlation entries.
     *
     * @return store size
     */
    int size();

    /**
     * Shuts down the correlation store and releases resources.
     *
     * <p>
     * Default implementation is a no-op. Implementations that
     * manage background threads or scheduled executors should
     * override this method.
     * </p>
     */
    default void shutdown() {
        // no-op by default
    }
}
