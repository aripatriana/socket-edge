package com.socket.edge.core.transport;

import com.socket.edge.model.ChannelCfg;
import com.socket.edge.constant.SocketType;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * TransportProvider is a thread-safe registry and lifecycle manager
 * for {@link Transport} instances.
 *
 * <p>
 * This class is responsible for:
 * <ul>
 *   <li>Registering transports by a unique key</li>
 *   <li>Resolving transports based on channel configuration and socket type</li>
 *   <li>Managing transport lifecycle (shutdown on unregister/destroy)</li>
 * </ul>
 * </p>
 *
 * <p>
 * Internally uses {@link ConcurrentHashMap} to support concurrent access.
 * </p>
 */
public final class TransportProvider {

    /**
     * Registry of transports keyed by a unique identifier.
     *
     * <p>Key format is expected to be:</p>
     * <pre>
     *     OUTBOUND_TYPE|CHANNEL_NAME
     * </pre>
     */
    private final Map<String, Transport> transports =
            new ConcurrentHashMap<>();

    /**
     * Registers a transport only if the key is not already present.
     *
     * @param key unique transport key
     * @param transport transport instance
     * @return {@code true} if the transport was registered,
     *         {@code false} if a transport already exists for the key
     * @throws NullPointerException if key or transport is null
     */
    public boolean registerIfAbsent(String key, Transport transport) {
        Objects.requireNonNull(key, "key must not be null");
        Objects.requireNonNull(transport, "transport must not be null");

        return transports.putIfAbsent(key, transport) == null;
    }

    /**
     * Registers or replaces a transport for the given key.
     *
     * <p>
     * If a transport already exists for the same key, it will be overwritten.
     * Lifecycle cleanup of the replaced transport is the caller's responsibility.
     * </p>
     *
     * @param key unique transport key
     * @param transport transport instance
     * @throws NullPointerException if key or transport is null
     */
    public void register(String key, Transport transport) {
        Objects.requireNonNull(key, "key must not be null");
        Objects.requireNonNull(transport, "transport must not be null");

        transports.put(key, transport);
    }

    /**
     * Unregisters a transport and shuts it down if present.
     *
     * <p>
     * This method ensures proper lifecycle cleanup by invoking
     * {@link Transport#shutdown()} on the removed transport.
     * </p>
     *
     * @param key transport key
     * @return the removed transport, or {@code null} if none existed
     */
    public Transport unregister(String key) {
        Transport removed = transports.remove(key);
        if (removed != null) {
            removed.shutdown(); // IMPORTANT: lifecycle cleanup
        }
        return removed;
    }

    /**
     * Retrieves a transport by its key.
     *
     * @param key transport key
     * @return the transport instance, or {@code null} if not found
     */
    public Transport get(String key) {
        return transports.get(key);
    }

    /**
     * Checks whether a transport exists for the given key.
     *
     * @param key transport key
     * @return {@code true} if transport exists, otherwise {@code false}
     */
    public boolean contains(String key) {
        return transports.containsKey(key);
    }

    /**
     * Shuts down all registered transports and clears the registry.
     *
     * <p>
     * This method should be called during application shutdown
     * to ensure all underlying resources are released properly.
     * </p>
     */
    public void destroy() {
        transports.values().forEach(Transport::shutdown);
        transports.clear();
    }

    /**
     * Resolves a transport based on channel configuration and outbound socket type.
     *
     * <p>
     * Lookup key is constructed as:
     * </p>
     * <pre>
     *     outboundType.name() + "|" + channelCfg.name()
     * </pre>
     *
     * @param channelCfg channel configuration
     * @param outboundType socket outbound type
     * @return resolved transport
     * @throws NullPointerException if outboundType is null
     * @throws NullPointerException if no transport is found for the given parameters
     */
    public Transport resolve(ChannelCfg channelCfg, SocketType outboundType) {
        Objects.requireNonNull(outboundType, "Outbound Type is null");

        Transport transport =
                transports.get(outboundType.name() + "|" + channelCfg.name());

        Objects.requireNonNull(
                transport,
                "No transport for channelCfg " + channelCfg
        );
        return transport;
    }
}