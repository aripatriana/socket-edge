package com.socket.edge.http.service;

import com.socket.edge.utils.JsonUtil;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.http.*;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

public class SocketInfoHandler implements HttpServiceHandler {

    private SocketInfoService service;
    public SocketInfoHandler(SocketInfoService service) {
        this.service = service;
    }

    @Override
    public String path() {
        return "/info";
    }

    @Override
    public FullHttpResponse handle(FullHttpRequest req) {
        Map<String, Object> info = new HashMap<>();
        info.put("uptime", service.uptimeSeconds()+"s");
        info.put("socketInfo", service.socketInfo());

        FullHttpResponse resp = new DefaultFullHttpResponse(
                HttpVersion.HTTP_1_1,
                HttpResponseStatus.OK,
                Unpooled.copiedBuffer(JsonUtil.toJson(info), StandardCharsets.UTF_8)
        );

        resp.headers()
                .set(HttpHeaderNames.CONTENT_TYPE, "text/plain")
                .setInt(HttpHeaderNames.CONTENT_LENGTH,
                        resp.content().readableBytes());

        return resp;
    }
}
