package com.socket.edge.core.socket;

import com.socket.edge.constant.SocketType;
import com.socket.edge.core.transport.TransportRegister;
import com.socket.edge.model.ChannelCfg;
import com.socket.edge.model.SocketEndpoint;
import com.socket.edge.utils.CommonUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages socket registration, lifecycle, and channel group coordination.
 *
 * <p>v3.0 changes:
 * <ul>
 *   <li>No longer has circular dependency with {@link SocketFactory}</li>
 *   <li>Uses {@link ChannelGroupRegistry} for explicit server↔client tracking</li>
 *   <li>New {@code createChannelGroup()} method creates and registers all sockets as a unit</li>
 *   <li>Removed Thread.sleep() from restart operations</li>
 * </ul>
 *
 * @author Ari Patriana
 * @since 3.0.0
 */
public class SocketManager {

    private static final Logger log = LoggerFactory.getLogger(SocketManager.class);

    private final Map<String, AbstractSocket> sockets = new ConcurrentHashMap<>();
    private final SocketFactory socketFactory;
    private final TransportRegister transportRegister;
    private final ChannelGroupRegistry groupRegistry;

    public SocketManager(
            SocketFactory socketFactory,
            TransportRegister transportRegister,
            ChannelGroupRegistry groupRegistry
    ) {
        this.socketFactory = socketFactory;
        this.transportRegister = transportRegister;
        this.groupRegistry = groupRegistry;
    }

    /**
     * Creates all sockets for a channel config and registers
     * them as a {@link ChannelGroup}.
     */
    public ChannelGroup createChannelGroup(ChannelCfg cfg) {
        Objects.requireNonNull(cfg, "ChannelCfg required");

        ChannelGroup group = new ChannelGroup(cfg.name(), cfg);

        // Create server socket
        if (cfg.server() != null) {
            AbstractSocket server = socketFactory.createServer(cfg);
            sockets.put(server.getId(), server);
            group.setServer(server);
            groupRegistry.mapSocket(server.getId(), group);
            transportRegister.registerServerTransport(cfg, server);
            log.debug("Server socket registered id={}", server.getId());
        }

        // Create client sockets
        if (cfg.client() != null && !cfg.client().endpoints().isEmpty()) {
            List<AbstractSocket> clients = new ArrayList<>();
            for (SocketEndpoint se : cfg.client().endpoints()) {
                AbstractSocket client = socketFactory.createClient(cfg, se);
                sockets.put(client.getId(), client);
                group.addClient(client);
                groupRegistry.mapSocket(client.getId(), group);
                clients.add(client);
                log.debug("Client socket registered id={}", client.getId());
            }
            if (!clients.isEmpty()) {
                transportRegister.registerClientTransport(cfg, clients);
            }
        }

        groupRegistry.register(group);
        return group;
    }

    /**
     * Creates and registers a single client socket into an existing channel group.
     */
    public AbstractSocket createClientSocket(ChannelCfg cfg, SocketEndpoint se) {
        if (cfg.client() == null || se == null) return null;

        AbstractSocket client = socketFactory.createClient(cfg, se);
        AbstractSocket existing = sockets.putIfAbsent(client.getId(), client);
        if (existing != null) {
            log.warn("Client socket already exists id={}", client.getId());
            return existing;
        }

        ChannelGroup group = groupRegistry.byName(cfg.name());
        if (group != null) {
            group.addClient(client);
            groupRegistry.mapSocket(client.getId(), group);
        }
        transportRegister.registerClientTransport(cfg, client);
        log.debug("Client socket registered id={}", client.getId());
        return client;
    }

    public Collection<AbstractSocket> getSockets() {
        return sockets.values();
    }

    public AbstractSocket getSocket(String id) {
        return sockets.get(id);
    }

    public void start(AbstractSocket socket) {
        Objects.requireNonNull(socket, "Socket must not be null");
        try {
            socket.start();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Start failed id={}", socket.getId(), e);
            throw new RuntimeException(e);
        }
    }

    public void startById(String id) {
        start(requireSocket(id));
    }

