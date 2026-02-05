package com.socket.edge.core.socket;

import com.socket.edge.constant.NodeRole;
import com.socket.edge.constant.SocketState;
import com.socket.edge.constant.SocketType;
import com.socket.edge.core.SocketTelemetry;
import com.socket.edge.core.TelemetryRegistry;
import com.socket.edge.model.EndpointKey;
import com.socket.edge.model.SocketEndpoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Base abstraction for socket-based communication endpoints.
 *
 * <p>{@code AbstractSocket} represents a logical socket instance
 * (server or client) that manages:
 * <ul>
 *   <li>Socket lifecycle (start, stop, activate, standby)</li>
 *   <li>Cluster role awareness (MASTER / SLAVE)</li>
 *   <li>Endpoint registration and resolution</li>
 *   <li>Channel pooling</li>
 *   <li>Telemetry collection and cleanup</li>
 * </ul>
 *
 * <p>This class is designed to be extended by concrete socket
 * implementations (e.g. server socket, client socket).</p>
 *
 * <p>Thread safety:
 * <ul>
 *   <li>Endpoint and telemetry registries use {@link ConcurrentHashMap}</li>
 *   <li>{@link #role} is {@code volatile} to ensure visibility
 *       across threads in clustered environments</li>
 * </ul>
 *
 *  @author Ari Patriana
 *  @since 1.0.0
 */
public abstract class AbstractSocket {

    private static final Logger log = LoggerFactory.getLogger(AbstractSocket.class);

    /**
     * Telemetry registry mapped by endpoint key.
     */
    private final Map<EndpointKey, SocketTelemetry> telemetryMap = new ConcurrentHashMap<>();

    /**
     * Registered socket endpoints mapped by endpoint key.
     */
    private final Map<EndpointKey, SocketEndpoint> endpointMap = new ConcurrentHashMap<>();

    /**
     * Central telemetry registry for registering/unregistering socket metrics.
     */
    private final TelemetryRegistry telemetryRegistry;

    /**
     * Logical socket identifier.
     */
    String id;

    /**
     * Human-readable socket name.
     */
    String name;

    /**
     * Socket bind or remote host.
     */
    String host;

    /**
     * Socket bind or remote port.
     */
    int port;

    /**
     * Socket start timestamp.
     */
    long startTime;

    /**
     * Indicates whether this socket runs in cluster mode.
     */
    private final boolean cluster;

    /**
     * Current node role in cluster mode.
     * Defaults to {@link NodeRole#SLAVE}.
     */
    private volatile NodeRole role = NodeRole.SLAVE;

    /**
     * Creates a new socket instance.
     *
     * @param cluster           whether clustering is enabled
     * @param id                unique socket identifier
     * @param name              socket name
     * @param host              bind or remote host
     * @param port              bind or remote port
     * @param telemetryRegistry telemetry registry used to track socket metrics
     */
    public AbstractSocket(
            boolean cluster,
            String id,
            String name,
            String host,
            int port,
            TelemetryRegistry telemetryRegistry
    ) {
        this.cluster = cluster;
        this.id = id;
        this.name = name;
        this.host = host;
        this.port = port;
        this.telemetryRegistry = telemetryRegistry;
    }

    /**
     * Returns the socket identifier.
     *
     * @return socket id
     */
    public String getId() {
        return id;
    }

    /**
     * Returns the socket name.
     *
     * @return socket name
     */
    public String getName() {
        return name;
    }

    /**
     * Returns the socket start time.
     *
     * @return start timestamp
     */
    public long getStartTime() {
        return startTime;
    }

    /**
     * Returns the current node role.
     *
     * @return node role
     */
    public NodeRole getRole() {
        return role;
    }

    /**
     * Returns the socket type (SERVER or CLIENT).
     *
     * @return socket type
     */
    public abstract SocketType getType();

    /**
     * Starts the socket and allocates required resources.
     *
     * @throws InterruptedException if startup is interrupted
     */
    public abstract void start() throws InterruptedException;

    /**
     * Stops the socket and releases all runtime resources.
     *
     * @throws InterruptedException if shutdown is interrupted
     */
    public abstract void stop() throws InterruptedException;

    /**
     * Activates the socket for traffic handling.
     *
     * <p>Typically called when node becomes MASTER.</p>
     *
     * @throws InterruptedException if activation is interrupted
     */
    public abstract void activate() throws InterruptedException;

    /**
     * Puts the socket into standby mode.
     *
     * <p>Typically called when node becomes SLAVE.</p>
     *
     * @throws InterruptedException if standby transition is interrupted
     */
    public abstract void standby() throws InterruptedException;

    /**
     * Returns the current socket state.
     *
     * @return socket state
     */
    public abstract SocketState getState();

    /**
     * Indicates whether clustering is enabled.
     *
     * @return {@code true} if cluster mode is enabled
     */
    public boolean isCluster() {
        return cluster;
    }

    /**
     * Returns the channel pool associated with this socket.
     *
     * @return socket channel pool
     */
    public abstract SocketChannelPooling channelPool();

    /**
     * Gracefully shuts down the socket.
     *
     * <p>This method unregisters telemetry, disposes endpoint metrics,
     * clears endpoint and telemetry caches.</p>
     *
     * @throws InterruptedException if shutdown is interrupted
     */
    public void shutdown() throws InterruptedException {
        telemetryRegistry.unregister(this);
        telemetryMap.values().forEach(SocketTelemetry::dispose);
        telemetryMap.clear();
        endpointMap.clear();
    }

    /**
     * Returns internal telemetry map.
     *
     * @return telemetry map
     */
    Map<EndpointKey, SocketTelemetry> getTelemetryMap() {
        return telemetryMap;
    }

    /**
     * Returns internal endpoint map.
     *
     * @return endpoint map
     */
    Map<EndpointKey, SocketEndpoint> getEndpointMap() {
        return endpointMap;
    }

    /**
     * Resolves an endpoint by key.
     *
     * @param endpointKey endpoint identifier
     * @return resolved endpoint or {@code null}
     */
    public SocketEndpoint resolveEndpoint(EndpointKey endpointKey) {
        return endpointMap.get(endpointKey);
    }

    /**
     * Resolves an endpoint by host and port.
     *
     * <p>For SERVER sockets, the socket port is used.
     * For CLIENT sockets, the provided port is used.</p>
     *
     * @param host endpoint host
     * @param port endpoint port
     * @return resolved endpoint or {@code null}
     */
    public SocketEndpoint resolveEndpoint(String host, int port) {
        if (getType() == SocketType.SERVER) {
            return resolveEndpoint(EndpointKey.from(host, this.port));
        } else {
            return resolveEndpoint(EndpointKey.from(host, port));
        }
    }

    /**
     * Registers a new socket endpoint and its telemetry.
     *
     * @param se socket endpoint
     */
    public void registerEndpoint(SocketEndpoint se) {
        endpointMap.put(se.id(), se);
        telemetryMap.put(se.id(), telemetryRegistry.register(this, se));
    }

    /**
     * Removes an endpoint and releases its associated resources.
     *
     * @param endpointKey endpoint identifier
     */
    public void removeEndpoint(EndpointKey endpointKey) {
        channelPool().removeByEndpoint(endpointKey);

        endpointMap.remove(endpointKey);
        SocketTelemetry socketTelemetry = telemetryMap.remove(endpointKey);
        if (socketTelemetry != null) {
            socketTelemetry.dispose();
        }
    }

    /**
     * Resolves telemetry for the given endpoint.
     *
     * @param endpointKey endpoint identifier
     * @return socket telemetry or {@code null}
     */
    public SocketTelemetry resolveTelemetry(EndpointKey endpointKey) {
        return telemetryMap.get(endpointKey);
    }

    /**
     * Returns all allowed endpoints for this socket.
     *
     * @return collection of endpoints
     */
    public Collection<SocketEndpoint> allowlist() {
        return endpointMap.values();
    }

    /**
     * Updates endpoint properties and propagates changes
     * to all active channels using that endpoint.
     *
     * @param newEp updated endpoint definition
     */
    public void updateEndpointProperties(SocketEndpoint newEp) {
        EndpointKey key = newEp.id();
        endpointMap.put(key, newEp);

        channelPool().getAllChannel().stream()
                .filter(sc -> sc.getSocketEndpoint().id().equals(key))
                .forEach(sc -> sc.setSocketEndpoint(newEp));

        channelPool().updateVersion();
    }

    /**
     * Changes the node role in cluster mode.
     *
     * @param role new node role
     */
    public void changeRole(NodeRole role) {
        this.role = role;
    }
}

