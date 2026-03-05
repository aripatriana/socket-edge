package com.socket.edge.constant;

/**
 * Represents the lifecycle state of a socket connection
 * within the Load Balancer.
 *
 * <p>
 * This state is used to control connection flow, routing eligibility,
 * retry mechanism, and transaction safety.
 * </p>
 *
 * <ul>
 *     <li>{@link #DOWN}    - Socket is not connected and not ready to accept traffic.</li>
 *     <li>{@link #STANDBY} - Socket is initialized but not yet actively handling traffic.</li>
 *     <li>{@link #LISTEN}  - Server-side socket is listening for incoming connections.</li>
 *     <li>{@link #WAIT}    - Socket is waiting for handshake, upstream readiness,
 *                            or dependency connection (e.g., backend not yet ready).</li>
 *     <li>{@link #ACTIVE}  - Socket is fully connected and eligible for transaction processing.</li>
 *     <li>{@link #ERROR}   - Socket encountered an unrecoverable error condition.</li>
 * </ul>
 *
 * <p>
 * Typical state transitions:
 * <pre>
 * DOWN -> STANDBY -> LISTEN -> WAIT -> ACTIVE
 * ACTIVE -> ERROR -> DOWN
 * </pre>
 * </p>
 *
 * <p>
 * Important:
 * Transactions must only be processed when state is {@link #ACTIVE}.
 * </p>
 */
public enum SocketState {

    /**
     * Socket is fully disconnected.
     * No read/write operations are allowed.
     */
    DOWN,

    /**
     * Socket is initialized but not yet active.
     * Resources may be allocated but no traffic is processed.
     */
    STANDBY,

    /**
     * Server socket is listening for inbound connection requests.
     */
    LISTEN,

    /**
     * Connection established but waiting for dependency readiness,
     * handshake completion, or upstream/backend availability.
     */
    WAIT,

    /**
     * Socket is fully operational and allowed to process transactions.
     */
    ACTIVE,

    /**
     * Socket encountered an error condition.
     * Requires recovery or restart.
     */
    ERROR
}