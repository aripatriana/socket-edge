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
import com.socket.edge.core.cache.HazelcastCorrelationStore;
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
import com.socket.edge.http.service.CorrelationCacheService;
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
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Application bootstrap and composition root.
 *
 * <p>
 * {@code SystemBootstrap} is responsible for initializing and wiring
 * all core components of the Socket Edge system, including:
 * <ul>
 * <li>System and cluster configuration loading</li>
 * <li>Channel and ISO 8583 metadata processing</li>
 * <li>Routing engine (Apache Camel)</li>
 * <li>Socket layer (Netty-based)</li>
 * <li>Transport providers</li>
 * <li>Cluster management (optional)</li>
 * <li>HTTP admin and monitoring services</li>
 * </ul>
 *
 * <p>
 * The bootstrap supports two operating modes:
 * <ul>
 * <li>{@link ServerMode#STANDALONE} – single-node execution</li>
 * <li>{@link ServerMode#CLUSTER} – active–passive clustered execution</li>
 * </ul>
 *
 * <p>
 * Startup flow (high-level):
 * <ol>
 * <li>Load system and cluster configuration</li>
 * <li>Initialize core objects and registries</li>
 * <li>Load channel and ISO metadata</li>
 * <li>Start routing engine</li>
 * <li>Initialize sockets (and cluster manager if enabled)</li>
 * <li>Start HTTP server</li>
 * <li>Register shutdown hooks</li>
 * </ol>
 *
 * <p>
 * This class should be instantiated once and acts as the main
 * lifecycle owner of the application.
 * </p>
 *
 * @author Ari Patriana
 * @since 1.0.0
 */
public class SystemBootstrap {

    private static final Logger log = LoggerFactory.getLogger(SystemBootstrap.class);

    /**
     * ISO 8583 profile resolver.
     */
    private Iso8583ProfileResolver profileProcessor;

    /**
     * Channel configuration selector.
     */
    private ChannelCfgSelector channelCfgSelector;

    /**
     * Transport provider resolver.
     */
    private TransportProvider transportProvider;

    /**
     * Transport register for lifecycle management.
     */
    private TransportRegister transportRegister;

    /**
     * Correlation store for request–response matching.
     */
    private CorrelationStore correlationStore;

    /**
     * Socket manager controlling socket lifecycle.
     */
    private SocketManager socketManager;

    /**
     * Socket factory for creating socket instances.
     */
    private SocketFactory socketFactory;

    /**
     * Message dispatcher into Camel routes.
     */
    private MessageContextProcess messageContextProcess;

    /**
     * ISO packager used for parsing ISO 8583 messages.
     */
    private ISOPackager packager;

    /**
     * ISO message parser.
     */
    private IsoParser parser;

    /**
     * Channel configuration processor.
     */
    private ChannelCfgProcessor channelCfgProcessor;

    /**
     * Apache Camel context.
     */
    private CamelContext camelContext;

    /**
     * Runtime metadata holder.
     */
    private MetadataHolder metadataHolder;

    /**
     * Embedded HTTP server for admin and monitoring endpoints.
     */
    private NettyHttpServer httpServer;

    /**
     * Global system configuration.
     */
    private static volatile Config sc;

    /**
     * Configuration utility.
     */
    private final ConfigUtil cu = new ConfigUtil();

    /**
     * Telemetry registry for metrics and monitoring.
     */
    private TelemetryRegistry telemetryRegistry;

    /**
     * Core routing engine.
     */
    private SEEngine SEEngine;

    /**
     * Indicates whether cluster mode is enabled.
     */
    private static boolean cluster = false;

    /**
     * Server operating mode.
     */
    private ServerMode serverMode;

    // OPT-01: Removed hardcoded developer path.
    // base.dir MUST be provided via -Dbase.dir=<path> JVM argument.

    /**
     * Creates a new SystemBootstrap instance.
     *
     * <p>
     * The server mode is determined by the system property
     * {@code server.mode}.
     * </p>
     *
     * @param args application arguments
     */
    public SystemBootstrap(String[] args) {
        String mode = System.getProperty("server.mode");

        if (mode == null || mode.isBlank()) {
            throw new IllegalStateException(
                    "System property 'server.mode' is required. " +
                            "Use: -Dserver.mode=standalone | cluster");
        }

        try {
            serverMode = ServerMode.valueOf(mode.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException(
                    "Invalid server.mode value: '" + mode + "'. " +
                            "Allowed values: STANDALONE, CLUSTER",
                    e);
        }

        cluster = serverMode == ServerMode.CLUSTER;
    }

    /**
     * Loads and validates system and cluster configuration files.
     *
     * <p>
     * This method performs schema validation and merges
     * system and cluster configuration when cluster mode
     * is enabled.
     * </p>
     */
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
                        "cluster.enabled=true but cluster.conf not found: " + clusterConf);
            }
            if (!Files.exists(clusterSchema)) {
                throw new IllegalStateException(
                        "schema-cluster.conf not found: " + clusterSchema);
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
                                + jgroupPath.toAbsolutePath());
            }
        }

        sc = finalConfig;
    }

    /**
     * Initializes core runtime objects and registries.
     */
    public void initializeObject() {
        log.info("System initialization..");
        profileProcessor = new Iso8583ProfileResolver();
        transportProvider = new TransportProvider();
        transportRegister = new TransportRegister(transportProvider);
        correlationStore = new CacheCorrelationStore(cu.getInt("engine.cache.ttl", 30000));
        channelCfgProcessor = new ChannelCfgProcessor();
        channelCfgSelector = new ChannelCfgSelector();

        boolean enableJmxMeter = Boolean.parseBoolean(
                System.getProperty("jmx.meter.enabled", "false"));

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

    /**
     * Loads channel configuration, ISO packager, and metadata.
     */
    public void loadChannelConfiguration() throws IOException {
        log.info("Load channel configuration..");

        Path packagerPath = Path.of(System.getProperty("base.dir"), sc.getString("message.packager.path"));
        try (InputStream is = Files.newInputStream(packagerPath)) {
            packager = new GenericPackager(is);
        } catch (ISOException | IOException e) {
            throw new IllegalStateException("Failed to load ISO packager", e);
        }
        parser = new IsoParser(packager);
        Metadata metadata = channelCfgProcessor
                .process(Path.of(System.getProperty("base.dir"), "conf", "channel.conf"));
        metadataHolder = new MetadataHolder(metadata);
    }

    /**
     * Initializes and starts the routing engine.
     */
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
                transportProvider);

        camelContext.addRoutes(SEEngine);
        camelContext.start();
    }

    /**
     * Initializes socket layer and cluster manager if enabled.
     */
    public void handleSocketConfiguration() throws Exception {
        log.info("Setup socket configuration..");
        messageContextProcess = new MessageContextProcess(camelContext.createProducerTemplate());

        // OPT-07: Fixed circular null-reference.
        // Create SocketFactory first (without SocketManager), then SocketManager,
        // then bind them together via deferred setters.
        socketFactory = new SocketFactory(telemetryRegistry, parser, messageContextProcess);
        socketManager = new SocketManager(socketFactory, transportRegister);
        socketFactory.bindSocketManager(socketManager);

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

    /**
     * Initializes cluster components (JGroups + Hazelcast).
     */
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
                        .setAsyncBackupCount(0));

        // initialize channel and hazelcast cluster
        HazelcastInstance hazelcast = Hazelcast.newHazelcastInstance(hzConfig);
        JChannel channel = new JChannel(jGroupPath.toAbsolutePath().toString());

        ClusterListener listener = new SocketClusterAdapter(socketManager);

        RolePreference prefer = RolePreference.valueOf(
                cu.getString("cluster.role.prefer", "slave").toUpperCase());

        boolean strict = cu.getBoolean("cluster.role.strict", false);

        RolePolicy rolePolicy = new RolePolicy(prefer, strict);

        ClusterManager clusterManager = new ClusterManager(
                channel,
                rolePolicy,
                listener);

        /**
         * CorrelationStore is bound Before cluster manager started.
         */
        correlationStore = new HazelcastCorrelationStore(hazelcast.getMap("correlation-store"),
                cu.getInt("engine.cache.ttl", 30000));
        SEEngine.bindCorrelationStore(correlationStore);

        log.warn("Override correlation store using hazelcast-backed store for cluster mode");

        clusterManager.start();
    }

    /**
     * Starts the embedded HTTP server.
     */
    public void handleHttpServer() throws Exception {
        log.info("Start httpserver..");

        List<HttpServiceHandler> services = new ArrayList<>();
        addHttpServiceHandlers(services);
        httpServer = new NettyHttpServer(
                sc.getString("server.name"),
                sc.getInt("server.port"),
                services);

        httpServer.start();
    }

    private void addHttpServiceHandlers(List<HttpServiceHandler> services) {
        ReloadCfgService reloadCfgService = new ReloadCfgService(socketManager, metadataHolder, channelCfgProcessor);
        AdminHttpService adminHttpService = new AdminHttpService(socketManager);
        CorrelationCacheService correlationCacheService = new CorrelationCacheService(correlationStore);
        new CommonServiceHandler(telemetryRegistry, adminHttpService, correlationCacheService, services);
        new ConfigServiceHandler(reloadCfgService, services);
    }

    /**
     * Registers JVM shutdown hook for graceful shutdown.
     */
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

            // OPT-05: Shut down correlation store to prevent cleanup thread leak.
            try {
                if (correlationStore != null) {
                    correlationStore.shutdown();
                }
            } catch (Exception e) {
                log.error("Error shutting down correlation store", e);
            }

            log.info("Gracefully shutdown took {}ms", (System.currentTimeMillis() - start));
        }));
    }

    /**
     * Application entry point.
     */
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

    /**
     * Indicates whether the application is running in cluster mode.
     *
     * @return {@code true} if cluster mode is enabled
     */
    public static boolean isCluster() {
        return cluster;
    }

    /**
     * Returns the global system configuration.
     *
     * @return system configuration (never {@code null} after bootstrap)
     */
    public static Config getConfig() {
        return sc;
    }

    /**
     * Sets the global system configuration.
     *
     * <p>
     * Intended for test use only. In production, configuration
     * is loaded via {@link #loadSystemConfiguration()}.
     * </p>
     *
     * @param config configuration to set
     */
    public static void setConfig(Config config) {
        sc = config;
    }
}
