package com.socket.edge.http.handler;

import com.socket.edge.SystemBootstrap;
import com.socket.edge.constant.NodeRole;
import com.socket.edge.core.TelemetryRegistry;
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

public class CommonServiceHandler {
    private static final Logger log = LoggerFactory.getLogger(ConfigServiceHandler.class);

    private TelemetryRegistry telemetryRegistry;
    private AdminHttpService adminHttpService;
    private CorrelationCacheService correlationCacheService;

    public CommonServiceHandler(TelemetryRegistry telemetryRegistry, AdminHttpService adminHttpService, CorrelationCacheService correlationCacheService, List<HttpServiceHandler> services) {
        this.telemetryRegistry = telemetryRegistry;
        this.adminHttpService = adminHttpService;
        this.correlationCacheService = correlationCacheService;
        initialize(services);
    }

    public void initialize(List<HttpServiceHandler> services) {
        services.add(new QueueServiceHttpHandler());
        services.add(new MetricsHttpHandler());
        services.add(new HealthCheckHttpHandler());
        services.add(new GetCacheHttpHandler());
    }

    public class QueueServiceHttpHandler implements HttpServiceHandler {

        @Override
        public String path() {
            return "/socket/queues";
        }

        @Override
        public FullHttpResponse handle(FullHttpRequest request, QueryStringDecoder decoder) {
            String id = decoder.parameters()
                    .getOrDefault("id", List.of())
                    .stream()
                    .findFirst()
                    .orElse(null);

            String name = decoder.parameters()
                    .getOrDefault("name", List.of())
                    .stream()
                    .findFirst()
                    .orElse(null);

            Map<String, Object> result = new HashMap<>();
            try {
                if (id != null && !id.isEmpty()) {
                    if (id.equalsIgnoreCase("all")) {
                        result.put("result", telemetryRegistry.getAllQueue());
                    } else {
                        result.put("result", List.of(telemetryRegistry.getQueueById(id)));
                    }
                } else if (name != null && !name.isEmpty()) {
                    result.put("result", telemetryRegistry.getQueueByName(name));
                } else {
                    result.put("message", "No action peformed");
                }
                result.put("status", "OK");
            } catch (Exception e) {
                log.error("Error {}", e.getCause());
                result.put("status", "FAILED");
                result.put("message", e.getMessage());
            }
            return httpSuccessResponse(result);
        }
    }

    public class MetricsHttpHandler implements HttpServiceHandler {

        @Override
        public String path() {
            return "/socket/metrics";
        }

        @Override
        public FullHttpResponse handle(FullHttpRequest request, QueryStringDecoder decoder) {
            String id = decoder.parameters()
                    .getOrDefault("id", List.of())
                    .stream()
                    .findFirst()
                    .orElse(null);

            String name = decoder.parameters()
                    .getOrDefault("name", List.of())
                    .stream()
                    .findFirst()
                    .orElse(null);

            Map<String, Object> result = new HashMap<>();
            try {
                if (id != null && !id.isEmpty()) {
                    if (id.equalsIgnoreCase("all")) {
                        result.put("result", telemetryRegistry.getAllMetrics());
                    } else {
                        result.put("result", List.of(telemetryRegistry.getMetricsById(id)));
                    }
                } else if (name != null && !name.isEmpty()) {
                    result.put("result", telemetryRegistry.getMetricsByName(name));
                } else {
                    result.put("message", "No action peformed");
                }
                result.put("status", "OK");
            } catch (Exception e) {
                log.error("Error {}", e.getCause());
                result.put("status", "FAILED");
                result.put("message", e.getMessage());
            }
            return httpSuccessResponse(result);
        }
    }

    public class HealthCheckHttpHandler implements HttpServiceHandler {

        @Override
        public String path() {
            return "/healthcheck";
        }

        @Override
        public FullHttpResponse handle(FullHttpRequest request, QueryStringDecoder decoder) {
            Map<String, Object> result = new HashMap<>();
            HttpResponseStatus httpResponseStatus = HttpResponseStatus.OK;
            try {
                NodeRole role = adminHttpService.getNodeRole();
                if (SystemBootstrap.isCluster() && role != NodeRole.MASTER) {
                    httpResponseStatus = HttpResponseStatus.UNAUTHORIZED;
                    result.put("status", "UNAUTHORIZED");
                } else {
                    result.put("status", "OK");
                }
                result.put("message", role.name());
            } catch (Exception e) {
                httpResponseStatus = HttpResponseStatus.INTERNAL_SERVER_ERROR;
                log.error("Failed to validate configuration ", e.getCause());
                result.put("status", "FAILED");
                result.put("message", e.getCause().getMessage());
            }

            FullHttpResponse resp = new DefaultFullHttpResponse(
                    HttpVersion.HTTP_1_1,
                    httpResponseStatus,
                    Unpooled.copiedBuffer(JsonUtil.toJson(result), StandardCharsets.UTF_8)
            );

            resp.headers()
                    .set(HttpHeaderNames.CONTENT_TYPE, "text/plain")
                    .setInt(HttpHeaderNames.CONTENT_LENGTH,
                            resp.content().readableBytes());

            return resp;
        }
    }

    public class GetCacheHttpHandler implements HttpServiceHandler {

        @Override
        public String path() {
            return "/count-cache";
        }

        @Override
        public FullHttpResponse handle(FullHttpRequest request, QueryStringDecoder decoder) {
            Map<String, Object> result = new HashMap<>();
            try {
                result.put("result", correlationCacheService.countSize());
                result.put("status", "OK");
            } catch (Exception e) {
                log.error("Failed to reload configuration {}", e.getCause().getMessage());
                result.put("status", "FAILED");
                result.put("message", e.getCause().getMessage());
            }
            FullHttpResponse resp = new DefaultFullHttpResponse(
                    HttpVersion.HTTP_1_1,
                    HttpResponseStatus.OK,
                    Unpooled.copiedBuffer(JsonUtil.toJson(result), StandardCharsets.UTF_8)
            );

            resp.headers()
                    .set(HttpHeaderNames.CONTENT_TYPE, "text/plain")
                    .setInt(HttpHeaderNames.CONTENT_LENGTH,
                            resp.content().readableBytes());

            return resp;
        }
    }

    public FullHttpResponse httpSuccessResponse(Map<String, Object> result) {
        FullHttpResponse resp = new DefaultFullHttpResponse(
                HttpVersion.HTTP_1_1,
                HttpResponseStatus.OK,
                Unpooled.copiedBuffer(JsonUtil.toJson(result), StandardCharsets.UTF_8)
        );

        resp.headers()
                .set(HttpHeaderNames.CONTENT_TYPE, "text/plain")
                .setInt(HttpHeaderNames.CONTENT_LENGTH,
                        resp.content().readableBytes());

        return resp;
    }
}
