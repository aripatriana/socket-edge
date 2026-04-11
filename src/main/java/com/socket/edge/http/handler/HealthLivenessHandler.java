package com.socket.edge.http.handler;

import io.netty.handler.codec.http.*;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Liveness probe — returns 200 if JVM and engine are alive.
 *
 * @author Ari Patriana
 * @since 3.0.0
 */
public final class HealthLivenessHandler implements HttpServiceHandler {

    private final String path;
    private final long startupTime = System.currentTimeMillis();

    public HealthLivenessHandler(String path) {
        this.path = path;
    }

    @Override
    public String path() {
        return path;
    }

    @Override
    public FullHttpResponse handle(FullHttpRequest request, QueryStringDecoder decoder) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", "UP");
        body.put("uptime", System.currentTimeMillis() - startupTime);
        return HttpServiceHandler.ok(body);
    }
}
