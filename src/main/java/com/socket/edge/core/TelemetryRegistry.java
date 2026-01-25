package com.socket.edge.core;

import com.socket.edge.core.socket.AbstractSocket;
import com.socket.edge.model.Metrics;
import com.socket.edge.model.Queue;
import com.socket.edge.model.RuntimeState;
import com.socket.edge.model.SocketEndpoint;
import com.socket.edge.utils.CommonUtil;
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

    public SocketTelemetry register(AbstractSocket socket, SocketEndpoint se) {
        String hashId = CommonUtil.hashId(socket.getId(), se.id().id());
        SocketTelemetry telemetry = byId.computeIfAbsent(
                hashId,
                k -> new SocketTelemetry(registry, socket, hashId)
        );

        nameToIds
                .computeIfAbsent(socket.getName(), k -> ConcurrentHashMap.newKeySet())
                .add(hashId);

        return telemetry;
    }

    public void unregister(AbstractSocket socket) {
        // remove from name index
        Set<String> ids = nameToIds.remove(socket.getName());
        if (ids != null) {
            ids.forEach(id -> {
                SocketTelemetry telemetry = byId.remove(socket.getId());
                if (telemetry != null) {
                    telemetry.dispose();
                }
            });
        }
    }

    public SocketTelemetry unregister(AbstractSocket socket, SocketEndpoint se) {
        // remove from name index
        String hashId = CommonUtil.hashId(socket.getId(), se.id().id());
        Set<String> ids = nameToIds.get(socket.getName());
        if (ids != null) {
            ids.remove(hashId);
            if (ids.isEmpty()) {
                nameToIds.remove(socket.getName());
            }
        }

        SocketTelemetry telemetry = byId.remove(hashId);
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
        SocketTelemetry socketTelemetry = getById(id);
        if (socketTelemetry != null) {
            return socketTelemetry.getMetrics();
        }
        return null;
    }

    public RuntimeState getRuntimeStateById(String id) {
        SocketTelemetry socketTelemetry = getById(id);
        if (socketTelemetry != null) {
            return socketTelemetry.getRuntimeState();
        }
        return null;
    }

    public Queue getQueueById(String id) {
        SocketTelemetry socketTelemetry = getById(id);
        if (socketTelemetry != null) {
            return socketTelemetry.getQueue();
        }
        return null;
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
