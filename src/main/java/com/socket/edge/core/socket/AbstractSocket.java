package com.socket.edge.core.socket;

import com.socket.edge.constant.SocketState;
import com.socket.edge.constant.SocketType;
import com.socket.edge.core.SocketTelemetry;
import com.socket.edge.core.TelemetryRegistry;
import com.socket.edge.model.EndpointKey;
import com.socket.edge.model.SocketEndpoint;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public abstract class AbstractSocket {

    private final Map<EndpointKey, SocketTelemetry> telemetryMap = new ConcurrentHashMap<>();
    private final Map<EndpointKey, SocketEndpoint> endpointMap = new ConcurrentHashMap<>();
    private TelemetryRegistry telemetryRegistry;

    String name;
    String id;
    String host;
    int port;
    long startTime;

    public AbstractSocket(String id, String name, String host, int port, TelemetryRegistry telemetryRegistry) {
        this.id = id;
        this.name = name;
        this.host = host;
        this.port = port;
        this.telemetryRegistry = telemetryRegistry;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public long getStartTime() {
        return startTime;
    }

    public abstract SocketType getType();

    /*
     * start the socket
     */
    public abstract void start() throws InterruptedException;

    /*
     * stop the socket
     */
    public abstract void stop() throws InterruptedException;

    public abstract SocketState getState();

    public abstract SocketChannelPooling channelPool();

    public void shutdown() throws InterruptedException {
        telemetryRegistry.unregister(this);
        telemetryMap.values().forEach(SocketTelemetry::dispose);
        telemetryMap.clear();
        endpointMap.clear();
    };

    Map<EndpointKey, SocketTelemetry> getTelemetryMap() {
        return telemetryMap;
    }

    Map<EndpointKey, SocketEndpoint> getEndpointMap() {
        return endpointMap;
    }

    public SocketEndpoint resolveEndpoint(EndpointKey endpointKey) {
        return endpointMap.get(endpointKey);
    }

    public SocketEndpoint resolveEndpoint(String host, int port) {
        if (getType() == SocketType.SERVER) {
            return resolveEndpoint(EndpointKey.from(host, this.port));
        } else {
            return resolveEndpoint(EndpointKey.from(host, port));
        }
    }

    public void addEndpoint(SocketEndpoint se) {
        endpointMap.put(se.id(), se);
        telemetryMap.put(se.id(), telemetryRegistry.register(this, se));
    }

    public void removeEndpoint(EndpointKey endpointKey) {
        channelPool().removeByEndpoint(endpointKey);

        endpointMap.remove(endpointKey);
        SocketTelemetry socketTelemetry = telemetryMap.remove(endpointKey);
        if (socketTelemetry != null) {
            socketTelemetry.dispose();
        }
    }

    public SocketTelemetry resolveTelemetry(EndpointKey endpointKey) {
        return telemetryMap.get(endpointKey);
    }

    public Collection<SocketEndpoint> allowlist() {
        return endpointMap.values();
    }

    public void updateEndpointProperties(SocketEndpoint newEp) {
        EndpointKey key = newEp.id();
        endpointMap.put(key, newEp);

        channelPool().getAllChannel().stream()
                .filter(sc -> sc.getSocketEndpoint().id().equals(key))
                .forEach(sc -> sc.setSocketEndpoint(newEp));
    }

}
