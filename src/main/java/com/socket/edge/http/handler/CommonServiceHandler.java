package com.socket.edge.http.handler;

import com.socket.edge.constant.NodeRole;
import com.socket.edge.constant.SocketState;
import com.socket.edge.core.SystemConfig;
import com.socket.edge.core.TelemetryRegistry;
import com.socket.edge.core.socket.AbstractSocket;
import com.socket.edge.core.socket.SocketManager;
import com.socket.edge.http.service.AdminHttpService;
import com.socket.edge.http.service.CorrelationCacheService;
import com.socket.edge.utils.JsonUtil;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.http.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Common HTTP service handlers including health probes, metrics, queues.
 *
 * <p>v3.0: Health probe paths configurable via {@code engine.health.*}.
 * Readiness checks actual socket state. Startup probe delay respected.</p>
 *
 * @author Ari Patriana
 * @since 3.0.0
 */
public class CommonServiceHandler {
    private static final Logger log = LoggerFactory.getLogger(CommonServiceHandler.class);

    private final SystemConfig systemConfig;
    private final TelemetryRegistry telemetryRegistry;
    private final AdminHttpService adminHttpService;
    private final CorrelationCacheService correlationCacheService;
    private final SocketManager socketManager;
    private final long startupTime = System.currentTimeMillis();

    public CommonServiceHandler(
            SystemConfig systemConfig,
            TelemetryRegistry telemetryRegistry,
            AdminHttpService adminHttpService,
            CorrelationCacheService correlationCacheService,
            SocketManager socketManager,
            List<HttpServiceHandler> services
    ) {
        this.systemConfig = systemConfig;
        this.telemetryRegistry = telemetryRegistry;
        this.adminHttpService = adminHttpService;
        this.correlationCacheService = correlationCacheService;
        this.socketManager = socketManager;
        initialize(services);
    }

    public void initialize(List<HttpServiceHandler> services) {
        services.add(new QueueServiceHttpHandler());
        services.add(new MetricsHttpHandler());
        services.add(new GetCacheHttpHandler());

        // Health probes with configurable paths
        SystemConfig.HealthConfig health = systemConfig.health();
        services.add(new LivenessHandler(health.livenessPath()));
        services.add(new ReadinessHandler(health.readinessPath()));

        // Legacy healthcheck path for backward compatibility
        services.add(new LegacyHealthCheckHandler());
    }

    /**
     * Liveness probe — returns 200 if JVM and engine are alive.
     */
    public class LivenessHandler implements HttpServiceHandler {
        private final String path;
        LivenessHandler(String path) { this.path = path; }

        @Override public String path() { return path; }

        @Override
        public FullHttpResponse handle(FullHttpRequest request, QueryStringDecoder decoder) {
            Map<String, Object> result = new HashMap<>();
            result.put("status", "UP");
            result.put("uptime", System.currentTimeMillis() - startupTime);
            return jsonResponse(HttpResponseStatus.OK, result);
        }
    }

    /**
     * Readiness probe — returns 200 only when sockets are ACTIVE
     * and startup probe delay has passed.
     */
    public class ReadinessHandler implements HttpServiceHandler {
        private final String path;
        ReadinessHandler(String path) { this.path = path; }

        @Override public String path() { return path; }

        @Override
        public FullHttpResponse handle(FullHttpRequest request, QueryStringDecoder decoder) {
            Map<String, Object> result = new HashMap<>();

            // Startup probe delay
            long elapsed = System.currentTimeMillis() - startupTime;
            long delay = systemConfig.health().startupProbeDelay();
            if (elapsed < delay) {
                result.put("status", "STARTING");
                result.put("message", "Startup probe delay: " + (delay - elapsed) + "ms remaining");
                return jsonResponse(HttpResponseStatus.SERVICE_UNAVAILABLE, result);
            }

            // Check if any socket is ACTIVE
            boolean anyActive = socketManager.getSockets().stream()
                    .anyMatch(s -> s.getState() == SocketState.ACTIVE
                            || s.getState() == SocketState.LISTEN);

            if (anyActive) {
                result.put("status", "READY");

                // Socket summary
                Map<String, String> sockets = new HashMap<>();
                socketManager.getSockets().forEach(s ->
                        sockets.put(s.getId(), s.getState().name()));
                result.put("sockets", sockets);

                return jsonResponse(HttpResponseStatus.OK, result);
            } else {
                result.put("status", "NOT_READY");
                result.put("message", "No active sockets");
                return jsonResponse(HttpResponseStatus.SERVICE_UNAVAILABLE, result);
            }
        }
    }

