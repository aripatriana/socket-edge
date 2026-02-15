package com.socket.edge.core.transport;

import com.socket.edge.constant.SocketState;
import com.socket.edge.core.MessageContext;
import com.socket.edge.core.socket.AbstractSocket;
import com.socket.edge.core.socket.SocketChannel;
import com.socket.edge.core.strategy.SelectionStrategy;
import com.socket.edge.model.VersionedCandidates;

import java.util.List;

/**
 * {@code ServerTransport} is a {@link Transport} implementation that
 * delivers messages through a pool of server-side {@link SocketChannel}s.
 *
 * <p>
 * Message delivery is performed by selecting one active channel from
 * the underlying socket's channel pool using a
 * {@link SelectionStrategy}.
 * </p>
 *
 * <p>
 * This transport does not own the lifecycle of the underlying socket.
 * Shutdown is therefore a no-op and is expected to be handled externally.
 * </p>
 *
 * @author Ari Patriana
 * @since 1.0.0
 */
public final class ServerTransport implements Transport {

    /**
     * Underlying socket that manages the channel pool.
     */
    private final AbstractSocket socket;

    /**
     * Strategy used to select a {@link SocketChannel}
     * from the active channel candidates.
     */
    private final SelectionStrategy<SocketChannel> strategy;

    /**
     * Creates a new {@code ServerTransport}.
     *
     * @param socket socket providing access to the channel pool
     * @param strategy channel selection strategy
     * @throws NullPointerException if socket or strategy is {@code null}
     */
    public ServerTransport(
            AbstractSocket socket,
            SelectionStrategy<SocketChannel> strategy
    ) {
        this.socket = socket;
        this.strategy = strategy;
    }

    /**
     * Sends a message by selecting an active {@link SocketChannel}
     * and writing the raw payload to it.
     *
     * <p>
     * Selection is delegated to {@link SelectionStrategy#next(Object, MessageContext)}
     * using a versioned snapshot of currently active channels.
     * </p>
     *
     * @param ctx message context containing payload and metadata
     * @throws IllegalStateException if no active socket channel is available
     */
    @Override
    public void send(MessageContext ctx) {
        int maxRetry = 3;
        long version = socket.channelPool().getVersion().get();

        for (int attempt = 1; attempt <= maxRetry; attempt++) {
            List<SocketChannel> availables = socket.channelPool().availableChannels();

            if (availables.isEmpty()) {
                throw new IllegalStateException(
                        "No available socket channel (all unhealthy or cooldown)"
                );
            }

            SocketChannel selected = strategy.next(
                    new VersionedCandidates<>(version, availables),
                    ctx
            );

            boolean success = selected.send(ctx.getRawBytes());
            if (success) {
                selected.increment();
                selected.markSuccess();

                // Expose selected channel for tracing / debugging / callback purpose
                ctx.addProperty("back_forward_channel", selected);
                return;
            }

            selected.markFailure(System.currentTimeMillis());
        }

        throw new IllegalStateException("Send failed after " + maxRetry + " retries");
    }

    /**
     * Indicates whether this transport is able to send messages.
     *
     * <p>
     * A {@code ServerTransport} is considered active when:
     * <ul>
     *   <li>The underlying socket state is {@link SocketState#ACTIVE}</li>
     *   <li>At least one active {@link SocketChannel} exists</li>
     * </ul>
     * </p>
     *
     * @return {@code true} if the transport is active, otherwise {@code false}
     */
    @Override
    public boolean isActive() {
        boolean stateActive = socket.getState() == SocketState.ACTIVE;
        boolean hasActiveChannel = !socket.channelPool()
                .activeChannels()
                .isEmpty();

        return stateActive && hasActiveChannel;
    }

    /**
     * Shuts down this transport.
     *
     * <p>
     * This implementation performs no action because the lifecycle
     * of the underlying socket and channels is managed externally.
     * </p>
     */
    @Override
    public void shutdown() {
        // do nothing
    }
}

