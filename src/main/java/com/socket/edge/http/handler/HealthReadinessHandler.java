package com.socket.edge.http.handler;

import com.socket.edge.constant.SocketState;
import com.socket.edge.core.socket.AbstractSocket;
import com.socket.edge.core.socket.SocketManager;
import io.netty.handler.codec.http.*;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Readiness probe — returns 200 only when at least one socket
 * is ACTIVE and startup probe delay has passed.
 *
 * @author Ari Patriana
 * @since 3.0.0
 */
public final class HealthReadinessHandler implements HttpServiceHandler {

    private final String path;
    private final SocketManager socketManager;
    private final long startupProbeDelay;
    private final long startupTime = System.currentTimeMillis();

    public HealthReadinessHandler(String path, SocketManager socketManager, long startupProbeDelay) {
        this.path = path;
        this.socketManager = socketManager;
        this.startupProbeDelay = startupProbeDelay;
    }

    @Override
    public String path() {
        return path;
    }

    @Override
    public FullHttpResponse handle(FullHttpRequest request, QueryStringDecoder decoder) {
        Map<String, Object> body = new LinkedHashMap<>();

        // Startup probe delay
        long elapsed = System.currentTimeMillis() - startupTime;
        if (elapsed < startupProbeDelay) {
            body.put("status", "STARTING");
            body.put("message", "Startup probe delay: " + (startupProbeDelay - elapsed) + "ms remaining");
            return HttpServiceHandler.json(HttpResponseStatus.SERVICE_UNAVAILABLE, body);
        }

        // Check if any socket is ACTIVE or LISTEN
        boolean anyActive = socketManager.getSockets().stream()
                .anyMatch(s -> s.getState() == SocketState.ACTIVE
                        || s.getState() == SocketState.LISTEN);

        if (anyActive) {
            body.put("status", "READY");
            Map<String, String> sockets = new LinkedHashMap<>();
            socketManager.getSockets().forEach(s -> sockets.put(s.getId(), s.getState().name()));
            body.put("sockets", sockets);
            return HttpServiceHandler.ok(body);
        } else {
            body.put("status", "NOT_READY");
            body.put("message", "No active sockets");
            return HttpServiceHandler.json(HttpResponseStatus.SERVICE_UNAVAILABLE, body);
        }
    }
}