    /**
     * Legacy /healthcheck endpoint for backward compatibility.
     */
    public class LegacyHealthCheckHandler implements HttpServiceHandler {
        @Override public String path() { return "/healthcheck"; }

        @Override
        public FullHttpResponse handle(FullHttpRequest request, QueryStringDecoder decoder) {
            Map<String, Object> result = new HashMap<>();
            try {
                NodeRole role = adminHttpService.getNodeRole();
                if (role != NodeRole.MASTER) {
                    result.put("status", "STANDBY");
                } else {
                    result.put("status", "OK");
                }
                result.put("role", role.name());
                result.put("mode", systemConfig.clusterEnabled() ? "CLUSTER" : "STANDALONE");
            } catch (Exception e) {
                result.put("status", "FAILED");
                result.put("message", e.getMessage());
                return jsonResponse(HttpResponseStatus.INTERNAL_SERVER_ERROR, result);
            }
            return jsonResponse(HttpResponseStatus.OK, result);
        }
    }

    public class QueueServiceHttpHandler implements HttpServiceHandler {
        @Override public String path() { return "/socket/queues"; }

        @Override
        public FullHttpResponse handle(FullHttpRequest request, QueryStringDecoder decoder) {
            String id = param(decoder, "id");
            String name = param(decoder, "name");
            Map<String, Object> result = new HashMap<>();
            try {
                if (id != null) {
                    result.put("result", "all".equalsIgnoreCase(id)
                            ? telemetryRegistry.getAllQueue()
                            : List.of(telemetryRegistry.getQueueById(id)));
                } else if (name != null) {
                    result.put("result", telemetryRegistry.getQueueByName(name));
                }
                result.put("status", "OK");
            } catch (Exception e) {
                log.error("Error", e);
                result.put("status", "FAILED");
                result.put("message", e.getMessage());
            }
            return jsonResponse(HttpResponseStatus.OK, result);
        }
    }

    public class MetricsHttpHandler implements HttpServiceHandler {
        @Override public String path() { return "/socket/metrics"; }

        @Override
        public FullHttpResponse handle(FullHttpRequest request, QueryStringDecoder decoder) {
            String id = param(decoder, "id");
            String name = param(decoder, "name");
            Map<String, Object> result = new HashMap<>();
            try {
                if (id != null) {
                    result.put("result", "all".equalsIgnoreCase(id)
                            ? telemetryRegistry.getAllMetrics()
                            : List.of(telemetryRegistry.getMetricsById(id)));
                } else if (name != null) {
                    result.put("result", telemetryRegistry.getMetricsByName(name));
                }
                result.put("status", "OK");
            } catch (Exception e) {
                log.error("Error", e);
                result.put("status", "FAILED");
                result.put("message", e.getMessage());
            }
            return jsonResponse(HttpResponseStatus.OK, result);
        }
    }

    public class GetCacheHttpHandler implements HttpServiceHandler {
        @Override public String path() { return "/count-cache"; }

        @Override
        public FullHttpResponse handle(FullHttpRequest request, QueryStringDecoder decoder) {
            Map<String, Object> result = new HashMap<>();
            try {
                result.put("result", correlationCacheService.countSize());
                result.put("status", "OK");
            } catch (Exception e) {
                result.put("status", "FAILED");
                result.put("message", e.getMessage());
            }
            return jsonResponse(HttpResponseStatus.OK, result);
        }
    }

    // --- Helpers ---

    private static String param(QueryStringDecoder decoder, String key) {
        return decoder.parameters().getOrDefault(key, List.of())
                .stream().findFirst().orElse(null);
    }

    private static FullHttpResponse jsonResponse(HttpResponseStatus status, Map<String, Object> body) {
        FullHttpResponse resp = new DefaultFullHttpResponse(
                HttpVersion.HTTP_1_1, status,
                Unpooled.copiedBuffer(JsonUtil.toJson(body), StandardCharsets.UTF_8));
        resp.headers()
                .set(HttpHeaderNames.CONTENT_TYPE, "application/json")
                .setInt(HttpHeaderNames.CONTENT_LENGTH, resp.content().readableBytes());
        return resp;
    }
}
