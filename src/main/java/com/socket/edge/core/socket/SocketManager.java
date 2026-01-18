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

    public void createSocket(ChannelCfg cfg) throws InterruptedException {
        // ===== SERVER =====
        if (cfg.server() != null) {
            NettyServerSocket serverSocket =
                    new NettyServerSocket(
                            cfg.name(),
                            cfg.server().listenPort(),
                            cfg.server().pool(),
                            telemetryRegistry,
                            isoParser,
                            forwardService
                    );

            if (!sockets.containsKey(serverSocket.getId())) {
                sockets.put(serverSocket.getId(), serverSocket);
                transportRegister.registerServerTransport(cfg, serverSocket);

                serverSocket.start();
            } else {
                log.warn("Socket has already exists with id {}");
            }
        }

        // ===== CLIENT =====
        if (cfg.client() != null) {

            List<NettyClientSocket> clientSockets = new ArrayList<>();

            for (SocketEndpoint se : cfg.client().endpoints()) {
                NettyClientSocket clientSocket =
                        new NettyClientSocket(
                                cfg.name(),
                                se,
                                telemetryRegistry,
                                isoParser,
                                forwardService
                        );

                if (!sockets.containsKey(clientSocket.getId())) {
                    sockets.put(clientSocket.getId(), clientSocket);
                    clientSockets.add(clientSocket);
                } else {
                    log.warn("Socket has already exists with id {}");
                }
            }

            transportRegister.registerClientTransport(cfg, clientSockets);
            clientSockets.forEach(c -> {
                c.start();
            });
        }
    }

    public void destroySocket(ChannelCfg cfg) throws InterruptedException {
        AbstractSocket serverSocket = sockets.remove(CommonUtil.serverId(cfg.name(), cfg.server().listenPort()));
        if (serverSocket != null) {
            serverSocket.stop();
        }
        transportRegister.unregisterServerTransport(cfg);

        cfg.client().endpoints().forEach(se -> {
            AbstractSocket clientSocket = sockets.remove( CommonUtil.clientId(cfg.name(), se.host(), se.port()));
            if (clientSocket != null) {
                try {
                    clientSocket.stop();
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
        });
        transportRegister.unregisterClientTransport(cfg);
    }

    public AbstractSocket getSocket(String id) {
        return sockets.get(id);
    }

    public void start(String id) throws InterruptedException {
        log.info("Start socket by id {}", id);
        Objects.requireNonNull(id, "Required id");
        AbstractSocket socket = sockets.get(id);
        Objects.requireNonNull(socket, "Object socket null");
        try {
            socket.start();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    public void stop(String id) throws InterruptedException {
        Objects.requireNonNull(id, "Required name");
        AbstractSocket socket = sockets.get(id);
        Objects.requireNonNull(socket, "Object socket null");
        try {
            socket.stop();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    public void restart(String id) throws InterruptedException {
        stop(id);
        Thread.sleep(1000);
        start(id);
    }

    public void restartByName(String name) throws InterruptedException {
        stopByName(name);
        Thread.sleep(1000);
        startByName(name);
    }

    public void restartAll() throws InterruptedException {
        stopAll();
        Thread.sleep(1000);
        startAll();
    }

    public void startAll() {
        sockets.values().forEach(s -> {
            try {
                s.start();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        });
    }

    public void stopAll() {
        sockets.values().forEach(s -> {
            try {
                s.stop();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        });
    }

    public void destroyAll() {
        for (AbstractSocket as : sockets.values()) {
            try {
                as.shutdown();
            } catch (Exception ignored) {
                log.error("{}", ignored.getCause());
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
                throw new RuntimeException(e);
            }
        });
    }
}
