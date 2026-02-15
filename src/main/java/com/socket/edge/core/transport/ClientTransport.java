package com.socket.edge.core.transport;

import com.socket.edge.core.MessageContext;
import com.socket.edge.core.socket.AbstractSocket;
import com.socket.edge.core.socket.SocketChannel;
import com.socket.edge.core.strategy.SelectionStrategy;
import com.socket.edge.constant.SocketState;
import com.socket.edge.model.VersionedCandidates;

import java.util.List;
import java.util.Objects;

/**
 * {@code ClientTransport} is a {@link Transport} implementation that
 * sends messages through multiple client-side sockets.
 *
 * <p>
 * Active {@link SocketChannel}s are collected from all registered
 * {@link AbstractSocket}s and one channel is selected using a
 * {@link SelectionStrategy}.
 * </p>
 *
 * <p>
 * This transport aggregates channels across multiple sockets,
 * allowing load distribution and failover between remote endpoints.
 * </p>
 *
 * <p>
 * The lifecycle of sockets and channels is managed externally.
 * Therefore, {@link #shutdown()} is a no-op.
 * </p>
 *
 * @author Ari Patriana
 * @since 1.0.0
 */
public final class ClientTransport implements Transport {

    /**
     * Collection of client sockets providing channel pools.
     */
    private final List<AbstractSocket> sockets;

    /**
     * Strategy used to select a {@link SocketChannel}
     * from aggregated active channels.
     */
    private final SelectionStrategy<SocketChannel> strategy;

    /**
     * Creates a new {@code ClientTransport}.
     *
     * @param sockets list of client sockets
     * @param strategy channel selection strategy
     * @throws NullPointerException if sockets or strategy is {@code null}
     */
    public ClientTransport(
            List<AbstractSocket> sockets,
            SelectionStrategy<SocketChannel> strategy
    ) {
        this.sockets = sockets;
        this.strategy = strategy;
    }

    /**
     * Returns the list of managed client sockets.
     *
     * <p>
     * Modifications to the returned list will affect this transport directly.
     * </p>
     *
     * @return list of client sockets
     */
    public List<AbstractSocket> getSockets() {
        return sockets;
    }

    /**
     * Adds a client socket to this transport.
     *
     * @param socket client socket to add
     */
    public void addSocket(AbstractSocket socket) {
        this.sockets.add(socket);
    }

    /**
     * Removes a client socket from this transport.
     *
     * @param socket client socket to remove
     */
    public void removeSocket(AbstractSocket socket) {
        this.sockets.remove(socket);
    }

    /**
     * Sends a message by selecting an active {@link SocketChannel}
     * from all available client sockets.
     *
     * <p>
     * Channel selection is performed using a versioned snapshot
     * derived from all underlying channel pools.
     * </p>
     *
     * @param ctx message context containing payload and metadata
     * @throws IllegalStateException if no active client socket channel exists
     */
    @Override
    public void send(MessageContext ctx) {
        int maxRetry = 3;
        long version = sockets.stream()
                .map(AbstractSocket::channelPool)
                .filter(Objects::nonNull)
                .mapToLong(p -> p.getVersion().get())
                .sum();

        for (int attempt = 1; attempt <= maxRetry; attempt++) {
            List<SocketChannel> availables = sockets.stream()
                    .map(AbstractSocket::channelPool)
                    .filter(Objects::nonNull)
                    .flatMap(p -> p.availableChannels().stream())
                    .toList();

            if (availables.isEmpty()) {
                throw new IllegalStateException("No available socket channel (all unhealthy or cooldown)" );
            }

            SocketChannel selected =
                    strategy.next(
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
     * Indicates whether at least one client socket is active.
     *
     * <p>
     * This method does not guarantee that an active channel exists,
     * only that at least one socket is in {@link SocketState#ACTIVE} state.
     * </p>
     *
     * @return {@code true} if any socket is active, otherwise {@code false}
     */
    @Override
    public boolean isActive() {
        return sockets.stream()
                .anyMatch(socket -> socket.getState() == SocketState.ACTIVE);
    }

    /**
     * Shuts down this transport.
     *
     * <p>
     * This implementation performs no action because socket and channel
     * lifecycles are managed externally.
     * </p>
     */
    @Override
    public void shutdown() {
        // do nothing
    }
}
