package com.socket.edge.http.handler;

import com.socket.edge.http.service.AdminHttpService;
import io.netty.handler.codec.http.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Socket lifecycle control handlers: start, stop, restart.
 *
 * <p>v3.0: Mutating operations require POST method.
 * Each handler is a standalone class registered individually.</p>
 *
 * @author Ari Patriana
 * @since 3.0.0
 */
public final class SocketControlHandler {

    private SocketControlHandler() {}

    /**
     * POST /socket/start — start socket by id or name.
     */
    public static final class StartHandler implements HttpServiceHandler {
        private static final Logger log = LoggerFactory.getLogger(StartHandler.class);
        private final AdminHttpService adminService;

        public StartHandler(AdminHttpService adminService) {
            this.adminService = adminService;
        }

        @Override public String path() { return "/socket/start"; }
        @Override public HttpMethod method() { return HttpMethod.POST; }

        @Override
        public FullHttpResponse handle(FullHttpRequest request, QueryStringDecoder decoder) {
            String id = param(decoder, "id");
            String name = param(decoder, "name");
            Map<String, Object> body = new LinkedHashMap<>();
            try {
                if (id != null) {
                    if ("all".equalsIgnoreCase(id)) adminService.startAllSocket();
                    else adminService.startSocketById(id);
                } else if (name != null) {
                    adminService.startSocketByName(name);
                } else {
                    body.put("message", "Provide ?id= or ?name= parameter");
                }
                body.put("status", "OK");
            } catch (Exception e) {
                log.error("Start failed", e);
                body.put("status", "FAILED");
                body.put("message", e.getMessage());
            }
            return HttpServiceHandler.ok(body);
        }
    }

    /**
     * POST /socket/stop — stop socket by id or name.
     */
    public static final class StopHandler implements HttpServiceHandler {
        private static final Logger log = LoggerFactory.getLogger(StopHandler.class);
        private final AdminHttpService adminService;

        public StopHandler(AdminHttpService adminService) {
            this.adminService = adminService;
        }

        @Override public String path() { return "/socket/stop"; }
        @Override public HttpMethod method() { return HttpMethod.POST; }

        @Override
        public FullHttpResponse handle(FullHttpRequest request, QueryStringDecoder decoder) {
            String id = param(decoder, "id");
            String name = param(decoder, "name");
            Map<String, Object> body = new LinkedHashMap<>();
            try {
                if (id != null) {
                    if ("all".equalsIgnoreCase(id)) adminService.stopAllSocket();
                    else adminService.stopSocketById(id);
                } else if (name != null) {
                    adminService.stopSocketByName(name);
                } else {
                    body.put("message", "Provide ?id= or ?name= parameter");
                }
                body.put("status", "OK");
            } catch (Exception e) {
                log.error("Stop failed", e);
                body.put("status", "FAILED");
                body.put("message", e.getMessage());
            }
            return HttpServiceHandler.ok(body);
        }
    }

    /**
     * POST /socket/restart — restart socket by id or name.
     */
    public static final class RestartHandler implements HttpServiceHandler {
        private static final Logger log = LoggerFactory.getLogger(RestartHandler.class);
        private final AdminHttpService adminService;

        public RestartHandler(AdminHttpService adminService) {
            this.adminService = adminService;
        }

        @Override public String path() { return "/socket/restart"; }
        @Override public HttpMethod method() { return HttpMethod.POST; }

        @Override
        public FullHttpResponse handle(FullHttpRequest request, QueryStringDecoder decoder) {
            String id = param(decoder, "id");
            String name = param(decoder, "name");
            Map<String, Object> body = new LinkedHashMap<>();
            try {
                if (id != null) {
                    if ("all".equalsIgnoreCase(id)) adminService.restartAll();
                    else adminService.restartSocketById(id);
                } else if (name != null) {
                    adminService.restartSocketByName(name);
                } else {
                    body.put("message", "Provide ?id= or ?name= parameter");
                }
                body.put("status", "OK");
            } catch (Exception e) {
                log.error("Restart failed", e);
                body.put("status", "FAILED");
                body.put("message", e.getMessage());
            }
            return HttpServiceHandler.ok(body);
        }
    }

    private static String param(QueryStringDecoder d, String key) {
        return d.parameters().getOrDefault(key, List.of()).stream().findFirst().orElse(null);
    }
}
