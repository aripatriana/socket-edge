package com.socket.edge;

import com.hazelcast.config.MapConfig;
import com.hazelcast.config.TcpIpConfig;
import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;
import com.socket.edge.constant.RolePreference;
import com.socket.edge.constant.ServerMode;
import com.socket.edge.core.*;
import com.socket.edge.core.cache.CacheCorrelationStore;
import com.socket.edge.core.cache.CorrelationStore;
import com.socket.edge.core.cluster.ClusterListener;
import com.socket.edge.core.cluster.ClusterManager;
import com.socket.edge.core.cluster.SocketClusterAdapter;
import com.socket.edge.core.engine.SEEngine;
import com.socket.edge.core.iso.Iso8583ProfileResolver;
import com.socket.edge.core.socket.SocketFactory;
import com.socket.edge.core.socket.SocketManager;
import com.socket.edge.core.transport.TransportProvider;
import com.socket.edge.core.transport.TransportRegister;
import com.socket.edge.http.NettyHttpServer;
import com.socket.edge.http.handler.*;
import com.socket.edge.http.service.AdminHttpService;
import com.socket.edge.http.service.ReloadCfgService;
import com.socket.edge.model.ChannelCfg;
import com.socket.edge.model.Metadata;
import com.socket.edge.model.RolePolicy;
import com.socket.edge.utils.ConfigUtil;
import com.socket.edge.utils.IsoParser;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.jmx.JmxConfig;
import io.micrometer.jmx.JmxMeterRegistry;
import org.apache.camel.CamelContext;
import org.apache.camel.impl.DefaultCamelContext;
import org.jgroups.JChannel;
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
import java.util.stream.Collectors;

public class SystemBootstrap {

    private static final Logger log = LoggerFactory.getLogger(SystemBootstrap.class);

    private Iso8583ProfileResolver profileProcessor;
    private ChannelCfgSelector channelCfgSelector;
    private TransportProvider transportProvider;
    private TransportRegister transportRegister;
    private CorrelationStore correlationStore;
    private SocketManager socketManager;
    private SocketFactory socketFactory;
    private MessageContextProcess messageContextProcess;
    private ISOPackager packager;
    private IsoParser parser;
    private ChannelCfgProcessor channelCfgProcessor;
    private CamelContext camelContext;
    private MetadataHolder metadataHolder;
    private NettyHttpServer httpServer;
    public static Config sc;
    private final ConfigUtil cu = new ConfigUtil();
    private TelemetryRegistry telemetryRegistry;
    private SEEngine SEEngine;
    private static boolean cluster = false;
    private ServerMode serverMode;

    static {
        // For testing purpose
        if (System.getProperty("base.dir") == null) {
            System.setProperty("base.dir", "C:\\Users\\ari.patriana\\DATA\\Project\\Github\\socket-edge\\src\\main\\resources");
        }
    }

