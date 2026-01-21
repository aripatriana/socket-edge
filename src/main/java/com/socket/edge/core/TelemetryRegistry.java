package com.socket.edge.core;

import com.socket.edge.core.socket.AbstractSocket;
import com.socket.edge.model.Metrics;
import com.socket.edge.model.RuntimeState;
import io.micrometer.core.instrument.MeterRegistry;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class TelemetryRegistry {

    private final MeterRegistry registry;
    private final Map<String, SocketTelemetry> map = new ConcurrentHashMap<>();

    public TelemetryRegistry(MeterRegistry registry) {
        this.registry = registry;
    }

    public SocketTelemetry register(AbstractSocket socket) {
        return map.computeIfAbsent(socket.getId(),
                k -> new SocketTelemetry(registry, socket));
    }

    public SocketTelemetry unregister(AbstractSocket socket) {
        SocketTelemetry telemetry = map.remove(socket.getId());
        if (telemetry != null) {
            telemetry.dispose(); // remove meter from registry
        }
        return telemetry;
    }

    public Metrics getMetric(String id) {
        SocketTelemetry m = map.get(id);
        return m == null ? null : m.getMetrics();
    }

    public List<Metrics> getAllMetrics() {
        return map.values()
                .stream()
                .map(SocketTelemetry::getMetrics)
                .sorted(Comparator.comparing(Metrics::id))
                .toList();
    }

    public RuntimeState getRuntimeState(String id) {
        SocketTelemetry m = map.get(id);
        return m == null ? null : m.getRuntimeState();
    }

    public List<RuntimeState> getAllRuntimeState() {
        return map.values()
                .stream()
                .map(SocketTelemetry::getRuntimeState)
                .sorted(Comparator.comparing(RuntimeState::id))
                .toList();
    }
}
