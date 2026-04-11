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
import com.socket.edge.core.socket.*;
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
 * <p>v3.0 changes:
 * <ul>
 *   <li>No static fields — all state is instance-based</li>
 *   <li>No circular dependencies — linear initialization order</li>
 *   <li>Uses {@link SystemConfig} instead of raw Config with static access</li>
 *   <li>Uses {@link ChannelGroupRegistry} for explicit server↔client tracking</li>
 *   <li>Uses {@link SocketLifecycleCoordinator} for handler lifecycle coordination</li>
 *   <li>Removed hardcoded developer path</li>
 * </ul>
 *
 * <p>Dependency graph (top-down, no cycles):
 * <pre>
 *   SystemConfig (immutable)
 *       ↓
 *   ChannelGroupRegistry (pure registry)
 *       ↓
 *   SocketLifecycleCoordinator (depends on: Registry)
 *       ↓
 *   SocketFactory (depends on: Config, Coordinator, Telemetry, Parser)
 *       ↓
 *   SocketManager (depends on: Factory, TransportRegister, Registry)
 *       ↓
 *   SEEngine (depends on: SocketManager via late-bind)
 * </pre>
 *
 * @author Ari Patriana
 * @since 3.0.0
 */
public class SystemBootstrap {

    private static final Logger log = LoggerFactory.getLogger(SystemBootstrap.class);

