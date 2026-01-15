package com.socket.edge.http.handler;

import com.socket.edge.core.TelemetryRegistry;
import com.socket.edge.http.service.AdminHttpService;
import com.socket.edge.utils.JsonUtil;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.http.*;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SocketStopHandler implements HttpServiceHandler {

    private AdminHttpService adminHttpService;
    public SocketStopHandler(AdminHttpService adminHttpService) {
        this.adminHttpService = adminHttpService;
    }

    @Override
    public String path() {
        return "/socket/stop";
    }

    @Override
    public FullHttpResponse handle(FullHttpRequest req) {
        QueryStringDecoder decoder = new QueryStringDecoder(req.uri());

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
