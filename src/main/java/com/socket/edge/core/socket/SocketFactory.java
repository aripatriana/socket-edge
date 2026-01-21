package com.socket.edge.core.socket;

import com.socket.edge.core.ForwardService;
import com.socket.edge.core.TelemetryRegistry;
import com.socket.edge.model.ChannelCfg;
import com.socket.edge.model.SocketEndpoint;
import com.socket.edge.utils.IsoParser;

public class SocketFactory {

    private final TelemetryRegistry telemetryRegistry;
    private final IsoParser isoParser;
    private final ForwardService forwardService;

    public SocketFactory(
            TelemetryRegistry telemetryRegistry,
            IsoParser isoParser,
            ForwardService forwardService
    ) {
        this.telemetryRegistry = telemetryRegistry;
        this.isoParser = isoParser;
        this.forwardService = forwardService;
    }

    public AbstractSocket createServer(ChannelCfg cfg) {
        return new NettyServerSocket(
                cfg.name(),
                cfg.server().listenPort(),
                cfg.server().pool(),
                telemetryRegistry,
                isoParser,
                forwardService
        );
    }

    public AbstractSocket createClient(ChannelCfg cfg, SocketEndpoint endpoint) {
        return new NettyClientSocket(
                cfg.name(),
                endpoint,
                telemetryRegistry,
                isoParser,
                forwardService
        );
    }
}
