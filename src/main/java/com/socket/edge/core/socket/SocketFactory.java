package com.socket.edge.core.socket;

import com.socket.edge.core.MessageContextProcess;
import com.socket.edge.core.SystemConfig;
import com.socket.edge.core.TelemetryRegistry;
import com.socket.edge.model.ChannelCfg;
import com.socket.edge.model.SocketEndpoint;
import com.socket.edge.utils.IsoParser;

/**
 * Creates server-side and client-side {@link AbstractSocket} instances.
 *
 * <p>v3.0 changes:
 * <ul>
 *   <li>No longer depends on {@link SocketManager} — circular dependency eliminated</li>
 *   <li>Receives {@link SystemConfig} instead of calling static {@code SystemBootstrap.isCluster()}</li>
 *   <li>Receives {@link SocketLifecycleCoordinator} for handler lifecycle coordination</li>
 * </ul>
 *
 * @author Ari Patriana
 * @since 3.0.0
 */
public class SocketFactory {

    private final boolean clusterEnabled;
    private final TelemetryRegistry telemetryRegistry;
    private final IsoParser isoParser;
    private final MessageContextProcess messageContextProcess;
    private final SocketLifecycleCoordinator coordinator;

    public SocketFactory(
            SystemConfig config,
            TelemetryRegistry telemetryRegistry,
            IsoParser isoParser,
            MessageContextProcess messageContextProcess,
            SocketLifecycleCoordinator coordinator
    ) {
        this.clusterEnabled = config.clusterEnabled();
        this.telemetryRegistry = telemetryRegistry;
        this.isoParser = isoParser;
        this.messageContextProcess = messageContextProcess;
        this.coordinator = coordinator;
    }

    public AbstractSocket createServer(ChannelCfg cfg) {
        return new DefaultServerSocket(
                clusterEnabled,
                cfg.name(),
                cfg.server().listenHost(),
                cfg.server().listenPort(),
                cfg.server().pool(),
                coordinator,
                telemetryRegistry,
                isoParser,
                messageContextProcess
        );
    }

    public AbstractSocket createClient(ChannelCfg cfg, SocketEndpoint endpoint) {
        return new DefaultClientSocket(
                clusterEnabled,
                cfg.name(),
                endpoint,
                coordinator,
                telemetryRegistry,
                isoParser,
                messageContextProcess
        );
    }
}
