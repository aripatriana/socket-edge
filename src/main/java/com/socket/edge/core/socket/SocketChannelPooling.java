package com.socket.edge.core.socket;

import com.socket.edge.model.EndpointKey;
import com.socket.edge.model.SocketEndpoint;
import io.netty.channel.Channel;

import java.net.InetSocketAddress;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Manages active socket channels and their association with endpoints.
 *
 * <p>{@code SocketChannelPooling} acts as a central registry for all
 * active {@link SocketChannel} instances belonging to a single
 * {@link AbstractSocket}. It provides:
 * <ul>
 *   <li>Channel admission control based on endpoint allowlist</li>
 *   <li>Fast lookup by channel ID</li>
 *   <li>Endpoint-based indexing</li>
 *   <li>Lifecycle management (add, remove, close)</li>
 *   <li>Versioning for detecting structural changes</li>
 * </ul>
 *
 * <p>The pool enforces endpoint validation on channel creation.
 * Channels that do not match the configured allowlist or endpoint
 * definition are immediately closed.</p>
 *
 * <p>Thread safety:
 * <ul>
 *   <li>Uses {@link ConcurrentHashMap} and concurrent sets</li>
 *   <li>Channel additions and removals are safe for concurrent access</li>
 *   <li>Version counter is maintained using {@link AtomicLong}</li>
 * </ul>
 *
 * @author Ari Patriana
 * @since 1.0.0
 */
public class SocketChannelPooling {

    /**
     * Monotonically increasing version indicating structural changes
     * to the channel pool.
     */
    private final AtomicLong version = new AtomicLong(1);

    /**
     * Owning socket instance.
     */
    private final AbstractSocket abstractSocket;

    /**
     * Active channels indexed by Netty channel ID.
     */
    private final ConcurrentMap<String, SocketChannel> activeChannels = new ConcurrentHashMap<>();

    /**
     * Index of channels grouped by endpoint.
     */
    private final ConcurrentMap<EndpointKey, Set<SocketChannel>> endpointIndex = new ConcurrentHashMap<>();

    /**
     * Creates a new channel pool for the given socket.
     *
     * @param abstractSocket owning socket
     */
    public SocketChannelPooling(AbstractSocket abstractSocket) {
        this.abstractSocket = abstractSocket;
    }

    /**
     * Attempts to add a Netty channel to the pool.
     *
     * <p>The channel is validated against the socket allowlist
     * and endpoint configuration. If validation fails, the
     * channel is immediately closed.</p>
     *
     * @param ch Netty channel
     * @return {@code true} if the channel was added successfully,
     *         {@code false} otherwise
     */
    public boolean addChannel(Channel ch) {
        String remoteIp =
                ((InetSocketAddress) ch.remoteAddress())
                        .getAddress()
                        .getHostAddress();

        if (!abstractSocket.allowlist().isEmpty()
                && abstractSocket.allowlist().stream()
                .noneMatch(ep -> remoteIp.equals(ep.host()))) {

            ch.close();
            return false;
        }

        int remotePort =
                ((InetSocketAddress) ch.remoteAddress())
                        .getPort();

        SocketEndpoint se = abstractSocket.resolveEndpoint(remoteIp, remotePort);
        if (se == null) {
            ch.close();
            return false;
        }

        SocketChannel sc = new SocketChannel(
                abstractSocket.getId(),
                ch,
                se,
                abstractSocket.resolveTelemetry(se.id())
        );

        SocketChannel existing =
                activeChannels.putIfAbsent(ch.id().asLongText(), sc);

        if (existing != null) {
            return false;
        }

        endpointIndex
                .computeIfAbsent(EndpointKey.from(se), k -> ConcurrentHashMap.newKeySet())
                .add(sc);

        updateVersion();
        return true;
    }

    /**
     * Removes and closes all channels associated with the given endpoint.
     *
     * @param key endpoint identifier
     * @return number of channels removed
     */
    public int removeByEndpoint(EndpointKey key) {
        Set<SocketChannel> channels = endpointIndex.remove(key);
        if (channels == null || channels.isEmpty()) {
            return 0;
        }

        channels.forEach(sc -> {
            activeChannels.remove(sc.channelId().asLongText());
            if (sc.isActive()) {
                sc.close();
            }
        });

        updateVersion();
        return channels.size();
    }

    /**
     * Removes a channel from the pool.
     *
     * @param ch Netty channel
     */
    public void removeChannel(Channel ch) {
        SocketChannel sc = activeChannels.remove(ch.id().asLongText());
        if (sc == null) {
            return;
        }

        EndpointKey key = endpointKey(sc.getSocketEndpoint());
        Set<SocketChannel> set = endpointIndex.get(key);

        if (set != null) {
            set.remove(sc);
            if (set.isEmpty()) {
                endpointIndex.remove(key);
            }
            updateVersion();
        }
    }

    /**
     * Resolves a socket channel by Netty channel.
     *
     * @param ch Netty channel
     * @return socket channel or {@code null}
     */
    public SocketChannel getChannel(Channel ch) {
        return activeChannels.get(ch.id().asLongText());
    }

    /**
     * Resolves a socket channel by channel ID.
     *
     * @param channelId Netty channel ID
     * @return socket channel or {@code null}
     */
    public SocketChannel getChannelById(String channelId) {
        return activeChannels.get(channelId);
    }

    /**
     * Returns all channels associated with the given endpoint.
     *
     * @param se socket endpoint
     * @return set of socket channels
     */
    public Set<SocketChannel> getAllByEndpoint(SocketEndpoint se) {
        return endpointIndex.getOrDefault(EndpointKey.from(se), Set.of());
    }

    /**
     * Returns all active channels.
     *
     * @return list of active socket channels
     */
    public List<SocketChannel> activeChannels() {
        return activeChannels.values().stream()
                .filter(SocketChannel::isActive)
                .toList();
    }

    public List<SocketChannel> availableChannels() {
        return activeChannels.values().stream()
                .filter(sc -> sc.isAvailable(System.currentTimeMillis()))
                .toList();
    }

    /**
     * Returns all channels (active and inactive).
     *
     * @return list of socket channels
     */
    public List<SocketChannel> getAllChannel() {
        return activeChannels.values().stream().toList();
    }

    /**
     * Closes and removes all channels from the pool.
     */
    public void closeAll() {
        activeChannels.values().forEach(ch -> {
            if (ch.isActive()) {
                ch.close();
            }
        });

        activeChannels.clear();
        endpointIndex.clear();
        updateVersion();
    }

    /**
     * Returns the current pool version.
     *
     * @return version counter
     */
    public AtomicLong getVersion() {
        return version;
    }

    /**
     * Increments the pool version.
     */
    public void updateVersion() {
        version.incrementAndGet();
    }

    private EndpointKey endpointKey(SocketEndpoint se) {
        if (se == null) {
            throw new IllegalStateException("SocketEndpoint cannot be null");
        }
        return EndpointKey.from(se);
    }

}