    private SystemConfig systemConfig;
    private Config rawConfig;
    private ServerMode serverMode;
    private CamelContext camelContext;
    private SocketManager socketManager;
    private TransportRegister transportRegister;
    private CorrelationStore correlationStore;
    private NettyHttpServer httpServer;
    private MetadataHolder metadataHolder;
    private SEEngine seEngine;

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
                            "Allowed values: STANDALONE, CLUSTER", e);
        }
    }

    public void loadSystemConfiguration() {
        log.info("Load system configuration..");
        String baseDir = System.getProperty("base.dir");
        if (baseDir == null || baseDir.isBlank()) {
            throw new IllegalStateException("System property 'base.dir' is not set.");
        }

        Path confDir = Path.of(baseDir, "conf");
        Path systemConf = confDir.resolve("system.conf");
        Path systemSchema = confDir.resolve("schema/schema-system.conf");

        if (!Files.exists(systemConf)) {
            throw new IllegalStateException("system.conf not found: " + systemConf);
        }
        if (!Files.exists(systemSchema)) {
            throw new IllegalStateException("schema-system.conf not found: " + systemSchema);
        }

        Config sysConfig = ConfigFactory.parseFile(systemConf.toFile()).resolve();
        sysConfig.checkValid(ConfigFactory.parseFile(systemSchema.toFile()));

        Config finalConfig = sysConfig;
        boolean cluster = serverMode == ServerMode.CLUSTER;

        if (cluster) {
            Path clusterConf = confDir.resolve("cluster.conf");
            Path clusterSchema = confDir.resolve("schema/schema-cluster.conf");

            if (!Files.exists(clusterConf)) {
                throw new IllegalStateException("cluster.conf not found: " + clusterConf);
            }
            if (!Files.exists(clusterSchema)) {
                throw new IllegalStateException("schema-cluster.conf not found: " + clusterSchema);
            }

            Config clusterConfig = ConfigFactory.parseFile(clusterConf.toFile()).resolve();
            clusterConfig.checkValid(ConfigFactory.parseFile(clusterSchema.toFile()));
            finalConfig = clusterConfig.withFallback(sysConfig).resolve();

            Path jgroupPath = Path.of(baseDir, finalConfig.getString("cluster.jgroup-path"));
            if (!Files.exists(jgroupPath)) {
                throw new IllegalStateException("jgroups.xml not found: " + jgroupPath);
            }
        }

        this.rawConfig = finalConfig;
        this.systemConfig = SystemConfig.from(finalConfig, cluster);
    }

    public void initialize() throws Exception {
        log.info("System initialization..");

        // 1. Telemetry
        TelemetryRegistry telemetryRegistry = createTelemetryRegistry();

        // 2. ISO Packager & Parser
        Path packagerPath = Path.of(System.getProperty("base.dir"), systemConfig.packagerPath());
        ISOPackager packager;
        try (InputStream is = Files.newInputStream(packagerPath)) {
            packager = new GenericPackager(is);
        } catch (ISOException | IOException e) {
            throw new IllegalStateException("Failed to load ISO packager", e);
        }
        IsoParser parser = new IsoParser(packager);

        // 3. Channel config & metadata
        ChannelCfgProcessor channelCfgProcessor = new ChannelCfgProcessor();
        Metadata metadata = channelCfgProcessor.process(
                Path.of(System.getProperty("base.dir"), "conf", "channel.conf"));
        metadataHolder = new MetadataHolder(metadata);

        // 4. Correlation store
        correlationStore = new CacheCorrelationStore(systemConfig.cacheTtl());

        // 5. Camel context
        camelContext = new DefaultCamelContext();
        camelContext.getExecutorServiceManager().setThreadPoolFactory(new VirtualThreadPoolFactory());
        MessageContextProcess messageContextProcess =
                new MessageContextProcess(camelContext.createProducerTemplate());

        // 6. ChannelGroupRegistry (pure, no deps)
        ChannelGroupRegistry groupRegistry = new ChannelGroupRegistry();

        // 7. SocketLifecycleCoordinator (depends on: registry)
        SocketLifecycleCoordinator coordinator = new SocketLifecycleCoordinator(groupRegistry);

        // 8. SocketFactory (depends on: config, coordinator — NOT SocketManager)
        SocketFactory socketFactory = new SocketFactory(
                systemConfig, telemetryRegistry, parser, messageContextProcess, coordinator);

        // 9. TransportRegister
        TransportProvider transportProvider = new TransportProvider();
        transportRegister = new TransportRegister(transportProvider);

        // 10. SocketManager (depends on: factory — one-way, NO circular dep)
        socketManager = new SocketManager(socketFactory, transportRegister, groupRegistry);

        // 11. SEEngine
        Iso8583ProfileResolver profileResolver = new Iso8583ProfileResolver(systemConfig);
        ChannelCfgSelector cfgSelector = new ChannelCfgSelector();
        seEngine = new SEEngine(
                systemConfig, metadataHolder, profileResolver,
                cfgSelector, correlationStore, transportProvider);
        seEngine.bindSocketManager(socketManager);
        camelContext.addRoutes(seEngine);
        camelContext.start();

        // 12. Create sockets (AFTER engine is ready)
        for (ChannelCfg cfg : metadataHolder.get().channelCfgs()) {
            socketManager.createChannelGroup(cfg);
        }

        // 13. Cluster or standalone
        if (systemConfig.clusterEnabled()) {
            handleCluster(transportProvider);
        } else {
            socketManager.startAll();
        }

        // 14. HTTP admin server
        handleHttpServer(channelCfgProcessor, telemetryRegistry);

        // 15. Shutdown hook
        handleLifecycle();

        log.info("System initialized successfully");
    }

    private void handleCluster(TransportProvider transportProvider) throws Exception {
        log.info("Cluster mode enabled, initializing cluster manager..");

        System.setProperty("jgroups.bind_addr", rawConfig.getString("cluster.jgroup.bind-addr"));
        System.setProperty("jgroups.members",
                rawConfig.getStringList("cluster.members").stream()
                        .map(ip -> ip + "[" + rawConfig.getInt("cluster.jgroup.port") + "]")
                        .collect(Collectors.joining(",")));

        Path jGroupPath = Path.of(System.getProperty("base.dir"),
                rawConfig.getString("cluster.jgroup-path"));

        // Hazelcast
        com.hazelcast.config.Config hzConfig = new com.hazelcast.config.Config();
        hzConfig.setClusterName(rawConfig.hasPath("cluster.cluster-name")
                ? rawConfig.getString("cluster.cluster-name") : "socket-edge-cluster");

        hzConfig.getNetworkConfig().getJoin().getMulticastConfig().setEnabled(false);
        TcpIpConfig tcp = hzConfig.getNetworkConfig().getJoin().getTcpIpConfig().setEnabled(true);
        rawConfig.getStringList("cluster.members").forEach(tcp::addMember);

        hzConfig.addMapConfig(new MapConfig("socket-edge-state")
                .setBackupCount(1).setAsyncBackupCount(0));

        HazelcastInstance hazelcast = Hazelcast.newHazelcastInstance(hzConfig);
        JChannel channel = new JChannel(jGroupPath.toAbsolutePath().toString());

        ClusterListener listener = new SocketClusterAdapter(socketManager);

        RolePreference prefer = RolePreference.valueOf(
                (rawConfig.hasPath("cluster.role.prefer")
                        ? rawConfig.getString("cluster.role.prefer") : "slave").toUpperCase());
        boolean strict = rawConfig.hasPath("cluster.role.strict")
                && rawConfig.getBoolean("cluster.role.strict");

        String clusterName = rawConfig.hasPath("cluster.cluster-name")
                ? rawConfig.getString("cluster.cluster-name") : "socket-edge-cluster";
        ClusterManager clusterManager = new ClusterManager(channel, new RolePolicy(prefer, strict), listener, clusterName);

        // Override correlation store with Hazelcast-backed
        correlationStore = new HazelcastCorrelationStore(
                hazelcast.getMap("correlation-store"), systemConfig.cacheTtl());
        seEngine.bindCorrelationStore(correlationStore);

        log.warn("Override correlation store with hazelcast-backed store for cluster mode");
        clusterManager.start();
    }

    private void handleHttpServer(ChannelCfgProcessor channelCfgProcessor,
                                  TelemetryRegistry telemetryRegistry) throws Exception {
        log.info("Start HTTP server..");

        List<HttpServiceHandler> services = new ArrayList<>();

        ReloadCfgService reloadCfgService = new ReloadCfgService(socketManager, metadataHolder, channelCfgProcessor);
        AdminHttpService adminHttpService = new AdminHttpService(socketManager);
        CorrelationCacheService correlationCacheService = new CorrelationCacheService(correlationStore);

        new CommonServiceHandler(telemetryRegistry, adminHttpService, correlationCacheService, services);
        new ConfigServiceHandler(reloadCfgService, services);

        httpServer = new NettyHttpServer(systemConfig.serverName(), systemConfig.http().port(), services);
        httpServer.start();
    }

    private void handleLifecycle() {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log.info("Shutdown signal received...");
            long start = System.currentTimeMillis();

            safeStop("HTTP server", () -> { if (httpServer != null) httpServer.stop(); });
            safeStop("Camel context", () -> { if (camelContext != null) camelContext.stop(); });
            safeStop("Sockets", () -> { if (socketManager != null) socketManager.destroyAll(); });
            safeStop("Transport", () -> { if (transportRegister != null) transportRegister.destroy(); });
            safeStop("Correlation store", () -> { if (correlationStore != null) correlationStore.shutdown(); });

            log.info("Gracefully shutdown took {}ms", (System.currentTimeMillis() - start));
        }));
    }

    private void safeStop(String component, Runnable action) {
        try {
            action.run();
        } catch (Exception e) {
            log.error("Error stopping {}", component, e);
        }
    }

    private TelemetryRegistry createTelemetryRegistry() {
        boolean enableJmx = Boolean.parseBoolean(System.getProperty("jmx.meter.enabled", "false"));

        MeterRegistry meterRegistry;
        if (enableJmx) {
            log.info("JMX Meter Registry ENABLED");
            JmxConfig config = key -> "jmx.domain".equals(key) ? "socket.edge" : null;
            meterRegistry = new JmxMeterRegistry(config, Clock.SYSTEM);
        } else {
            log.info("JMX Meter Registry DISABLED (using SimpleMeterRegistry)");
            meterRegistry = new SimpleMeterRegistry();
        }

        return new TelemetryRegistry(meterRegistry);
    }

    public static void main(String[] args) throws Exception {
        try {
            log.info("Starting Socket Edge v3.0.0..");
            long start = System.currentTimeMillis();
            SystemBootstrap bootstrap = new SystemBootstrap(args);
            bootstrap.loadSystemConfiguration();
            bootstrap.initialize();
            log.info("Started in {}ms", (System.currentTimeMillis() - start));
        } catch (Exception e) {
            log.error("Fatal startup error", e);
            System.exit(1);
        }
    }
}
