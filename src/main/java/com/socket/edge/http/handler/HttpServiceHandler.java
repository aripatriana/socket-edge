package com.socket.edge.http.handler;

import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.QueryStringDecoder;

public interface HttpServiceHandler {
    String path();
    FullHttpResponse handle(FullHttpRequest request, QueryStringDecoder decoder);
}
