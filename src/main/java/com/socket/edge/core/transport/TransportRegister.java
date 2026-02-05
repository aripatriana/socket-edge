package com.socket.edge.core.transport;

import com.socket.edge.core.socket.AbstractSocket;
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
 * {@code TransportRegister} is responsible for registering and unregistering
 * {@link Transport} instances into a {@link TransportProvider}.
 *
 * <p>
 * This class acts as an orchestration layer that:
 * <ul>
 *   <li>Creates {@link ServerTransport} and {@link ClientTransport} instances</li>
 *   <li>Builds consistent transport keys based on socket type and channel</li>
 *   <li>Delegates transport lifecycle registration to {@link TransportProvider}</li>
 * </ul>
 * </p>
 *
 * <p>
 * This class does not manage the lifecycle of sockets or channels directly.
 * It only coordinates transport registration and composition.
 * </p>
 *
 * @author Ari Patriana
 * @since 1.0.0
 */
public class TransportRegister {

    private static final Logger log =
            LoggerFactory.getLogger(TransportRegister.class);

    /**
     * Underlying provider used to store and manage transports.
     */
    private final TransportProvider transportProvider;

    /**
     * Creates a new {@code TransportRegister}.
     *
     * @param transportProvider transport provider
     * @throws NullPointerException if transportProvider is {@code null}
     */
    public TransportRegister(TransportProvider transportProvider) {
        this.transportProvider = transportProvider;
    }

    /**
     * Registers a server-side transport for the given channel configuration.
     *
     * <p>
     * A {@link ServerTransport} will be created and registered using a key
     * composed of socket type and channel name.
     * </p>
     *
     * @param cfg channel configuration
     * @param socket server socket
     * @throws NullPointerException if cfg or socket is {@code null}
     * @throws IllegalStateException if a transport is already registered
     *                              for the same key
     */
    public void registerServerTransport(ChannelCfg cfg, AbstractSocket socket) {
        log.debug("registering server transport for channel {} socket {}", cfg.name(), socket.getId());

        Objects.requireNonNull(cfg, "cfg must not be null");
        Objects.requireNonNull(socket, "socket must not be null");

        String key = key(socket.getType(), cfg.name());
        SelectionStrategy<SocketChannel> strategy =
                SelectionFactory.create(cfg.client().strategy(), null);

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
     * Registers a client-side transport using multiple client sockets.
     *
     * <p>
     * All provided sockets will be aggregated into a single
     * {@link ClientTransport}.
     * </p>
     *
     * @param cfg channel configuration
     * @param clientSockets list of client sockets
     * @throws NullPointerException if cfg or clientSockets is {@code null}
     * @throws IllegalArgumentException if clientSockets is empty
     * @throws IllegalStateException if a transport is already registered
     *                              for the same key
     */
    public void registerClientTransport(ChannelCfg cfg, List<AbstractSocket> clientSockets) {
        log.debug("registering client transport for channel {}", cfg.name());

        Objects.requireNonNull(cfg, "cfg must not be null");
        Objects.requireNonNull(clientSockets, "clientSockets must not be null");
        if (clientSockets.isEmpty()) {
            throw new IllegalArgumentException(
                    "clientSockets must not be empty"
            );
        }

        String key = key(clientSockets.get(0).getType(), cfg.name());
        SelectionStrategy<SocketChannel> strategy =
                SelectionFactory.create(cfg.client().strategy(), null);

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
     * Adds a client socket to an existing {@link ClientTransport}.
     *
     * <p>
     * If no matching {@link ClientTransport} exists, this method
     * will log a warning and perform no action.
     * </p>
     *
     * @param cfg channel configuration
     * @param clientSocket client socket to add
     */
    public void registerClientTransport(ChannelCfg cfg, AbstractSocket clientSocket) {
        log.debug("Registering client transport for channel {} socket {}", cfg.name(), clientSocket.getId());

        String key = key(clientSocket.getType(), cfg.name());
        Object t = transportProvider.get(key);

        if (t instanceof ClientTransport) {
            ((ClientTransport) t).addSocket(clientSocket);
        } else {
            log.warn("No ClientTransport found for key {} or type mismatch", key);
        }
    }

    /**
     * Unregisters a server-side transport for the given channel.
     *
     * <p>
     * Underlying transport shutdown will be handled by
     * {@link TransportProvider#unregister(String)}.
     * </p>
     *
     * @param cfg channel configuration
     */
    public void unregisterServerTransport(ChannelCfg cfg) {
        log.debug("unregistering server transport for channel {}", cfg.name());
        transportProvider.unregister(
                key(SocketType.SERVER, cfg.name())
        );
    }

    /**
     * Unregisters a client-side transport for the given channel.
     *
     * @param cfg channel configuration
     */
    public void unregisterClientTransport(ChannelCfg cfg) {
        log.debug("Unregistering client transport for channel {}", cfg.name());
        transportProvider.unregister(
                key(SocketType.CLIENT, cfg.name())
        );
    }

    /**
     * Removes a client socket from an existing {@link ClientTransport}.
     *
     * <p>
     * If the transport does not exist or is not a {@link ClientTransport},
     * this method will log a warning and perform no action.
     * </p>
     *
     * @param cfg channel configuration
     * @param clientSocket client socket to remove
     */
    public void unregisterClientTransport(ChannelCfg cfg, AbstractSocket clientSocket) {
        log.debug("Unregistering client transport from channel {} socket {}", cfg.name(), clientSocket.getId());

        String key = key(clientSocket.getType(), cfg.name());
        Object t = transportProvider.get(key);

        if (t instanceof ClientTransport) {
            ((ClientTransport) t).removeSocket(clientSocket);
        } else {
            log.warn("No ClientTransport found for key {} or type mismatch", key);
        }
    }

    /**
     * Destroys all registered transports.
     *
     * <p>
     * This method delegates to {@link TransportProvider#destroy()}
     * and should be invoked during application shutdown.
     * </p>
     */
    public void destroy() {
        transportProvider.destroy();
    }

    /**
     * Builds a transport key using socket type and channel name.
     *
     * @param type socket type
     * @param channelName channel name
     * @return transport key
     */
    private String key(SocketType type, String channelName) {
        return type.name() + "|" + channelName;
    }
}