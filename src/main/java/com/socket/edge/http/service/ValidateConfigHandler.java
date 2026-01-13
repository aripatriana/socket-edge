package com.socket.edge.http.service;

import com.socket.edge.utils.JsonUtil;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.http.*;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

public class ValidateConfigHandler implements HttpServiceHandler {

    private AdminHttpService service;
    public ValidateConfigHandler(AdminHttpService service) {
        this.service = service;
    }

    @Override
    public String path() {
        return "/config/validate";
    }

    @Override
    public FullHttpResponse handle(FullHttpRequest request) {
        Map<String, Object> result = new HashMap<>();
        try {
            service.validate();
            result.put("status", "OK");
        } catch (Exception e) {
            result.put("status", "FAILED");
            result.put("message", e.getMessage());
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
