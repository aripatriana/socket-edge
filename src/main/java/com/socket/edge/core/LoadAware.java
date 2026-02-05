package com.socket.edge.core;

/**
 * Contract for components that track in-flight workload.
 *
 * <p>This interface is typically used to monitor and control
 * the number of active or in-progress operations, such as
 * requests, messages, or transactions.</p>
 *
 * <p>Common use cases:
 * <ul>
 *   <li>Load balancing and throttling</li>
 *   <li>Backpressure and flow control</li>
 *   <li>Capacity-aware routing or admission control</li>
 * </ul>
 *
 * <p>Implementations are expected to be thread-safe.</p>
 *
 *  @author Ari Patriana
 *  @since 1.0.0
 */
public interface LoadAware {

    /**
     * Returns the current number of in-flight operations.
     *
     * @return current in-flight count
     */
    int inflight();

    /**
     * Increments the in-flight operation counter.
     *
     * <p>Should be called when an operation starts processing.</p>
     */
    void increment();

    /**
     * Decrements the in-flight operation counter.
     *
     * <p>Should be called when an operation completes or is aborted.</p>
     */
    void decrement();
}