    public void stop(AbstractSocket socket) {
        Objects.requireNonNull(socket, "Socket must not be null");
        try {
            socket.stop();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Stop failed id={}", socket.getId(), e);
            throw new RuntimeException(e);
        }
    }

    public void stopById(String id) {
        stop(requireSocket(id));
    }

    public void restart(AbstractSocket socket) {
        Objects.requireNonNull(socket, "Socket must not be null");
        try {
            socket.restart();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Restart failed id={}", socket.getId(), e);
            throw new RuntimeException(e);
        }
    }

    public void restartById(String id) {
        restart(requireSocket(id));
    }

    public void startAll() {
        sockets.values().forEach(this::start);
    }

    public void startAll(List<AbstractSocket> list) {
        list.forEach(this::start);
    }

    public void stopAll() {
        sockets.values().forEach(this::stop);
    }

    public void restartAll() {
        sockets.values().forEach(this::restart);
    }

    public void startByName(String name) {
        Objects.requireNonNull(name, "Required name");
        ChannelGroup group = groupRegistry.byName(name);
        if (group == null) {
            throw new IllegalArgumentException("No channel group found with name: " + name);
        }
        if (group.server() != null) start(group.server());
        group.clients().forEach(this::start);
    }

    public void stopByName(String name) {
        Objects.requireNonNull(name, "Required name");
        ChannelGroup group = groupRegistry.byName(name);
        if (group == null) {
            throw new IllegalArgumentException("No channel group found with name: " + name);
        }
        if (group.server() != null) stop(group.server());
        group.clients().forEach(this::stop);
    }

    public void restartByName(String name) {
        Objects.requireNonNull(name, "Required name");
        ChannelGroup group = groupRegistry.byName(name);
        if (group == null) {
            throw new IllegalArgumentException("No channel group found with name: " + name);
        }
        if (group.server() != null) restart(group.server());
        group.clients().forEach(this::restart);
    }

    public void destroySocket(ChannelCfg cfg) {
        Objects.requireNonNull(cfg, "ChannelCfg required");

        if (cfg.server() != null) {
            destroyServerSocket(cfg);
        }
        if (cfg.client() != null) {
            cfg.client().endpoints().forEach(se -> destroyClientSocket(cfg, se));
            transportRegister.unregisterClientTransport(cfg);
        }

        groupRegistry.unregister(cfg.name());
    }

    public void destroyServerSocket(ChannelCfg cfg) {
        if (cfg.server() != null) {
            String id = CommonUtil.serverId(cfg.name(), cfg.server().listenPort());
            AbstractSocket socket = removeAndShutdown(id);
            if (socket != null) {
                transportRegister.unregisterServerTransport(cfg);
            }
        }
    }

    public void destroyClientSocket(ChannelCfg cfg, SocketEndpoint se) {
        Objects.requireNonNull(se, "SocketEndpoint required");
        if (cfg.client() != null) {
            String id = CommonUtil.clientId(cfg.name(), se.host(), se.port());
            AbstractSocket socket = removeAndShutdown(id);
            if (socket != null) {
                ChannelGroup group = groupRegistry.byName(cfg.name());
                if (group != null) {
                    group.removeClient(socket);
                }
                groupRegistry.unmapSocket(id);
                transportRegister.unregisterClientTransport(cfg, socket);
                log.debug("Destroyed client socket id={}", id);
            }
        }
    }

    public void destroyAll() {
        sockets.values().forEach(s -> {
            try {
                s.shutdown();
            } catch (Exception e) {
                log.error("Shutdown failed id={}", s.getId(), e);
            }
        });
        sockets.clear();
    }

    private AbstractSocket removeAndShutdown(String id) {
        AbstractSocket socket = sockets.remove(id);
        if (socket != null) {
            groupRegistry.unmapSocket(id);
            try {
                socket.shutdown();
            } catch (Exception e) {
                log.error("Shutdown failed id={}", id, e);
                throw new RuntimeException(e);
            }
        }
        return socket;
    }

    AbstractSocket requireSocket(String id) {
        AbstractSocket socket = sockets.get(id);
        if (socket == null) {
            throw new IllegalArgumentException("No socket found with id: " + id);
        }
        return socket;
    }
}
