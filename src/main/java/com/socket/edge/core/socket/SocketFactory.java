package com.socket.edge.core.socket;

import com.socket.edge.SystemBootstrap;
import com.socket.edge.core.MessageContextProcess;
import com.socket.edge.core.TelemetryRegistry;
import com.socket.edge.model.ChannelCfg;
import com.socket.edge.model.SocketEndpoint;
import com.socket.edge.utils.IsoParser;

/**
 * {@code SocketFactory} is responsible for creating
 * server-side and client-side {@link AbstractSocket} instances.
 *
 * <p>
 * This factory centralizes socket construction logic and ensures
 * consistent dependency injection across all socket implementations.
 * </p>
 *
 * <p>
 * Created sockets are configured with:
 * <ul>
 *   <li>Telemetry instrumentation</li>
 *   <li>ISO message parsing</li>
 *   <li>Message context processing</li>
 * </ul>
 * </p>
 *
 * <p>
 * This class does not manage socket lifecycle (start/stop).
 * It only creates fully configured socket instances.
 * </p>
 *
 * @author Ari Patriana
 * @since 1.0.0
 */
public class SocketFactory {

    /**
     * Registry used to collect telemetry and metrics.
     */
    private final TelemetryRegistry telemetryRegistry;

    /**
     * Parser used to decode and encode ISO messages.
     */
    private final IsoParser isoParser;

    /**
     * Processor responsible for handling message context lifecycle.
     */
    private final MessageContextProcess messageContextProcess;

    /**
     * Creates a new {@code SocketFactory}.
     *
     * @param telemetryRegistry telemetry registry
     * @param isoParser ISO message parser
     * @param messageContextProcess message context processor
     * @throws NullPointerException if any dependency is {@code null}
     */
    public SocketFactory(TelemetryRegistry telemetryRegistry, IsoParser isoParser, MessageContextProcess messageContextProcess) {
        this.telemetryRegistry = telemetryRegistry;
        this.isoParser = isoParser;
        this.messageContextProcess = messageContextProcess;
    }

    /**
     * Creates a server-side socket based on channel configuration.
     *
     * <p>
     * The socket will listen on the configured host and port
     * and use a pooled channel model.
     * </p>
     *
     * @param cfg channel configuration
     * @return configured server socket
     */
    public AbstractSocket createServer(ChannelCfg cfg) {
        return new DefaultServerSocket(
                SystemBootstrap.isCluster(),
                cfg.name(),
                cfg.server().listenHost(),
                cfg.server().listenPort(),
                cfg.server().pool(),
                telemetryRegistry,
                isoParser,
                messageContextProcess
        );
    }

    /**
     * Creates a client-side socket for the given endpoint.
     *
     * <p>
     * The socket will establish outgoing connections
     * to the specified remote endpoint.
     * </p>
     *
     * @param cfg channel configuration
     * @param endpoint remote socket endpoint
     * @return configured client socket
     */
    public AbstractSocket createClient(ChannelCfg cfg, SocketEndpoint endpoint) {
        return new DefaultClientSocket(
                SystemBootstrap.isCluster(),
                cfg.name(),
                endpoint,
                telemetryRegistry,
                isoParser,
                messageContextProcess
        );
    }
}

