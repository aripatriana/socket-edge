package com.socket.edge.core.socket;

import com.socket.edge.core.ForwardService;
import com.socket.edge.core.TelemetryRegistry;
import com.socket.edge.core.transport.TransportRegister;
import com.socket.edge.model.ChannelCfg;
import com.socket.edge.model.SocketEndpoint;
import com.socket.edge.utils.CommonUtil;
import com.socket.edge.utils.IsoParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

public class SocketManager {

    private static final Logger log = LoggerFactory.getLogger(SocketManager.class);

    private final Map<String, AbstractSocket> sockets = new ConcurrentHashMap<>();
    private TelemetryRegistry telemetryRegistry;
    private IsoParser isoParser;
    private ForwardService forwardService;
    private TransportRegister transportRegister;

    public SocketManager(TransportRegister transportRegister, TelemetryRegistry telemetryRegistry, IsoParser isoParser, ForwardService forwardService) {
        this.transportRegister = transportRegister;
        this.telemetryRegistry = telemetryRegistry;
        this.isoParser = isoParser;
        this.forwardService = forwardService;
    }

    public List<AbstractSocket> createSocket(ChannelCfg cfg) {
        Objects.requireNonNull(cfg, "ChannelCfg required");

        List<AbstractSocket> list = new ArrayList<>();
        list.add(createServerSocket(cfg));
        list.addAll(createClientSockets(cfg));
        return list;
    }

    private AbstractSocket createServerSocket(ChannelCfg cfg) {
        if (cfg.server() == null) {
            return null;
        }

        NettyServerSocket serverSocket = new NettyServerSocket(
                cfg.name(),
                cfg.server().listenPort(),
                cfg.server().pool(),
                telemetryRegistry,
                isoParser,
                forwardService
        );

        AbstractSocket existing = sockets.putIfAbsent(serverSocket.getId(), serverSocket);
        if (existing == null) {
            transportRegister.registerServerTransport(cfg, serverSocket);
            log.info("Server socket registered id={}", serverSocket.getId());
        } else {
            log.warn("Server socket already exists id={}", serverSocket.getId());
        }

        return serverSocket;
    }

    public <T extends  AbstractSocket> List<T> createClientSockets(ChannelCfg cfg) {
        if (cfg.client() == null || cfg.client().endpoints().isEmpty()) {
            return List.of();
        }

        List<NettyClientSocket> registered = new ArrayList<>();

        for (SocketEndpoint se : cfg.client().endpoints()) {
            NettyClientSocket clientSocket = new NettyClientSocket(
                    cfg.name(),
                    se,
                    telemetryRegistry,
                    isoParser,
                    forwardService
            );

            AbstractSocket existing = sockets.putIfAbsent(clientSocket.getId(), clientSocket);
            if (existing == null) {
                registered.add(clientSocket);
                log.info("Client socket registered id={}", clientSocket.getId());
            } else {
                log.warn("Client socket already exists id={}", clientSocket.getId());
            }
        }

        if (!registered.isEmpty()) {
            transportRegister.registerClientTransport(cfg, registered);
        }

        return (List<T>) registered;
    }

    public AbstractSocket createClientSockets(ChannelCfg cfg, SocketEndpoint se) {
        if (cfg.client() == null || cfg.client().endpoints().isEmpty() || se == null) {
            return null;
        }

        NettyClientSocket clientSocket = new NettyClientSocket(
                cfg.name(),
                se,
                telemetryRegistry,
                isoParser,
                forwardService
        );

        AbstractSocket existing = sockets.putIfAbsent(clientSocket.getId(), clientSocket);
        if (existing == null) {
            transportRegister.registerClientTransport(cfg, clientSocket);
            log.info("Client socket registered id={}", clientSocket.getId());
        } else {
            log.warn("Client socket already exists id={}", clientSocket.getId());
        }
        return clientSocket;
    }

    public void destroyServerSocket(ChannelCfg cfg, SocketEndpoint se) {
        Objects.requireNonNull(cfg, "ChannelCfg required");
        Objects.requireNonNull(se, "SocketEndpoint required");

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
                log.info("Destroyed client socket id={}", id);
                transportRegister.unregisterClientTransport(cfg, (NettyClientSocket) socket);
            }
        }
    }

    public void destroySocket(ChannelCfg cfg) {
        Objects.requireNonNull(cfg, "ChannelCfg required");

        if (cfg.server() != null) {
            String id = CommonUtil.serverId(cfg.name(), cfg.server().listenPort());
            removeAndShutdown(id);
            transportRegister.unregisterServerTransport(cfg);
        }

        if (cfg.client() != null) {
            cfg.client().endpoints().forEach(se -> {
                String id = CommonUtil.clientId(cfg.name(), se.host(), se.port());
                removeAndShutdown(id);
            });
            transportRegister.unregisterClientTransport(cfg);
        }
    }

    public AbstractSocket getSocket(String id) {
        return sockets.get(id);
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
        log.info("Start socket by id {}", id);
        Objects.requireNonNull(id, "Required id");
        AbstractSocket socket = sockets.get(id);
        start(socket);
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

    public void stopById(String id) throws InterruptedException {
        Objects.requireNonNull(id, "Required name");
        AbstractSocket socket = sockets.get(id);
        stop(socket);
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
        startAll(List.copyOf(sockets.values()));
    }

    public void startAll(List<AbstractSocket> list) {
        list.forEach(s -> {
            try {
                s.start();
            } catch (InterruptedException e) {
                log.error("Start failed id={}", s.getId(), e);
                throw new RuntimeException(e);
            }
        });
    }

    public void stopAll(List<AbstractSocket> list) {
        list.forEach(s -> {
            try {
                s.stop();
            } catch (InterruptedException e) {
                log.error("Stop failed id={}", s.getId(), e);
                throw new RuntimeException(e);
            }
        });
    }

    public void stopAll() {
        stopAll((List<AbstractSocket>) sockets.values());
    }

    public void destroyAll() {
        for (AbstractSocket as : sockets.values()) {
            try {
                as.shutdown();
            } catch (Exception ignored) {
                log.error("Shutdown failed id={}", as.getId(), ignored);
            }
        }
        sockets.clear();
    }

    public void startByName(String name) {
        Objects.requireNonNull(name, "Required name");
        List<AbstractSocket> socket = sockets.values()
                .stream()
                .filter(s -> name.equals(s.getName()))
                .toList();
        Objects.requireNonNull(socket, "Object socket null");
        socket.forEach(s -> {
            try {
                s.start();
            } catch (InterruptedException e) {
                log.error("Start socket failed id={}", s.getId(), e);
                throw new RuntimeException(e);
            }
        });
    }

    public void stopByName(String name) {
        Objects.requireNonNull(name, "Required name");
        List<AbstractSocket> socket = sockets.values()
                .stream()
                .filter(s -> name.equals(s.getName()))
                .toList();
        Objects.requireNonNull(socket, "Object socket null");
        socket.forEach(s -> {
            try {
                s.stop();
            } catch (InterruptedException e) {
                log.error("Stop socket failed id={}", s.getId(), e);
                throw new RuntimeException(e);
            }
        });
    }

    private AbstractSocket removeAndShutdown(String id) {
        AbstractSocket socket = sockets.remove(id);
        if (socket != null) {
            try {
                socket.shutdown();
            } catch (Exception e) {
                log.error("Shutdown failed id={}", id, e);
                throw new RuntimeException(e);
            }
        }
        return socket;
    }
}
