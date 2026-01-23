package com.socket.edge.core;

import com.socket.edge.core.socket.AbstractSocket;
import com.socket.edge.model.Metrics;
import com.socket.edge.model.Queue;
import com.socket.edge.model.RuntimeState;
import io.micrometer.core.instrument.MeterRegistry;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class TelemetryRegistry {

    private final MeterRegistry registry;

    // primary
    private final Map<String, SocketTelemetry> byId  = new ConcurrentHashMap<>();

    // secondary index (name -> many ids)
    private final Map<String, Set<String>> nameToIds = new ConcurrentHashMap<>();

    public TelemetryRegistry(MeterRegistry registry) {
        this.registry = registry;
    }

    public SocketTelemetry register(AbstractSocket socket) {
        SocketTelemetry telemetry = byId.computeIfAbsent(
                socket.getId(),
                k -> new SocketTelemetry(registry, socket)
        );

        nameToIds
                .computeIfAbsent(socket.getName(), k -> ConcurrentHashMap.newKeySet())
                .add(socket.getId());

        return telemetry;
    }

    public SocketTelemetry unregister(AbstractSocket socket) {
        // remove from name index
        Set<String> ids = nameToIds.get(socket.getName());
        if (ids != null) {
            ids.remove(socket.getId());
            if (ids.isEmpty()) {
                nameToIds.remove(socket.getName());
            }
        }

        SocketTelemetry telemetry = byId.remove(socket.getId());
        if (telemetry != null) {
            telemetry.dispose();
        }
        return telemetry;
    }

    public SocketTelemetry getById(String id) {
        return byId.get(id);
    }

    public List<SocketTelemetry> getByName(String name) {
        Set<String> ids = nameToIds.get(name);
        if (ids == null) return List.of();

        return ids.stream()
                .map(byId::get)
                .filter(Objects::nonNull)
                .toList();
    }

    public Metrics getMetricsById(String id) {
        return getById(id).getMetrics();
    }

    public RuntimeState getRuntimeStateById(String id) {
        return getById(id).getRuntimeState();
    }

    public Queue getQueueById(String id) {
        return getById(id).getQueue();
    }

    public List<Metrics> getMetricsByName(String name) {
        return getByName(name).stream()
                .map(SocketTelemetry::getMetrics)
                .sorted(Comparator.comparing(Metrics::id))
                .toList();
    }

    public List<RuntimeState> getRuntimeStateByName(String name) {
        return getByName(name).stream()
                .map(SocketTelemetry::getRuntimeState)
                .sorted(Comparator.comparing(RuntimeState::id))
                .toList();
    }

    public List<Queue> getQueueByName(String name) {
        return getByName(name).stream()
                .map(SocketTelemetry::getQueue)
                .sorted(Comparator.comparing(Queue::id))
                .toList();
    }

    public List<Metrics> getAllMetrics() {
        return byId.values()
                .stream()
                .map(SocketTelemetry::getMetrics)
                .sorted(Comparator.comparing(Metrics::id))
                .toList();
    }

    public List<RuntimeState> getAllRuntimeState() {
        return byId.values()
                .stream()
                .map(SocketTelemetry::getRuntimeState)
                .sorted(Comparator.comparing(RuntimeState::id))
                .toList();
    }

    public List<Queue> getAllQueue() {
        return byId.values()
                .stream()
                .map(SocketTelemetry::getQueue)
                .sorted(Comparator.comparing(Queue::id))
                .toList();
    }
}
