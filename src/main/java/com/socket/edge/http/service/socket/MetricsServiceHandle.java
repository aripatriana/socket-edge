package com.socket.edge.http.service.socket;

import com.socket.edge.http.service.admin.HttpServiceHandler;
import com.socket.edge.utils.JsonUtil;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.http.*;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

public class MetricsServiceHandle implements HttpServiceHandler {

    private MetricsService metricsService;

    public MetricsServiceHandle(MetricsService metricsService) {
        this.metricsService = metricsService;
    }

    @Override
    public String path() {
        return "/socket/metrics";
    }

    @Override
    public FullHttpResponse handle(FullHttpRequest request) {
        Map<String, Object> result = new HashMap<>();
        try {
            result.put("result", metricsService.getAllSnapshot());
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
