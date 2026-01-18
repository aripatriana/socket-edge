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

public class SocketRestartHandler implements HttpServiceHandler {

    private static final Logger log = LoggerFactory.getLogger(SocketRestartHandler.class);
    private AdminHttpService adminHttpService;
    public SocketRestartHandler(AdminHttpService adminHttpService) {
        this.adminHttpService = adminHttpService;
    }

    @Override
    public String path() {
        return "/socket/restart";
    }

    @Override
    public FullHttpResponse handle(FullHttpRequest req, QueryStringDecoder decoder) {
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
