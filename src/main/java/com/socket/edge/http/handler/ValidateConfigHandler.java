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
import java.util.Map;

public class ValidateConfigHandler implements HttpServiceHandler {

    private static final Logger log = LoggerFactory.getLogger(MetricsServiceHandle.class);
    private ReloadCfgService reloadCfgService;
    public ValidateConfigHandler(ReloadCfgService reloadCfgService) {
        this.reloadCfgService = reloadCfgService;
    }

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
