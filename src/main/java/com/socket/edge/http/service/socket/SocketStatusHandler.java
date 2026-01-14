package com.socket.edge.http.service.socket;

import com.socket.edge.http.service.admin.HttpServiceHandler;
import com.socket.edge.utils.JsonUtil;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.http.*;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

public class SocketStatusHandler implements HttpServiceHandler {

    private SocketStatusService service;
    public SocketStatusHandler(SocketStatusService service) {
        this.service = service;
    }

    @Override
    public String path() {
        return "/socket/status";
    }

    @Override
    public FullHttpResponse handle(FullHttpRequest req) {
        Map<String, Object> result = new HashMap<>();
        try {
            Map<String, Object> data = new HashMap<>();
            data.put("uptime", (service.uptime()/1000)+"s");
            data.put("socketStatus", service.getSocketStatus());

            result.put("status", "OK");
            result.put("result", data);
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
