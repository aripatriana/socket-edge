package com.socket.edge.http.service;

import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;

public interface HttpServiceHandler {
    String path();
    FullHttpResponse handle(FullHttpRequest request);
}
