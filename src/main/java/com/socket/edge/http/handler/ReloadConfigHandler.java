package com.socket.edge.http.handler;

import com.socket.edge.http.service.ReloadCfgService;
import com.socket.edge.utils.JsonUtil;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.http.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

public class ReloadConfigHandler implements HttpServiceHandler {

    private static final Logger log = LoggerFactory.getLogger(ReloadConfigHandler.class);
    private ReloadCfgService reloadCfgService;
    public ReloadConfigHandler(ReloadCfgService reloadCfgService) {
        this.reloadCfgService = reloadCfgService;
    }

    @Override
    public String path() {
        return "/config/reload";
    }

    @Override
    public FullHttpResponse handle(FullHttpRequest request, QueryStringDecoder decoder) {
        Map<String, Object> result = new HashMap<>();
        try {
            reloadCfgService.reload();
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
