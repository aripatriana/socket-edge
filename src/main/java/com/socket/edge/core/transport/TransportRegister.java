package com.socket.edge.core.transport;

import com.socket.edge.core.socket.NettyClientSocket;
import com.socket.edge.core.socket.NettyServerSocket;
import com.socket.edge.core.socket.SocketChannel;
import com.socket.edge.core.strategy.SelectionFactory;
import com.socket.edge.core.strategy.SelectionStrategy;
import com.socket.edge.model.ChannelCfg;
import com.socket.edge.constant.SocketType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Objects;

/**
 * Responsible for registering/unregistering transports (server/client) into the TransportProvider.
 * Each transport is keyed by a combination of SocketType and channel name.
 */
public class TransportRegister {

    private static final Logger log = LoggerFactory.getLogger(TransportRegister.class);

    private final TransportProvider transportProvider;

    /**
     * Create a TransportRegister backed by the given TransportProvider.
     *
     * @param transportProvider provider used to register/unregister transports (must not be null)
     */
    public TransportRegister(TransportProvider transportProvider) {
        this.transportProvider = transportProvider;
    }

    /**
     * Register a server transport for the given channel configuration.
     *
     * Behavior:
     * - Validates that cfg and socket are not null.
     * - Creates a SelectionStrategy from the channel client strategy.
     * - Registers a ServerTransport keyed by the socket's type and channel name.
     *
     * @param cfg server channel configuration (not null)
     * @param socket server socket to expose (not null)
     */
    public void registerServerTransport(ChannelCfg cfg, NettyServerSocket socket) {
        Objects.requireNonNull(cfg, "cfg must not be null");
        Objects.requireNonNull(socket, "socket must not be null");

        String key = key(socket.getType(), cfg.name());
        SelectionStrategy<SocketChannel> strategy =
                SelectionFactory.create(cfg.client().strategy());

        boolean registered = transportProvider.registerIfAbsent(
                key,
                new ServerTransport(socket, strategy)
        );

        if (!registered) {
            throw new IllegalStateException(
                    "Server transport already registered for key=" + key
            );
        }
    }

    /**
     * Unregister the server transport associated with the given channel configuration.
     *
     * Behavior:
     * - Removes the transport entry for SocketType.SOCKET_SERVER and the channel name.
     *
     * @param cfg channel configuration whose server transport should be removed (may be null -> NPE)
     */
    public void unregisterServerTransport(ChannelCfg cfg) {
        transportProvider.unregister(key(SocketType.SOCKET_SERVER, cfg.name()));
    }

    /**
     * Register a client transport for the given channel configuration and client sockets.
     *
     * Behavior:
     * - Validates cfg and clientSockets (not null) and that the list is not empty.
     * - Uses the first client's SocketType as the transport key.
     * - Creates a selection strategy from the channel client strategy and registers ClientTransport.
     *
     * Exceptions:
     * - Throws IllegalArgumentException when clientSockets is empty.
     *
     * @param cfg channel configuration for client transport (not null)
     * @param clientSockets list of client sockets to be used by the transport (not null, not empty)
     */
    public void registerClientTransport(ChannelCfg cfg, List<NettyClientSocket> clientSockets) {
        Objects.requireNonNull(cfg, "cfg must not be null");
        Objects.requireNonNull(clientSockets, "clientSockets must not be null");
        if (clientSockets.isEmpty()) {
            throw new IllegalArgumentException("clientSockets must not be empty");
        }

        String key = key(clientSockets.get(0).getType(), cfg.name());
        SelectionStrategy<SocketChannel> strategy =
                SelectionFactory.create(cfg.client().strategy());

        boolean registered = transportProvider.registerIfAbsent(
                key,
                new ClientTransport(clientSockets, strategy)
        );

        if (!registered) {
            throw new IllegalStateException(
                    "Client transport already registered for key=" + key
            );
        }
    }

    /**
     * Unregister the client transport associated with the given channel configuration.
     *
     * Behavior:
     * - Removes the transport entry for SocketType.SOCKET_CLIENT and the channel name.
     *
     * @param cfg channel configuration whose client transport should be removed (may be null -> NPE)
     */
    public void unregisterClientTransport(ChannelCfg cfg) {
        transportProvider.unregister(key(SocketType.SOCKET_CLIENT, cfg.name()));
    }

    /**
     * Add a NettyClientSocket to an existing ClientTransport for the specified channel name.
     *
     * Behavior:
     * - Looks up the transport by combining the client's SocketType and the channelName.
     * - If the found object is a ClientTransport, invokes addSocket; otherwise logs a warning.
     *
     * @param cfg name of the channel to which the socket should be added (not null)
     * @param clientSocket socket instance to add (not null)
     */
    public void registerClientTransport(ChannelCfg cfg, NettyClientSocket clientSocket) {
        String key = key(clientSocket.getType(), cfg.name());
        Object t = transportProvider.get(key);
        if (t instanceof ClientTransport) {
            ((ClientTransport) t).addSocket(clientSocket);
        } else {
            log.warn("No ClientTransport found for key {} or type mismatch", key(clientSocket.getType(), cfg.name()));
        }
    }

    /**
     * Remove a NettyClientSocket from an existing ClientTransport for the specified channel name.
     *
     * Behavior:
     * - Looks up the transport by combining the client's SocketType and the channelName.
     * - If the found object is a ClientTransport, invokes removeSocket; otherwise logs a warning.
     *
     * @param cfg name of the channel from which the socket should be removed (not null)
     * @param clientSocket socket instance to remove (not null)
     */
    public void unregisterClientTransport(ChannelCfg cfg, NettyClientSocket clientSocket) {
        String key = key(clientSocket.getType(), cfg.name());
        Object t = transportProvider.get(key);
        if (t instanceof ClientTransport) {
            ((ClientTransport) t).removeSocket(clientSocket);
        } else {
            log.warn("No ClientTransport found for key {} or type mismatch", key(clientSocket.getType(), cfg.name()));
        }
    }

    /**
     * Destroy all transports and cleanup resources in the TransportProvider.
     *
     * Behavior:
     * - Delegates to transportProvider.destroy().
     */
    public void destroy() {
        transportProvider.destroy();
    }

    /**
     * Compose the internal map key used to store transports.
     *
     * Format: <SocketType.name()>|<channelName>
     *
     * @param type socket type used as first portion of the key (not null)
     * @param channelName channel name used as second portion of the key (may be null)
     * @return composed key string
     */
    private String key(SocketType type, String channelName) {
        return type.name() + "|" + channelName;
    }
}