    public SystemBootstrap(String[] args) {
        String mode = System.getProperty("server.mode");

        if (mode == null || mode.isBlank()) {
            throw new IllegalStateException(
                    "System property 'server.mode' is required. " +
                            "Use: -Dserver.mode=standalone | cluster"
            );
        }

        try {
            serverMode = ServerMode.valueOf(mode.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException(
                    "Invalid server.mode value: '" + mode + "'. " +
                            "Allowed values: STANDALONE, CLUSTER",
                    e
            );
        }

        cluster = serverMode == ServerMode.CLUSTER;
    }

    public void loadSystemConfiguration() {
        log.info("Load system configuration..");
        String baseDir = System.getProperty("base.dir");
        if (baseDir == null || baseDir.isBlank()) {
            throw new IllegalStateException("System property 'base.dir' is not set. Please provide -Dbase.dir=<path>");
        }

        Path confDir = Path.of(baseDir, "conf");
        Path systemConf = confDir.resolve("system.conf");
        Path clusterConf = confDir.resolve("cluster.conf");

        Path systemSchema = confDir.resolve("schema/schema-system.conf");
        Path clusterSchema = confDir.resolve("schema/schema-cluster.conf");

        if (!Files.exists(systemConf)) {
            throw new IllegalStateException("system.conf not found: " + systemConf);
        }

        if (!Files.exists(systemSchema)) {
            throw new IllegalStateException("schema-system.conf not found: " + systemSchema);
        }

        Config systemConfig = ConfigFactory.parseFile(systemConf.toFile()).resolve();
        Config systemSchemaConfig = ConfigFactory.parseFile(systemSchema.toFile());

        systemConfig.checkValid(systemSchemaConfig);

        Config finalConfig = systemConfig;

        if (cluster) {
            if (!Files.exists(clusterConf)) {
                throw new IllegalStateException(
                        "cluster.enabled=true but cluster.conf not found: " + clusterConf
                );
            }
            if (!Files.exists(clusterSchema)) {
                throw new IllegalStateException(
                        "schema-cluster.conf not found: " + clusterSchema
                );
            }

            Config clusterConfig = ConfigFactory.parseFile(clusterConf.toFile()).resolve();
            Config clusterSchemaConfig = ConfigFactory.parseFile(clusterSchema.toFile());

            clusterConfig.checkValid(clusterSchemaConfig);

            finalConfig = clusterConfig
                    .withFallback(systemConfig)
                    .resolve();

            Path jgroupPath = Path.of(baseDir, finalConfig.getString("cluster.jgroup-path"));
            if (!Files.exists(jgroupPath)) {
                throw new IllegalStateException(
                        "cluster.enabled=true but jgroups.xml not found: "
                                + jgroupPath.toAbsolutePath()
                );
            }
        }

        this.sc = finalConfig;
    }

    public void initializeObject() {
        log.info("System initialization..");
        profileProcessor = new Iso8583ProfileResolver();
        transportProvider = new TransportProvider();
        transportRegister = new TransportRegister(transportProvider);
        correlationStore = new CacheCorrelationStore(cu.getInt("engine.cache.ttl", 30000));
        channelCfgProcessor = new ChannelCfgProcessor();
        channelCfgSelector = new ChannelCfgSelector();

        boolean enableJmxMeter = Boolean.parseBoolean(
                System.getProperty("jmx.meter.enabled", "false")
        );

        MeterRegistry meterRegistry = null;
        if (enableJmxMeter) {
            log.info("JMX Meter Registry ENABLED");

            JmxConfig config = new JmxConfig() {
                @Override
                public String get(String key) {
                    if ("jmx.domain".equals(key)) {
                        return "socket.edge";
                    }
                    return null;
                }
            };
            meterRegistry = new JmxMeterRegistry(config, Clock.SYSTEM);
        } else {
            log.info("JMX Meter Registry DISABLED (using SimpleMeterRegistry)");
            meterRegistry = new SimpleMeterRegistry();
        }
        telemetryRegistry = new TelemetryRegistry(meterRegistry);
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
        Metadata metadata = channelCfgProcessor.process(Path.of(System.getProperty("base.dir"),"conf", "channel.conf"));
        metadataHolder = new MetadataHolder(metadata);
    }

    public void handleRouterEngine() throws Exception {
        log.info("Setup router engine..");
        camelContext = new DefaultCamelContext();
        camelContext.getExecutorServiceManager()
                .setThreadPoolFactory(new VirtualThreadPoolFactory());

        SEEngine = new SEEngine(
                metadataHolder,
                profileProcessor,
                channelCfgSelector,
                correlationStore,
                transportProvider
        );

        camelContext.addRoutes(SEEngine);
        camelContext.start();
    }

    public void handleSocketConfiguration() throws Exception {
        log.info("Setup socket configuration..");
        messageContextProcess = new MessageContextProcess(camelContext.createProducerTemplate());
        socketFactory = new SocketFactory(telemetryRegistry, parser, messageContextProcess);
        socketManager = new SocketManager(socketFactory, transportRegister);

        /**
         * SocketManager is bound AFTER Camel routes are started.
         * This is intentional to guarantee routing readiness
         * before accepting inbound socket traffic.
         */
        SEEngine.bindSocketManager(socketManager);

        for (ChannelCfg cfg : metadataHolder.get().channelCfgs()) {
            socketManager.createSocket(cfg);
        }

        if (cluster) {
            handleCluster();
        } else {
            socketManager.startAll();
        }
    }

    public void handleCluster() throws Exception {
        log.info("Cluster mode enabled, initializing cluster manager..");

        // initialize jgroup parameter
        System.setProperty("jgroups.bind_addr",
                sc.getString("cluster.jgroup.bind-addr"));

        System.setProperty("jgroups.members",
                sc.getStringList("cluster.members")
                    .stream()
                    .map(ip -> ip + "[" + sc.getInt("cluster.jgroup.port") + "]")
                    .collect(Collectors.joining(",")));

        Path jGroupPath = Path.of(System.getProperty("base.dir"), sc.getString("cluster.jgroup-path"));

        // intialize hazelcast
        com.hazelcast.config.Config hzConfig = new com.hazelcast.config.Config();
        hzConfig.setClusterName(cu.getString("cluster.cluster-name", "socket-edge-cluster"));

        hzConfig.getNetworkConfig()
                .getJoin()
                .getMulticastConfig().setEnabled(false);

        TcpIpConfig tcp = hzConfig.getNetworkConfig()
                .getJoin()
                .getTcpIpConfig()
                .setEnabled(true);

       cu.getStringList("cluster.members").forEach(m -> {
            tcp.addMember(m);
        });

        hzConfig.addMapConfig(
                new MapConfig("socket-edge-state")
                        .setBackupCount(1)
                        .setAsyncBackupCount(0)
        );

        // initialize channel and hazelcast cluster
        HazelcastInstance hazelcast = Hazelcast.newHazelcastInstance(hzConfig);
        JChannel channel = new JChannel(jGroupPath.toAbsolutePath().toString());

        ClusterListener listener = new SocketClusterAdapter(socketManager);

        RolePreference prefer =
                RolePreference.valueOf(
                        cu.getString("cluster.role.prefer", "slave").toUpperCase()
                );

        boolean strict = cu.getBoolean("cluster.role.strict", false);

        RolePolicy rolePolicy =
                new RolePolicy(prefer, strict);

        ClusterManager clusterManager =
                new ClusterManager(
                        channel,
                        rolePolicy,
                        listener
                );

        clusterManager.start();
    }


    public void handleHttpServer() throws Exception {
        log.info("Start httpserver..");

        List<HttpServiceHandler> services = getHttpServiceHandlers();
        httpServer = new NettyHttpServer(
                        sc.getString("server.name"),
                        sc.getInt("server.port"),
                        services);

        httpServer.start();
    }

    private List<HttpServiceHandler> getHttpServiceHandlers() {
        ReloadCfgService reloadCfgService = new ReloadCfgService(socketManager, metadataHolder, channelCfgProcessor);
        AdminHttpService adminHttpService = new AdminHttpService(socketManager);
        List<HttpServiceHandler> services = List.of(
                new SocketStatusHandler(telemetryRegistry),
                new ValidateConfigHandler(reloadCfgService),
                new ReloadConfigHandler(reloadCfgService),
                new MetricsServiceHandle(telemetryRegistry),
                new QueueServiceHandle(telemetryRegistry),
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
            log.info("Starting application..");
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

    public static boolean isCluster() {
        return cluster;
    }
}
