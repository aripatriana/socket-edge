package com.socket.edge.core;

import com.typesafe.config.Config;

import java.util.List;

/**
 * Immutable system configuration record.
 *
 * <p>All runtime configuration is captured here at startup and injected
 * via constructor to all components that need it. No static access,
 * no global state.</p>
 *
 * @author Ari Patriana
 * @since 3.0.0
 */
public record SystemConfig(
        String serverName,
        HttpConfig http,
        TcpConfig tcp,
        String packagerKey,
        String packagerPath,
        long cacheTtl,
        long cacheMaxSize,
        boolean clusterEnabled,
        SedaConfig seda,
        LifecycleConfig lifecycle,
        MetricsConfig metrics,
        HealthConfig health,
        PciConfig pci,
        AuditConfig audit
) {

    // --- Nested config records ---

    public record HttpConfig(
            int port,
            String bindAddress,
            int idleTimeout,
            String authMode,
            String authUsername,
            String authPassword
    ) {}

    public record TcpConfig(
            int maxFrameLength,
            int idleTimeout,
            int soRcvBuf,
            int soSndBuf,
            boolean soKeepAlive,
            boolean tcpNoDelay,
            int connectTimeout
    ) {}

    public record SedaConfig(
            StageConfig receive,
            StageConfig inbound,
            StageConfig outbound
    ) {}

    public record StageConfig(
            int consumers,
            int queueSize,
            boolean blockWhenFull
    ) {}

    public record LifecycleConfig(
            long drainTimeout,
            long shutdownGracePeriod
    ) {}

    public record MetricsConfig(
            String exporter,
            int prometheusPort,
            String jmxDomain
    ) {}

    public record HealthConfig(
            String livenessPath,
            String readinessPath,
            long startupProbeDelay
    ) {}

    public record PciConfig(
            boolean enabled,
            List<String> maskFields,
            String maskStrategy
    ) {}

    public record AuditConfig(
            boolean enabled,
            String path,
            List<String> fields
    ) {}

    // --- Builder from Typesafe Config ---

    /**
     * Root namespace prefix.
     * Matches the root tag in system.conf: system { ... }
     */
    private static final String ROOT = "system.";

    public static SystemConfig from(Config config, boolean cluster) {
        return new SystemConfig(
                config.getString(ROOT + "server.name"),
                httpFrom(config),
                tcpFrom(config),
                config.getString(ROOT + "message.packager.key"),
                config.getString(ROOT + "message.packager.path"),
                config.getLong(ROOT + "engine.cache.ttl"),
                config.getLong(ROOT + "engine.cache.max-size"),
                cluster,
                new SedaConfig(
                        stageFrom(config, ROOT + "engine.seda.receive"),
                        stageFrom(config, ROOT + "engine.seda.inbound"),
                        stageFrom(config, ROOT + "engine.seda.outbound")
                ),
                lifecycleFrom(config),
                metricsFrom(config),
                healthFrom(config),
                pciFrom(config),
                auditFrom(config)
        );
    }

    private static HttpConfig httpFrom(Config config) {
        return new HttpConfig(
                config.getInt(ROOT + "server.http.port"),
                config.getString(ROOT + "server.http.bind-address"),
                config.getInt(ROOT + "server.http.idle-timeout"),
                config.getString(ROOT + "server.http.auth.mode"),
                config.getString(ROOT + "server.http.auth.username"),
                config.getString(ROOT + "server.http.auth.password")
        );
    }

    private static TcpConfig tcpFrom(Config config) {
        return new TcpConfig(
                config.getInt(ROOT + "server.tcp.max-frame-length"),
                config.getInt(ROOT + "server.tcp.idle-timeout"),
                config.getInt(ROOT + "server.tcp.so-rcvbuf"),
                config.getInt(ROOT + "server.tcp.so-sndbuf"),
                config.getBoolean(ROOT + "server.tcp.so-keepalive"),
                config.getBoolean(ROOT + "server.tcp.tcp-nodelay"),
                config.getInt(ROOT + "server.tcp.connect-timeout")
        );
    }

    private static StageConfig stageFrom(Config config, String prefix) {
        return new StageConfig(
                config.getInt(prefix + ".consumers"),
                config.getInt(prefix + ".queue-size"),
                config.getBoolean(prefix + ".block-when-full")
        );
    }

    private static LifecycleConfig lifecycleFrom(Config config) {
        return new LifecycleConfig(
                config.getLong(ROOT + "engine.lifecycle.drain-timeout"),
                config.getLong(ROOT + "engine.lifecycle.shutdown-grace-period")
        );
    }

    private static MetricsConfig metricsFrom(Config config) {
        return new MetricsConfig(
                config.getString(ROOT + "engine.metrics.exporter"),
                config.getInt(ROOT + "engine.metrics.prometheus-port"),
                config.getString(ROOT + "engine.metrics.jmx-domain")
        );
    }

    private static HealthConfig healthFrom(Config config) {
        return new HealthConfig(
                config.getString(ROOT + "engine.health.liveness-path"),
                config.getString(ROOT + "engine.health.readiness-path"),
                config.getLong(ROOT + "engine.health.startup-probe-delay")
        );
    }

    private static PciConfig pciFrom(Config config) {
        return new PciConfig(
                config.getBoolean(ROOT + "logging.pci.enabled"),
                config.getStringList(ROOT + "logging.pci.mask-fields"),
                config.getString(ROOT + "logging.pci.mask-strategy")
        );
    }

    private static AuditConfig auditFrom(Config config) {
        return new AuditConfig(
                config.getBoolean(ROOT + "logging.audit.enabled"),
                config.getString(ROOT + "logging.audit.path"),
                config.getStringList(ROOT + "logging.audit.fields")
        );
    }
}
