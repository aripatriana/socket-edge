package com.socket.edge;

import com.socket.edge.core.ChannelCfgProcessor;
import com.socket.edge.core.ChannelCfgSelector;
import com.socket.edge.core.ForwardService;
import com.socket.edge.core.VirtualThreadPoolFactory;
import com.socket.edge.core.iso.Iso8583ProfileResolver;
import com.socket.edge.core.cache.CorrelationStore;
import com.socket.edge.core.cache.CacheCorrelationStore;
import com.socket.edge.core.engine.SEEngine;
import com.socket.edge.core.socket.AbstractSocket;
import com.socket.edge.core.socket.NettyClientSocket;
import com.socket.edge.core.socket.NettyServerSocket;
import com.socket.edge.core.socket.SocketChannel;
import com.socket.edge.core.strategy.SelectionFactory;
import com.socket.edge.core.strategy.SelectionStrategy;
import com.socket.edge.core.transport.ClientTransport;
import com.socket.edge.core.transport.ServerTransport;
import com.socket.edge.core.transport.TransportProvider;
import com.socket.edge.http.service.admin.AdminHttpService;
import com.socket.edge.http.service.admin.HttpServiceHandler;
import com.socket.edge.http.service.admin.ReloadConfigHandler;
import com.socket.edge.http.service.admin.ValidateConfigHandler;
import com.socket.edge.http.service.socket.MetricsService;
import com.socket.edge.http.service.socket.MetricsServiceHandle;
import com.socket.edge.http.service.socket.SocketStatusHandler;
import com.socket.edge.http.service.socket.SocketStatusService;
import com.socket.edge.model.ChannelCfg;
import com.socket.edge.model.SocketEndpoint;
import com.socket.edge.model.SocketType;
import com.socket.edge.model.Metadata;
import com.socket.edge.http.NettyHttpServer;
import com.socket.edge.utils.ConfigUtil;
import com.socket.edge.utils.IsoParser;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.apache.camel.CamelContext;
import org.apache.camel.impl.DefaultCamelContext;
import org.jpos.iso.ISOException;
import org.jpos.iso.ISOPackager;
import org.jpos.iso.packager.GenericPackager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class SystemBootstrap {

    private static final Logger log = LoggerFactory.getLogger(SystemBootstrap.class);

    private Iso8583ProfileResolver profileProcessor;
    private ChannelCfgSelector channelCfgSelector;
    private TransportProvider transportProvider;
    private CorrelationStore correlationStore;
    private ISOPackager packager;
    private IsoParser parser;
    private ChannelCfgProcessor channelCfgProcessor;
    private CamelContext camelContext;
    private Metadata metadata;
    private NettyHttpServer httpServer;
    private final Map<String, AbstractSocket> sockets = new ConcurrentHashMap<>();
    public static Config sc;
    private ConfigUtil cu = new ConfigUtil();
    private MetricsService metricsService;

    static {
        Path configPath = Path.of(
                System.getProperty("base.dir"),
                "conf",
                "system.conf"
        );

        if (!Files.exists(configPath)) {
            throw new IllegalStateException(
                    "Config file not found: " + configPath.toAbsolutePath()
            );
        }

        sc = ConfigFactory.parseFile(configPath.toFile()).resolve();
    }

    public SystemBootstrap(String[] args) {

    }

    public void initialize() {
        log.info("System initializing..");
        profileProcessor = new Iso8583ProfileResolver();
        transportProvider = new TransportProvider();
        correlationStore = new CacheCorrelationStore(cu.getInt("engine.cache.ttl", 30000));
        channelCfgProcessor = new ChannelCfgProcessor();
        channelCfgSelector = new ChannelCfgSelector();
        metricsService = new MetricsService(new SimpleMeterRegistry());
    }

    public void loadConfiguration() throws IOException {
        log.info("Load configuration..");

        Path packagerPath = Path.of(System.getProperty("base.dir"),sc.getString("message.packager.path"));
        try (InputStream is = Files.newInputStream(packagerPath)) {
            packager = new GenericPackager(is);
        } catch (ISOException | IOException e) {
            throw new RuntimeException(e);
        }
        parser = new IsoParser(packager);
        metadata = channelCfgProcessor.process(Path.of(System.getProperty("base.dir"),"conf", "channel.conf"));
    }

    public void handleRouterEngine() throws Exception {
        log.info("Setup router engine..");
        camelContext = new DefaultCamelContext();
        camelContext.getExecutorServiceManager()
                .setThreadPoolFactory(new VirtualThreadPoolFactory());

        SEEngine SEEngine = new SEEngine(
                metadata,
                profileProcessor,
                channelCfgSelector,
                correlationStore,
                transportProvider
        );

        camelContext.addRoutes(SEEngine);
        camelContext.start();
    }

    public void handleSocketConfiguration() throws InterruptedException {
        log.info("Socket initializing..");

        ForwardService forward = new ForwardService(camelContext.createProducerTemplate());
        for (ChannelCfg cfg : metadata.channelCfgs()) {

            // ===== SERVER =====
            if (cfg.server() != null) {
                NettyServerSocket serverSocket =
                        new NettyServerSocket(
                                cfg.name(),
                                cfg.server().listenPort(),
                                cfg.server().pool(),
                                metricsService,
                                parser,
                                forward
                        );


                serverSocket.start();
                sockets.put(serverSocket.getId(), serverSocket);

                SelectionStrategy<SocketChannel> strategy =
                        SelectionFactory.create(cfg.client().strategy());

                transportProvider.register(SocketType.SOCKET_SERVER.name()+ "|" + cfg.name(),
                        new ServerTransport(serverSocket, strategy)
                );
            }

            // ===== CLIENT =====
            if (cfg.client() != null) {

                List<NettyClientSocket> clientSockets = new ArrayList<>();

                for (SocketEndpoint se : cfg.client().endpoints()) {
                    NettyClientSocket clientSocket =
                            new NettyClientSocket(
                                    cfg.name(),
                                    se,
                                    metricsService,
                                    parser,
                                    forward
                            );

                    clientSocket.start();
                    sockets.put(clientSocket.getId(), clientSocket);

                    clientSockets.add(clientSocket);
                }

                SelectionStrategy<SocketChannel> strategy =
                        SelectionFactory.create(cfg.client().strategy());

                transportProvider.register(
                        SocketType.SOCKET_CLIENT.name() + "|" + cfg.name(),
                        new ClientTransport(clientSockets, strategy)
                );
            }
        }
    }

    public void handleHttpServer() throws InterruptedException {
        log.info("Start httpserver..");
        AdminHttpService adminHttpService = new AdminHttpService();
        SocketStatusService socketStatusService = new SocketStatusService(sockets.values()
                .stream()
                .toList());
        List<HttpServiceHandler> services = List.of(
                new SocketStatusHandler(socketStatusService),
                new ValidateConfigHandler(adminHttpService),
                new ReloadConfigHandler(adminHttpService),
                new MetricsServiceHandle((metricsService))
        );

        httpServer = new NettyHttpServer(
                        sc.getString("server.name"),
                        sc.getInt("server.port"),
                        services);

        httpServer.start();
    }

    public void handleLifecycle() {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log.info("Shutdown signal received...");
            long start = System.currentTimeMillis();
            try {
                httpServer.stop();
            } catch (Exception e) {
                log.error("{}", e.getCause());
            }

            try {
                camelContext.stop();
            } catch (Exception e) {
                log.error("{}", e.getCause());
            }

            for (AutoCloseable c : sockets.values()) {
                try {
                    c.close();
                } catch (Exception ignored) {
                    log.error("{}", ignored.getCause());
                }
            }

            log.info("Gracefully shutdown took {}ms", (System.currentTimeMillis() - start));
        }));
    }

    public static void main(String[] args) throws Exception {
        log.info("Starting..");
        long start = System.currentTimeMillis();
        SystemBootstrap bootstrap = new SystemBootstrap(args);
        bootstrap.initialize();
        bootstrap.loadConfiguration();
        bootstrap.handleRouterEngine();
        bootstrap.handleSocketConfiguration();
        bootstrap.handleHttpServer();
        bootstrap.handleLifecycle();
        log.info("Started took {}ms", (System.currentTimeMillis() - start));
    }

}
