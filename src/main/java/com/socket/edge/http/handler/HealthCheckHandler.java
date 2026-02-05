package com.socket.edge.http.handler;

import com.socket.edge.SystemBootstrap;
import com.socket.edge.constant.NodeRole;
import com.socket.edge.http.service.AdminHttpService;
import com.socket.edge.utils.JsonUtil;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.http.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

public class HealthCheckHandler implements HttpServiceHandler {

    private static final Logger log = LoggerFactory.getLogger(MetricsServiceHandle.class);
    private AdminHttpService adminHttpService;
    public HealthCheckHandler(AdminHttpService adminHttpService) {
        this.adminHttpService = adminHttpService;
    }

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
