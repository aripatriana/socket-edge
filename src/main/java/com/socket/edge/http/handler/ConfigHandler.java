package com.socket.edge.http.handler;

import com.socket.edge.http.service.ReloadCfgService;
import com.socket.edge.model.helper.MetadataDiff;
import io.netty.handler.codec.http.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Configuration management handlers.
 *
 * <p>v3.0: Reload requires POST method (mutating operation).
 * Validate remains GET (read-only).</p>
 *
 * @author Ari Patriana
 * @since 3.0.0
 */
public final class ConfigHandler {

    private ConfigHandler() {}

    /**
     * POST /config/reload — hot-reload channel.conf.
     */
    public static final class ReloadHandler implements HttpServiceHandler {
        private static final Logger log = LoggerFactory.getLogger(ReloadHandler.class);
        private final ReloadCfgService reloadCfgService;

        public ReloadHandler(ReloadCfgService reloadCfgService) {
            this.reloadCfgService = reloadCfgService;
        }

        @Override public String path() { return "/config/reload"; }
        @Override public HttpMethod method() { return HttpMethod.POST; }

        @Override
        public FullHttpResponse handle(FullHttpRequest request, QueryStringDecoder decoder) {
            Map<String, Object> body = new LinkedHashMap<>();
            try {
                reloadCfgService.reload();
                body.put("status", "OK");
            } catch (Exception e) {
                log.error("Failed to reload configuration", e);
                body.put("status", "FAILED");
                body.put("message", e.getMessage());
            }
            return HttpServiceHandler.ok(body);
        }
    }

    /**
     * GET /config/validate — dry-run validate channel.conf changes.
     */
    public static final class ValidateHandler implements HttpServiceHandler {
        private static final Logger log = LoggerFactory.getLogger(ValidateHandler.class);
        private final ReloadCfgService reloadCfgService;

        public ValidateHandler(ReloadCfgService reloadCfgService) {
            this.reloadCfgService = reloadCfgService;
        }

        @Override public String path() { return "/config/validate"; }

        @Override
        public FullHttpResponse handle(FullHttpRequest request, QueryStringDecoder decoder) {
            Map<String, Object> body = new LinkedHashMap<>();
            try {
                MetadataDiff md = reloadCfgService.validate();
                body.put("status", "OK");
                body.put("hasChanges", md.hasChanges());
                if (md.hasChanges()) {
                    body.put("changes", md.toString(new StringBuffer()).toString());
                }
            } catch (Exception e) {
                log.error("Failed to validate configuration", e);
                body.put("status", "FAILED");
                body.put("message", e.getMessage());
            }
            return HttpServiceHandler.ok(body);
        }
    }
}
