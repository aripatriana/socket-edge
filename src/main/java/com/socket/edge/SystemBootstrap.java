package com.socket.edge;

import com.socket.edge.core.*;
import com.socket.edge.core.cache.CacheCorrelationStore;
import com.socket.edge.core.cache.CorrelationStore;
import com.socket.edge.core.engine.SEEngine;
import com.socket.edge.core.iso.Iso8583ProfileResolver;
import com.socket.edge.core.socket.SocketManager;
import com.socket.edge.core.transport.TransportProvider;
import com.socket.edge.core.transport.TransportRegister;
import com.socket.edge.http.NettyHttpServer;
import com.socket.edge.http.handler.*;
import com.socket.edge.http.service.AdminHttpService;
import com.socket.edge.http.service.ReloadCfgService;
import com.socket.edge.model.ChannelCfg;
import com.socket.edge.model.Metadata;
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
import java.util.List;

public class SystemBootstrap {

    private static final Logger log = LoggerFactory.getLogger(SystemBootstrap.class);

    private Iso8583ProfileResolver profileProcessor;
    private ChannelCfgSelector channelCfgSelector;
    private TransportProvider transportProvider;
    private TransportRegister transportRegister;
    private CorrelationStore correlationStore;
    private SocketManager socketManager;
    private ISOPackager packager;
    private IsoParser parser;
    private ChannelCfgProcessor channelCfgProcessor;
    private CamelContext camelContext;
    private Metadata metadata;
    private NettyHttpServer httpServer;
    public static Config sc;
    private final ConfigUtil cu = new ConfigUtil();
    private TelemetryRegistry telemetryRegistry;
    private SEEngine SEEngine;

    public SystemBootstrap(String[] args) {

    }

    public void loadSystemConfiguration() {
        log.info("Load system configuration..");
        String baseDir = System.getProperty("base.dir");
        if (baseDir == null || baseDir.isBlank()) {
            throw new IllegalStateException("System property 'base.dir' is not set. Please provide -Dbase.dir=<path>");
        }
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

    public void initializeObject() {
        log.info("System initializing..");
        profileProcessor = new Iso8583ProfileResolver();
        transportProvider = new TransportProvider();
        transportRegister = new TransportRegister(transportProvider);
        correlationStore = new CacheCorrelationStore(cu.getInt("engine.cache.ttl", 30000));
        channelCfgProcessor = new ChannelCfgProcessor();
        channelCfgSelector = new ChannelCfgSelector();
        telemetryRegistry = new TelemetryRegistry(new SimpleMeterRegistry());
    }

    public void loadChannelConfiguration() throws IOException {
        log.info("Load channel configuration..");

        Path packagerPath = Path.of(System.getProperty("base.dir"),sc.getString("message.packager.path"));
        try (InputStream is = Files.newInputStream(packagerPath)) {
            packager = new GenericPackager(is);
        } catch (ISOException | IOException e) {
            throw new IllegalStateException("Failed to load ISO packager", e);
        }
        parser = new IsoParser(packager);
        metadata = channelCfgProcessor.process(Path.of(System.getProperty("base.dir"),"conf", "channel.conf"));
    }

    public void handleRouterEngine() throws Exception {
        log.info("Setup router engine..");
        camelContext = new DefaultCamelContext();
        camelContext.getExecutorServiceManager()
                .setThreadPoolFactory(new VirtualThreadPoolFactory());

        SEEngine = new SEEngine(
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
        socketManager = new SocketManager(
                transportRegister,
                telemetryRegistry,
                parser,
                new ForwardService(camelContext.createProducerTemplate())
        );

        /**
         * SocketManager is bound AFTER Camel routes are started.
         * This is intentional to guarantee routing readiness
         * before accepting inbound socket traffic.
         */
        SEEngine.bindSocketManager(socketManager);

        for (ChannelCfg cfg : metadata.channelCfgs()) {
            socketManager.createSocket(cfg);
        }
        socketManager.startAll();
    }


    public void handleHttpServer() throws InterruptedException {
        log.info("Start httpserver..");

        List<HttpServiceHandler> services = getHttpServiceHandlers();
        httpServer = new NettyHttpServer(
                        sc.getString("server.name"),
                        sc.getInt("server.port"),
                        services);

        httpServer.start();
    }

    private List<HttpServiceHandler> getHttpServiceHandlers() {
        ReloadCfgService reloadCfgService = new ReloadCfgService(socketManager, metadata, channelCfgProcessor);
        AdminHttpService adminHttpService = new AdminHttpService(socketManager);
        List<HttpServiceHandler> services = List.of(
                new SocketStatusHandler(telemetryRegistry),
                new ValidateConfigHandler(reloadCfgService),
                new ReloadConfigHandler(reloadCfgService),
                new MetricsServiceHandle(telemetryRegistry),
                new SocketStartHandler(adminHttpService),
                new SocketStopHandler(adminHttpService),
                new SocketRestartHandler(adminHttpService)
        );
        return services;
    }

    public void handleLifecycle() {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log.info("Shutdown signal received...");
            long start = System.currentTimeMillis();
            try {
                if (httpServer != null) {
                    httpServer.stop();
                }
            } catch (Exception e) {
                log.error("Error stopping HTTP server", e);
            }

            try {
                if (camelContext != null) {
                    camelContext.stop();
                }
            } catch (Exception e) {
                log.error("Error stopping Camel context", e);
            }

            try {
                if (socketManager != null) {
                    socketManager.destroyAll();
                }
            } catch (Exception e) {
                log.error("Error destroying sockets", e);
            }

            try {
                if (transportRegister != null) {
                    transportRegister.destroy();
                }
            } catch (Exception e) {
                log.error("Error destroying transport register", e);
            }

            log.info("Gracefully shutdown took {}ms", (System.currentTimeMillis() - start));
        }));
    }

    public static void main(String[] args) throws Exception {
        try {
            log.info("Starting..");
            long start = System.currentTimeMillis();
            SystemBootstrap bootstrap = new SystemBootstrap(args);
            bootstrap.loadSystemConfiguration();
            bootstrap.initializeObject();
            bootstrap.loadChannelConfiguration();
            bootstrap.handleRouterEngine();
            bootstrap.handleSocketConfiguration();
            bootstrap.handleHttpServer();
            bootstrap.handleLifecycle();
            log.info("Started took {}ms", (System.currentTimeMillis() - start));
        } catch (Exception e) {
            log.error("Fatal startup error", e);
            System.exit(1);
        }
    }

}
