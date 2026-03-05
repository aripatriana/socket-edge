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
import java.util.concurrent.CopyOnWriteArrayList;

public class SocketManager {

    private static final Logger log = LoggerFactory.getLogger(SocketManager.class);

    private final Map<String, AbstractSocket> sockets = new ConcurrentHashMap<>();
    private final Map<String, AbstractSocket> socketServers = new ConcurrentHashMap<>();
    private final Map<String, List<AbstractSocket>> socketClients = new ConcurrentHashMap<>();
    private final SocketFactory socketFactory;
    private final TransportRegister transportRegister;

    public SocketManager(SocketFactory socketFactory,
                         TransportRegister transportRegister) {
        this.socketFactory = socketFactory;
        this.transportRegister = transportRegister;
    }

    public List<AbstractSocket> createSocket(ChannelCfg cfg) {
        Objects.requireNonNull(cfg, "ChannelCfg required");

        List<AbstractSocket> list = new ArrayList<>();

        AbstractSocket server = createServerSocket(cfg);
        if (server != null) list.add(server);

        list.addAll(createClientSockets(cfg));
        return list;
    }

    private AbstractSocket createServerSocket(ChannelCfg cfg) {
        if (cfg.server() == null) {
            return null;
        }

        AbstractSocket server = socketFactory.createServer(cfg);
        AbstractSocket existing = sockets.putIfAbsent(server.getId(), server);
        if (existing == null) {
            socketServers.putIfAbsent(cfg.name(), server);
            transportRegister.registerServerTransport(cfg, server);
            log.debug("Server socket registered id={}", server.getId());
        } else {
            log.warn("Server socket already exists id={}", server.getId());
        }

        return server;
    }

    public List<AbstractSocket>  createClientSockets(ChannelCfg cfg) {
        if (cfg.client() == null || cfg.client().endpoints().isEmpty()) {
            return List.of();
        }

        List<AbstractSocket> registered = new ArrayList<>();

        for (SocketEndpoint se : cfg.client().endpoints()) {
            AbstractSocket client = socketFactory.createClient(cfg, se);
            AbstractSocket existing = sockets.putIfAbsent(client.getId(), client);

            if (existing == null) {
                registered.add(client);
                socketClients
                        .computeIfAbsent(cfg.name(), k -> new CopyOnWriteArrayList<>())
                        .add(client);
                log.debug("Client socket registered id={}", client.getId());
            } else {
                log.warn("Client socket already exists id={}", client.getId());
            }
        }

        if (!registered.isEmpty()) {
            transportRegister.registerClientTransport(cfg, registered);
        }

        return registered;
    }

    public AbstractSocket createClientSockets(ChannelCfg cfg, SocketEndpoint se) {
        if (cfg.client() == null || cfg.client().endpoints().isEmpty() || se == null) {
            return null;
        }

        AbstractSocket client = socketFactory.createClient(cfg, se);
        AbstractSocket existing = sockets.putIfAbsent(client.getId(), client);
        if (existing == null) {
            socketClients
                    .computeIfAbsent(cfg.name(), k -> new CopyOnWriteArrayList<>())
                    .add(client);
            transportRegister.registerClientTransport(cfg, client);
            log.debug("Client socket registered id={}", client.getId());
        } else {
            log.warn("Client socket already exists id={}", client.getId());
        }

        return client;
    }

    public Collection<AbstractSocket> getSockets() {
        return sockets.values();
    }

    public AbstractSocket getSocket(String id) {
        return sockets.get(id);
    }

    public AbstractSocket getSocketServerByName(String name) {
        return socketServers.get(name);
    }

    public List<AbstractSocket> getSocketClientByName(String name) {
        return socketClients.get(name);
    }

    public void start(AbstractSocket socket) {
        Objects.requireNonNull(socket, "Object socket null");
        try {
            socket.start();
        } catch (InterruptedException e) {
            log.error("Start failed id={}", socket.getId(), e);
            throw new RuntimeException(e);
        }
    }

    public void startById(String id) throws InterruptedException {
        log.debug("Start socket by id {}", id);
        start(requireSocket(id));
    }

