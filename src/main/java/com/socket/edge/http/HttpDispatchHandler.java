package com.socket.edge.http;

import com.socket.edge.http.service.HttpServiceHandler;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.*;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public class HttpDispatchHandler extends SimpleChannelInboundHandler<FullHttpRequest> {

    private final Map<String, HttpServiceHandler> handlers;

    public HttpDispatchHandler(List<HttpServiceHandler> services) {
        this.handlers = services.stream()
                .collect(Collectors.toMap(
                        HttpServiceHandler::path,
                        Function.identity()
                ));
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx,
                                FullHttpRequest req) {

        HttpServiceHandler handler = handlers.get(req.uri());

        if (handler == null) {
            send(ctx, HttpResponseStatus.NOT_FOUND, "NOT_FOUND");
            return;
        }

        FullHttpResponse response = handler.handle(req);
        ctx.writeAndFlush(response)
                .addListener(ChannelFutureListener.CLOSE);
    }

    private void send(ChannelHandlerContext ctx,
                      HttpResponseStatus status,
                      String body) {

        FullHttpResponse resp = new DefaultFullHttpResponse(
                HttpVersion.HTTP_1_1,
                status,
                Unpooled.copiedBuffer(body, StandardCharsets.UTF_8)
        );
        ctx.writeAndFlush(resp)
                .addListener(ChannelFutureListener.CLOSE);
    }
}
