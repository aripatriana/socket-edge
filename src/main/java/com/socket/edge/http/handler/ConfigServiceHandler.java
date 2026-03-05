package com.socket.edge.http.handler;

import com.socket.edge.http.service.ReloadCfgService;
import com.socket.edge.model.helper.MetadataDiff;
import com.socket.edge.utils.JsonUtil;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.http.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ConfigServiceHandler {
    private static final Logger log = LoggerFactory.getLogger(ConfigServiceHandler.class);

    private ReloadCfgService reloadCfgService;
    public ConfigServiceHandler(ReloadCfgService reloadCfgService, List<HttpServiceHandler> services) {
        this.reloadCfgService = reloadCfgService;
        initialize(services);
    }

    public void initialize(List<HttpServiceHandler> services) {
        services.add(new ReloadConfigHttpHandler());
        services.add(new ValidateConfigHttpHandler());
    }

    public class ReloadConfigHttpHandler implements HttpServiceHandler {

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

            return httpSuccessResponse(result);
        }
    }

    public class ValidateConfigHttpHandler implements HttpServiceHandler {

        @Override
        public String path() {
            return "/config/validate";
        }

        @Override
        public FullHttpResponse handle(FullHttpRequest request, QueryStringDecoder decoder) {
            Map<String, Object> result = new HashMap<>();
            try {
                MetadataDiff md = reloadCfgService.validate();
                result.put("status", "OK");
                result.put("message", md.hasChanges() ? md.toString(new StringBuffer()) : "No changes detected");
            } catch (Exception e) {
                log.error("Failed to validate configuration ", e.getCause());
                result.put("status", "FAILED");
                result.put("message", e.getCause().getMessage());
            }

            return httpSuccessResponse(result);
        }
    }

    public FullHttpResponse httpSuccessResponse(Map<String, Object> result) {
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
