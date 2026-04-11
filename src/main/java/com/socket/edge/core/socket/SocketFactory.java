package com.socket.edge.core.socket;

import com.socket.edge.core.MessageContextProcess;
import com.socket.edge.core.SystemConfig;
import com.socket.edge.core.TelemetryRegistry;
import com.socket.edge.utils.IsoParser;
import com.socket.edge.utils.PciMaskUtil;
import com.socket.edge.model.ChannelCfg;
import com.socket.edge.model.SocketEndpoint;

/**
 * Creates server-side and client-side socket instances.
 *
 * <p>v3.0: Passes {@link SystemConfig.TcpConfig} to sockets so all TCP
 * parameters (frame length, idle timeout, buffer sizes, etc.) are
 * read from configuration instead of being hardcoded.</p>
 *
 * @author Ari Patriana
 * @since 3.0.0
 */
public class SocketFactory {

    private final boolean clusterEnabled;
    private final SystemConfig.TcpConfig tcpConfig;
    private final TelemetryRegistry telemetryRegistry;
    private final IsoParser isoParser;
    private final MessageContextProcess messageContextProcess;
    private final SocketLifecycleCoordinator coordinator;
    private final PciMaskUtil pciMaskUtil;

    public SocketFactory(
            SystemConfig config,
            TelemetryRegistry telemetryRegistry,
            IsoParser isoParser,
            MessageContextProcess messageContextProcess,
            SocketLifecycleCoordinator coordinator,
            PciMaskUtil pciMaskUtil
    ) {
        this.clusterEnabled = config.clusterEnabled();
        this.tcpConfig = config.tcp();
        this.telemetryRegistry = telemetryRegistry;
        this.isoParser = isoParser;
        this.messageContextProcess = messageContextProcess;
        this.coordinator = coordinator;
        this.pciMaskUtil = pciMaskUtil;
    }

    public AbstractSocket createServer(ChannelCfg cfg) {
        return new DefaultServerSocket(
                clusterEnabled,
                cfg.name(),
                cfg.server().listenHost(),
                cfg.server().listenPort(),
                cfg.server().pool(),
                tcpConfig,
                coordinator,
                telemetryRegistry,
                isoParser,
                messageContextProcess,
                pciMaskUtil
        );
    }

    public AbstractSocket createClient(ChannelCfg cfg, SocketEndpoint endpoint) {
        return new DefaultClientSocket(
                clusterEnabled,
                cfg.name(),
                endpoint,
                tcpConfig,
                coordinator,
                telemetryRegistry,
                isoParser,
                messageContextProcess,
                pciMaskUtil
        );
    }
}
