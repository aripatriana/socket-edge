package com.socket.edge.http.handler;

import com.socket.edge.utils.JsonUtil;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.http.*;

import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Contract for HTTP admin service endpoints.
 *
 * <p>v3.0 changes:
 * <ul>
 *   <li>Added {@link #method()} for HTTP method filtering (GET/POST)</li>
 *   <li>Added static response builder helpers (no more duplicated code)</li>
 *   <li>Content-Type standardized to {@code application/json}</li>
 * </ul>
 *
 * @author Ari Patriana
 * @since 3.0.0
 */
public interface HttpServiceHandler {

    /**
     * URL path this handler serves (e.g. "/health/live").
     */
    String path();

    /**
     * HTTP method this handler accepts.
     * Default is GET. Override for POST-only endpoints (e.g. reload).
     */
    default HttpMethod method() {
        return HttpMethod.GET;
    }

    /**
     * Handles the HTTP request and returns a response.
     */
    FullHttpResponse handle(FullHttpRequest request, QueryStringDecoder decoder);

    // --- Response Builders ---

    static FullHttpResponse json(HttpResponseStatus status, Map<String, Object> body) {
        byte[] bytes = JsonUtil.toJson(body).getBytes(StandardCharsets.UTF_8);
        FullHttpResponse resp = new DefaultFullHttpResponse(
                HttpVersion.HTTP_1_1, status, Unpooled.wrappedBuffer(bytes));
        resp.headers()
                .set(HttpHeaderNames.CONTENT_TYPE, "application/json")
                .setInt(HttpHeaderNames.CONTENT_LENGTH, bytes.length);
        return resp;
    }

    static FullHttpResponse ok(Map<String, Object> body) {
        return json(HttpResponseStatus.OK, body);
    }

    static FullHttpResponse error(HttpResponseStatus status, String message) {
        return json(status, Map.of("status", "FAILED", "message", message));
    }

    static FullHttpResponse methodNotAllowed() {
        return error(HttpResponseStatus.METHOD_NOT_ALLOWED, "Method not allowed");
    }
}
