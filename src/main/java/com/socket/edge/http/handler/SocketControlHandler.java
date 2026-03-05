package com.socket.edge.http.handler;

import com.socket.edge.core.TelemetryRegistry;
import com.socket.edge.http.service.AdminHttpService;
import com.socket.edge.utils.JsonUtil;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.http.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SocketControlHandler {
    private static final Logger log = LoggerFactory.getLogger(SocketControlHandler.class);
    private AdminHttpService adminHttpService;
    private TelemetryRegistry telemetryRegistry;

    public SocketControlHandler(AdminHttpService adminHttpService, TelemetryRegistry telemetryRegistry, List<HttpServiceHandler> services) {
        this.adminHttpService = adminHttpService;
        this.telemetryRegistry = telemetryRegistry;
        initialize(services);
    }

    public void initialize(List<HttpServiceHandler> services) {
        services.add(new RestartSocketHttpHandler());
        services.add(new StartSocketHttpHandler());
        services.add(new StopSocketHttpHandler());
        services.add(new StatusSocketHttpHandler());
    }

    public class RestartSocketHttpHandler implements HttpServiceHandler {

        @Override
        public String path () {
            return "/socket/restart";
        }

        @Override
        public FullHttpResponse handle (FullHttpRequest req, QueryStringDecoder decoder){
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
                Map<String, Object> data = new HashMap<>();
                if (id != null && !id.isEmpty()) {
                    if (id.equalsIgnoreCase("all")) {
                        adminHttpService.restartAll();
                    } else {
                        adminHttpService.restartSocketById(id);
                    }
                } else if (name != null && !name.isEmpty()) {
                    adminHttpService.restartSocketByName(name);
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

    public class StartSocketHttpHandler implements HttpServiceHandler {

        @Override
        public String path () {
            return "/socket/start";
        }

        @Override
        public FullHttpResponse handle (FullHttpRequest req, QueryStringDecoder decoder){
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
                Map<String, Object> data = new HashMap<>();
                if (id != null && !id.isEmpty()) {
                    if (id.equalsIgnoreCase("all")) {
                        adminHttpService.startAllSocket();
                    } else {
                        adminHttpService.startSocketById(id);
                    }
                } else if (name != null && !name.isEmpty()) {
                    adminHttpService.startSocketByName(name);
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

    public class StatusSocketHttpHandler implements HttpServiceHandler {

        @Override
        public String path () {
            return "/socket/status";
        }

        @Override
        public FullHttpResponse handle (FullHttpRequest req, QueryStringDecoder decoder){
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
                        result.put("result", telemetryRegistry.getAllRuntimeState());
                    } else {
                        result.put("result", List.of(telemetryRegistry.getRuntimeStateById(id)));
                    }
                } else if (name != null && !name.isEmpty()) {
                    result.put("result", telemetryRegistry.getRuntimeStateByName(name));
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

    public class StopSocketHttpHandler implements HttpServiceHandler {

        @Override
        public String path () {
            return "/socket/stop";
        }

        @Override
        public FullHttpResponse handle (FullHttpRequest req, QueryStringDecoder decoder){
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
                Map<String, Object> data = new HashMap<>();
                if (id != null && !id.isEmpty()) {
                    if (id.equalsIgnoreCase("all")) {
                        adminHttpService.stopAllSocket();
                    } else {
                        adminHttpService.stopSocketById(id);
                    }
                } else if (name != null && !name.isEmpty()) {
                    adminHttpService.stopSocketByName(name);
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
