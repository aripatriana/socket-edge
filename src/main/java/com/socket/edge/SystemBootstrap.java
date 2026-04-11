package com.socket.edge;

import com.hazelcast.config.MapConfig;
import com.hazelcast.config.TcpIpConfig;
import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;
import com.socket.edge.constant.RolePreference;
import com.socket.edge.constant.ServerMode;
import com.socket.edge.core.*;
import com.socket.edge.core.cache.CorrelationStore;
import com.socket.edge.core.cache.CorrelationStoreFactory;
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
import com.socket.edge.model.FailoverPolicy;
import com.socket.edge.model.Metadata;
import com.socket.edge.model.RolePolicy;
import com.socket.edge.utils.IsoParser;
import com.socket.edge.utils.PciMaskUtil;
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
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Application bootstrap and composition root.
 *
 * <p>v3.0: All config sections fully wired — no orphan parameters.</p>
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
    private AuditLogger auditLogger;

    public SystemBootstrap(String[] args) {
        String mode = System.getProperty("server.mode");
        if (mode == null || mode.isBlank()) {
            throw new IllegalStateException(
                    "System property 'server.mode' is required. Use: -Dserver.mode=standalone | cluster");
        }
        try {
            serverMode = ServerMode.valueOf(mode.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException(
                    "Invalid server.mode: '" + mode + "'. Allowed: STANDALONE, CLUSTER", e);
        }
    }

    public void loadSystemConfiguration() {
        log.info("Load system configuration..");
        String baseDir = System.getProperty("base.dir");
        if (baseDir == null || baseDir.isBlank())
            throw new IllegalStateException("System property 'base.dir' is not set.");

        Path confDir = Path.of(baseDir, "conf");
        Path systemConf = confDir.resolve("system.conf");
        Path systemSchema = confDir.resolve("schema/schema-system.conf");

        if (!Files.exists(systemConf))
            throw new IllegalStateException("system.conf not found: " + systemConf);
        if (!Files.exists(systemSchema))
            throw new IllegalStateException("schema-system.conf not found: " + systemSchema);

        Config sysConfig = ConfigFactory.parseFile(systemConf.toFile()).resolve();
        sysConfig.checkValid(ConfigFactory.parseFile(systemSchema.toFile()));

        Config finalConfig = sysConfig;
        boolean cluster = serverMode == ServerMode.CLUSTER;

        if (cluster) {
            Path clusterConf = confDir.resolve("cluster.conf");
            Path clusterSchema = confDir.resolve("schema/schema-cluster.conf");
            if (!Files.exists(clusterConf))
                throw new IllegalStateException("cluster.conf not found: " + clusterConf);
            if (!Files.exists(clusterSchema))
                throw new IllegalStateException("schema-cluster.conf not found: " + clusterSchema);

            Config clusterConfig = ConfigFactory.parseFile(clusterConf.toFile()).resolve();
            clusterConfig.checkValid(ConfigFactory.parseFile(clusterSchema.toFile()));
            finalConfig = clusterConfig.withFallback(sysConfig).resolve();
        }

        this.rawConfig = finalConfig;
        this.systemConfig = SystemConfig.from(finalConfig, cluster);
    }

    public void initialize() throws Exception {
        log.info("System initialization..");
        String baseDir = System.getProperty("base.dir");

        // 1. Telemetry — metrics exporter from config
        TelemetryRegistry telemetryRegistry = createTelemetryRegistry(systemConfig.metrics());

        // 2. PCI masking utility
        PciMaskUtil pciMaskUtil = new PciMaskUtil(systemConfig.pci());
        log.info("PCI masking: enabled={}, fields={}, strategy={}",
                systemConfig.pci().enabled(),
                systemConfig.pci().maskFields(),
                systemConfig.pci().maskStrategy());

        // 3. Audit logger
        auditLogger = systemConfig.audit().enabled()
                ? new AuditLogger(systemConfig.audit(), baseDir, pciMaskUtil)
                : AuditLogger.disabled();

        // 4. ISO Packager & Parser
        Path packagerPath = Path.of(baseDir, systemConfig.packagerPath());
        ISOPackager packager;
        try (InputStream is = Files.newInputStream(packagerPath)) {
            packager = new GenericPackager(is);
        } catch (ISOException | IOException e) {
            throw new IllegalStateException("Failed to load ISO packager", e);
        }
        IsoParser parser = new IsoParser(packager);

        // 5. Channel config & metadata
        ChannelCfgProcessor channelCfgProcessor = new ChannelCfgProcessor();
        Metadata metadata = channelCfgProcessor.process(Path.of(baseDir, "conf", "channel.conf"));
        metadataHolder = new MetadataHolder(metadata);

        // 6. Correlation store (auto-select by mode)
        correlationStore = CorrelationStoreFactory.standalone(
                systemConfig.cacheTtl(), systemConfig.cacheMaxSize());

        // 7. Camel context
        camelContext = new DefaultCamelContext();
        camelContext.getExecutorServiceManager().setThreadPoolFactory(new VirtualThreadPoolFactory());
        MessageContextProcess messageContextProcess =
                new MessageContextProcess(camelContext.createProducerTemplate());

        // 8. ChannelGroupRegistry
        ChannelGroupRegistry groupRegistry = new ChannelGroupRegistry();

        // 9. SocketLifecycleCoordinator
        SocketLifecycleCoordinator coordinator = new SocketLifecycleCoordinator(groupRegistry);

        // 10. SocketFactory — with TcpConfig and PciMaskUtil
        SocketFactory socketFactory = new SocketFactory(
                systemConfig, telemetryRegistry, parser,
                messageContextProcess, coordinator, pciMaskUtil);

        // 11. Transport
        TransportProvider transportProvider = new TransportProvider();
        transportRegister = new TransportRegister(transportProvider);

        // 12. SocketManager
        socketManager = new SocketManager(socketFactory, transportRegister, groupRegistry);

        // 13. SEEngine with audit logger
        Iso8583ProfileResolver profileResolver = new Iso8583ProfileResolver(systemConfig);
        ChannelCfgSelector cfgSelector = new ChannelCfgSelector();
        seEngine = new SEEngine(
                systemConfig, metadataHolder, profileResolver,
                cfgSelector, correlationStore, transportProvider, auditLogger);
        seEngine.bindSocketManager(socketManager);
        camelContext.addRoutes(seEngine);
        camelContext.start();

        // 14. Create sockets
        for (ChannelCfg cfg : metadataHolder.get().channelCfgs()) {
            socketManager.createChannelGroup(cfg);
        }

        // 15. Cluster or standalone
        if (systemConfig.clusterEnabled()) {
            handleCluster(transportProvider);
        } else {
            socketManager.startAll();
        }

        // 16. HTTP admin server — with full HttpConfig
        handleHttpServer(channelCfgProcessor, telemetryRegistry, socketManager);

        // 17. Shutdown hook — with drain timeout
        handleLifecycle();

        log.info("System initialized successfully");
    }

    private void handleCluster(TransportProvider transportProvider) throws Exception {
        log.info("Cluster mode enabled, initializing..");

        System.setProperty("jgroups.bind_addr", rawConfig.getString("cluster.jgroup.bind-addr"));
        List<String> members = rawConfig.getStringList("cluster.members");
        System.setProperty("jgroups.members",
                members.stream()
                        .map(ip -> ip + "[" + rawConfig.getInt("cluster.jgroup.port") + "]")
                        .collect(Collectors.joining(",")));

        Path jGroupPath = Path.of(System.getProperty("base.dir"),
                rawConfig.getString("cluster.jgroup-path"));

        // Hazelcast
        String clusterName = rawConfig.hasPath("cluster.cluster-name")
                ? rawConfig.getString("cluster.cluster-name") : "socket-edge-cluster";

        com.hazelcast.config.Config hzConfig = new com.hazelcast.config.Config();
        hzConfig.setClusterName(clusterName);
        hzConfig.getNetworkConfig().getJoin().getMulticastConfig().setEnabled(false);
        TcpIpConfig tcp = hzConfig.getNetworkConfig().getJoin().getTcpIpConfig().setEnabled(true);
        members.forEach(tcp::addMember);

        int backupCount = rawConfig.hasPath("cluster.hazelcast.map-backup-count")
                ? rawConfig.getInt("cluster.hazelcast.map-backup-count") : 1;
        int asyncBackup = rawConfig.hasPath("cluster.hazelcast.map-async-backup-count")
                ? rawConfig.getInt("cluster.hazelcast.map-async-backup-count") : 0;

        hzConfig.addMapConfig(new MapConfig("socket-edge-state")
                .setBackupCount(backupCount).setAsyncBackupCount(asyncBackup));

        HazelcastInstance hazelcast = Hazelcast.newHazelcastInstance(hzConfig);
        JChannel channel = new JChannel(jGroupPath.toAbsolutePath().toString());
        ClusterListener listener = new SocketClusterAdapter(socketManager);

        // Role policy
        RolePreference prefer = RolePreference.valueOf(
                (rawConfig.hasPath("cluster.role.prefer")
                        ? rawConfig.getString("cluster.role.prefer") : "slave").toUpperCase());
        boolean strict = rawConfig.hasPath("cluster.role.strict")
                && rawConfig.getBoolean("cluster.role.strict");

        // Failover policy
        long failoverTimeout = rawConfig.hasPath("cluster.failover.failover-timeout")
                ? rawConfig.getLong("cluster.failover.failover-timeout") : 5000;
        String splitBrainStr = rawConfig.hasPath("cluster.failover.split-brain-policy")
                ? rawConfig.getString("cluster.failover.split-brain-policy") : "keep-oldest";
        int quorumSize = rawConfig.hasPath("cluster.failover.quorum-size")
                ? rawConfig.getInt("cluster.failover.quorum-size") : 1;

        FailoverPolicy failoverPolicy = new FailoverPolicy(
                quorumSize, FailoverPolicy.parseSplitBrainPolicy(splitBrainStr), failoverTimeout);

        log.info("Cluster: name={}, quorum={}, splitBrain={}, failover={}ms",
                clusterName, quorumSize, splitBrainStr, failoverTimeout);

        ClusterManager clusterManager = new ClusterManager(
                channel, new RolePolicy(prefer, strict), failoverPolicy,
                listener, clusterName, members.size());

        // Override correlation store
        correlationStore = CorrelationStoreFactory.cluster(
                hazelcast, "correlation-store", systemConfig.cacheTtl());
        seEngine.bindCorrelationStore(correlationStore);
        clusterManager.start();
    }

    private void handleHttpServer(ChannelCfgProcessor channelCfgProcessor,
                                  TelemetryRegistry telemetryRegistry,
                                  SocketManager socketManager) throws Exception {
        log.info("Start HTTP server..");

        // Services
        ReloadCfgService reloadCfgService = new ReloadCfgService(
                socketManager, metadataHolder, channelCfgProcessor);
        AdminHttpService adminHttpService = new AdminHttpService(socketManager);
        CorrelationCacheService correlationCacheService = new CorrelationCacheService(correlationStore);

        // Register all handlers — flat, explicit, no constructor side-effects
        List<HttpServiceHandler> handlers = List.of(
                // Health probes (configurable paths)
                new HealthLivenessHandler(systemConfig.health().livenessPath()),
                new HealthReadinessHandler(systemConfig.health().readinessPath(),
                        socketManager, systemConfig.health().startupProbeDelay()),
                new HealthCheckHandler(adminHttpService, systemConfig.clusterEnabled()),

                // Monitoring (GET)
                new QueueHandler(telemetryRegistry),
                new MetricsHandler(telemetryRegistry),
                new StatusHandler(telemetryRegistry),
                new CacheHandler(correlationCacheService),

                // Socket control (POST)
                new SocketControlHandler.StartHandler(adminHttpService),
                new SocketControlHandler.StopHandler(adminHttpService),
                new SocketControlHandler.RestartHandler(adminHttpService),

                // Config management
                new ConfigHandler.ValidateHandler(reloadCfgService),
                new ConfigHandler.ReloadHandler(reloadCfgService)
        );

        httpServer = new NettyHttpServer(systemConfig.serverName(), systemConfig.http(),
                new ArrayList<>(handlers));
        httpServer.start();
    }

    /**
     * Shutdown hook with drain timeout and grace period from config.
     */
    private void handleLifecycle() {
        long drainTimeout = systemConfig.lifecycle().drainTimeout();
        long gracePeriod = systemConfig.lifecycle().shutdownGracePeriod();

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log.info("Shutdown signal received...");
            long start = System.currentTimeMillis();

            // Phase 1: Drain — wait for inflight messages
            if (drainTimeout > 0 && correlationStore != null) {
                log.info("Draining inflight messages (timeout={}ms)...", drainTimeout);
                long deadline = System.currentTimeMillis() + drainTimeout;
                while (System.currentTimeMillis() < deadline && correlationStore.size() > 0) {
                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
                int remaining = correlationStore.size();
                if (remaining > 0) {
                    log.warn("Drain timeout reached, {} messages still inflight", remaining);
                } else {
                    log.info("Drain complete, all messages processed");
                }
            }

            // Phase 2: Stop components
            safeStop("HTTP server", () -> { if (httpServer != null) httpServer.stop(); });
            safeStop("Camel context", () -> { if (camelContext != null) camelContext.stop(); });
            safeStop("Sockets", () -> { if (socketManager != null) socketManager.destroyAll(); });
            safeStop("Transport", () -> { if (transportRegister != null) transportRegister.destroy(); });
            safeStop("Correlation store", () -> { if (correlationStore != null) correlationStore.shutdown(); });
            safeStop("Audit logger", () -> { if (auditLogger != null) auditLogger.shutdown(); });

            // Phase 3: Grace period for final cleanup
            if (gracePeriod > 0) {
                try {
                    Thread.sleep(gracePeriod);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }

            log.info("Shutdown complete in {}ms", (System.currentTimeMillis() - start));
        }));
    }

    private void safeStop(String component, Runnable action) {
        try {
            action.run();
        } catch (Exception e) {
            log.error("Error stopping {}", component, e);
        }
    }

    /**
     * Creates MeterRegistry based on engine.metrics.exporter config.
     */
    private TelemetryRegistry createTelemetryRegistry(SystemConfig.MetricsConfig metricsConfig) {
        MeterRegistry meterRegistry = switch (metricsConfig.exporter().toLowerCase()) {
            case "jmx" -> {
                log.info("Metrics exporter: JMX (domain={})", metricsConfig.jmxDomain());
                String domain = metricsConfig.jmxDomain();
                JmxConfig config = key -> "jmx.domain".equals(key) ? domain : null;
                yield new JmxMeterRegistry(config, Clock.SYSTEM);
            }
            case "prometheus" -> {
                log.info("Metrics exporter: Prometheus (port={})", metricsConfig.prometheusPort());
                // Prometheus registry — actual HTTP endpoint created via health handler
                // For now use SimpleMeterRegistry as placeholder
                // TODO: integrate PrometheusMeterRegistry when dependency is added
                log.warn("Prometheus exporter configured but dependency not yet added. Using SimpleMeterRegistry.");
                yield new SimpleMeterRegistry();
            }
            default -> {
                log.info("Metrics exporter: Simple (in-memory)");
                yield new SimpleMeterRegistry();
            }
        };

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
