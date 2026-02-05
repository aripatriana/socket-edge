package com.socket.edge.core.transport;

import com.socket.edge.core.MessageContext;

/**
 * Transport represents a low-level message delivery mechanism.
 *
 * <p>
 * Implementations of this interface are responsible for:
 * <ul>
 *   <li>Sending messages to an external system or endpoint</li>
 *   <li>Reporting their operational state</li>
 *   <li>Releasing underlying resources during shutdown</li>
 * </ul>
 * </p>
 *
 * <p>
 * A {@code Transport} instance is expected to be:
 * <ul>
 *   <li>Thread-safe, unless explicitly documented otherwise</li>
 *   <li>Managed by a lifecycle owner (e.g. {@code TransportProvider})</li>
 * </ul>
 * </p>
 */
public interface Transport {

    /**
     * Sends a message using this transport.
     *
     * <p>
     * Implementations may perform synchronous or asynchronous delivery,
     * depending on the transport type.
     * </p>
     *
     * @param ctx message context containing payload and metadata
     * @throws RuntimeException if sending fails or transport is not active
     */
    void send(MessageContext ctx);

    /**
     * Indicates whether this transport is currently active and able to send messages.
     *
     * <p>
     * Returning {@code false} means the transport should not be used
     * for message delivery.
     * </p>
     *
     * @return {@code true} if the transport is active, otherwise {@code false}
     */
    boolean isActive();

    /**
     * Shuts down this transport and releases all underlying resources.
     *
     * <p>
     * After this method is called:
     * <ul>
     *   <li>{@link #isActive()} should return {@code false}</li>
     *   <li>{@link #send(MessageContext)} should no longer be invoked</li>
     * </ul>
     * </p>
     *
     * <p>
     * This method must be idempotent and safe to call multiple times.
     * </p>
     */
    void shutdown();
}
