package com.socket.edge.http;

import com.socket.edge.http.handler.HttpServiceHandler;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.*;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Dispatches HTTP requests to registered {@link HttpServiceHandler}s.
 *
 * <p>v3.0: Routes keyed by {@code METHOD|PATH} to support
 * same path with different methods (e.g. GET /config vs POST /config/reload).</p>
 *
 * @author Ari Patriana
 * @since 3.0.0
 */
public class HttpDispatchHandler extends SimpleChannelInboundHandler<FullHttpRequest> {

    private final Map<String, HttpServiceHandler> handlers = new ConcurrentHashMap<>();

    public HttpDispatchHandler(List<HttpServiceHandler> services) {
        for (HttpServiceHandler handler : services) {
            String key = routeKey(handler.method(), handler.path());
            handlers.put(key, handler);
        }
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, FullHttpRequest req) {
        QueryStringDecoder decoder = new QueryStringDecoder(req.uri());
        String path = decoder.path();
        HttpMethod method = req.method();

        // Try exact method match first
        HttpServiceHandler handler = handlers.get(routeKey(method, path));

        // Fallback: if GET handler exists but request is HEAD, use GET handler
        if (handler == null && method == HttpMethod.HEAD) {
            handler = handlers.get(routeKey(HttpMethod.GET, path));
        }

        if (handler == null) {
            // Check if path exists with different method → 405
            boolean pathExists = handlers.keySet().stream()
                    .anyMatch(k -> k.endsWith("|" + path));

            if (pathExists) {
                send(ctx, HttpResponseStatus.METHOD_NOT_ALLOWED, "Method not allowed");
            } else {
                send(ctx, HttpResponseStatus.NOT_FOUND, "Not found");
            }
            return;
        }

        FullHttpResponse response = handler.handle(req, decoder);
        ctx.writeAndFlush(response)
                .addListener(ChannelFutureListener.CLOSE);
    }

    private static String routeKey(HttpMethod method, String path) {
        return method.name() + "|" + path;
    }

    private void send(ChannelHandlerContext ctx, HttpResponseStatus status, String body) {
        FullHttpResponse resp = new DefaultFullHttpResponse(
                HttpVersion.HTTP_1_1, status,
                Unpooled.copiedBuffer(body, StandardCharsets.UTF_8));
        resp.headers()
                .set(HttpHeaderNames.CONTENT_TYPE, "text/plain")
                .setInt(HttpHeaderNames.CONTENT_LENGTH, resp.content().readableBytes());
        ctx.writeAndFlush(resp)
                .addListener(ChannelFutureListener.CLOSE);
    }
}
