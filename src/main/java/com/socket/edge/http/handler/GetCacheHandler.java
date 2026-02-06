package com.socket.edge.http.handler;

import com.socket.edge.http.service.CorrelationCacheService;
import com.socket.edge.utils.JsonUtil;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.http.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

public class GetCacheHandler implements HttpServiceHandler {

    private static final Logger log = LoggerFactory.getLogger(GetCacheHandler.class);

    private CorrelationCacheService correlationCacheService;
    public GetCacheHandler(CorrelationCacheService correlationCacheService) {
        this.correlationCacheService = correlationCacheService;
    }

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
