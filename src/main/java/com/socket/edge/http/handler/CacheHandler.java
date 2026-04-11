package com.socket.edge.http.handler;

import com.socket.edge.http.service.CorrelationCacheService;
import io.netty.handler.codec.http.*;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * GET /count-cache — correlation cache entry count.
 *
 * @author Ari Patriana
 * @since 3.0.0
 */
public final class CacheHandler implements HttpServiceHandler {

    private final CorrelationCacheService cacheService;

    public CacheHandler(CorrelationCacheService cacheService) {
        this.cacheService = cacheService;
    }

    @Override public String path() { return "/count-cache"; }

    @Override
    public FullHttpResponse handle(FullHttpRequest request, QueryStringDecoder decoder) {
        Map<String, Object> body = new LinkedHashMap<>();
        try {
            body.put("result", cacheService.countSize());
            body.put("status", "OK");
        } catch (Exception e) {
            body.put("status", "FAILED");
            body.put("message", e.getMessage());
        }
        return HttpServiceHandler.ok(body);
    }
}
