package com.socket.edge.http.handler;

import com.socket.edge.core.TelemetryRegistry;
import io.netty.handler.codec.http.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * GET /socket/queues — message queue depths per socket.
 *
 * @author Ari Patriana
 * @since 3.0.0
 */
public final class QueueHandler implements HttpServiceHandler {

    private static final Logger log = LoggerFactory.getLogger(QueueHandler.class);
    private final TelemetryRegistry telemetryRegistry;

    public QueueHandler(TelemetryRegistry telemetryRegistry) {
        this.telemetryRegistry = telemetryRegistry;
    }

    @Override public String path() { return "/socket/queues"; }

    @Override
    public FullHttpResponse handle(FullHttpRequest request, QueryStringDecoder decoder) {
        String id = param(decoder, "id");
        String name = param(decoder, "name");
        Map<String, Object> body = new LinkedHashMap<>();
        try {
            if (id != null) {
                body.put("result", "all".equalsIgnoreCase(id)
                        ? telemetryRegistry.getAllQueue()
                        : List.of(telemetryRegistry.getQueueById(id)));
            } else if (name != null) {
                body.put("result", telemetryRegistry.getQueueByName(name));
            }
            body.put("status", "OK");
        } catch (Exception e) {
            log.error("Queue query failed", e);
            body.put("status", "FAILED");
            body.put("message", e.getMessage());
        }
        return HttpServiceHandler.ok(body);
    }

    private static String param(QueryStringDecoder d, String key) {
        return d.parameters().getOrDefault(key, List.of()).stream().findFirst().orElse(null);
    }
}