    public void stop(AbstractSocket socket) {
        Objects.requireNonNull(socket, "Object socket null");
        try {
            socket.stop();
        } catch (InterruptedException e) {
            log.error("Stop failed id={}", socket.getId(), e);
            throw new RuntimeException(e);
        }
    }

    public void restart(AbstractSocket socket) {
        Objects.requireNonNull(socket, "Object socket null");
        try {
            socket.restart();
        } catch (InterruptedException e) {
            log.error("Restart failed id={}", socket.getId(), e);
            throw new RuntimeException(e);
        }
    }

    public void stopById(String id) throws InterruptedException {
        log.debug("Stop socket by id {}", id);
        stop(requireSocket(id));
    }

    public void restart(String id) throws InterruptedException {
        stopById(id);
        Thread.sleep(100);
        startById(id);
    }

    public void restartByName(String name) throws InterruptedException {
        stopByName(name);
        Thread.sleep(100);
        startByName(name);
    }

    public void restartAll() throws InterruptedException {
        stopAll();
        Thread.sleep(100);
        startAll();
    }

    public void startAll() {
        sockets.values().forEach(this::start);
    }

    public void startAll(List<AbstractSocket> sockets) {
        sockets.forEach(this::start);
    }

    public void stopAll() {
        sockets.values().forEach(this::stop);
    }

    public void stopAll(List<AbstractSocket> sockets) {
        sockets.forEach(this::stop);
    }

    public void startByName(String name) {
        Objects.requireNonNull(name, "Required name");

        boolean found = false;

        List<AbstractSocket> clients = socketClients.get(name);
        if (clients != null && clients.size() > 0) {
            clients.forEach(this::start);
            found = true;
        }

        AbstractSocket server = socketServers.get(name);
        if (server != null) {
            start(server);
            found = true;
        }

        if (!found) {
            log.warn("No socket client/server found with name: {}", name);
            throw new IllegalArgumentException("No socket client/server found with name: " + name);
        }
    }

    public void stopByName(String name) {
        Objects.requireNonNull(name, "Required name");
        boolean found = false;
        List<AbstractSocket> clients = socketClients.get(name);
        if (clients != null && clients.size() > 0) {
            clients.forEach(this::stop);
            found = true;
        }

        AbstractSocket server = socketServers.get(name);
        if (server != null) {
            stop(server);
            found = true;
        }

        if (!found) {
            log.warn("No socket client/server found with name: {}", name);
            throw new IllegalArgumentException("No socket client/server found with name: " + name);
        }
    }

    public void destroyServerSocket(ChannelCfg cfg) {
        Objects.requireNonNull(cfg, "ChannelCfg required");

        if (cfg.server() != null) {
            String id = CommonUtil.serverId(cfg.name(), cfg.server().listenPort());
            removeAndShutdown(id);
            transportRegister.unregisterServerTransport(cfg);
        }
    }

    public void destroyClientSocket(ChannelCfg cfg, SocketEndpoint se) {
        Objects.requireNonNull(cfg, "ChannelCfg required");
        Objects.requireNonNull(se, "SocketEndpoint required");

        if (cfg.client() != null) {
            String id = CommonUtil.clientId(cfg.name(), se.host(), se.port());
            AbstractSocket socket = removeAndShutdown(id);
            if (socket != null) {
                log.debug("Destroyed client socket id={}", id);
                transportRegister.unregisterClientTransport(cfg, socket);
            }
        }
    }

    public void destroySocket(ChannelCfg cfg) {
        Objects.requireNonNull(cfg, "ChannelCfg required");

        if (cfg.server() != null) {
            destroyServerSocket(cfg);
        }

        if (cfg.client() != null) {
            cfg.client().endpoints().forEach(se ->
                    destroyClientSocket(cfg, se)
            );
            transportRegister.unregisterClientTransport(cfg);
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
            if (socket.getType().equals(SocketType.SERVER)) {
                socketServers.remove(socket.getName());
            } else {
                List<AbstractSocket> clients = socketClients.get(socket.getName());
                if (clients != null) {
                    clients.removeIf(c -> c.getId().equals(id));
                    if (clients.isEmpty()) {
                        socketClients.remove(socket.getName());
                    }
                }
            }

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
            log.warn("No socket client/server found with id: {}", id);
            throw new IllegalArgumentException("No socket client/server found with id: " + id);
        }
        return socket;
    }
}
