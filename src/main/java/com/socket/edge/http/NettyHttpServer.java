package com.socket.edge.http;

import com.socket.edge.core.SystemConfig;
import com.socket.edge.http.handler.HttpServiceHandler;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.*;
import io.netty.handler.timeout.IdleStateEvent;
import io.netty.handler.timeout.IdleStateHandler;
import io.netty.util.concurrent.DefaultThreadFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Embedded HTTP server for admin and monitoring endpoints.
 *
 * <p>v3.0 additions:
 * <ul>
 *   <li>Configurable bind address (restrict to localhost or management VLAN)</li>
 *   <li>Idle connection timeout via {@link IdleStateHandler}</li>
 *   <li>HTTP Basic Authentication support</li>
 * </ul>
 *
 * @author Ari Patriana
 * @since 3.0.0
 */
public class NettyHttpServer {

    private static final Logger log = LoggerFactory.getLogger(NettyHttpServer.class);

    private final String name;
    private final SystemConfig.HttpConfig httpConfig;
    private final List<HttpServiceHandler> services;
    private EventLoopGroup boss;
    private EventLoopGroup worker;

    public NettyHttpServer(String name, SystemConfig.HttpConfig httpConfig,
                           List<HttpServiceHandler> services) {
        this.name = name;
        this.httpConfig = httpConfig;
        this.services = services;
    }

    public void start() throws InterruptedException {
        boss = new NioEventLoopGroup(1,
                new DefaultThreadFactory(name + "-http-boss"));
        worker = new NioEventLoopGroup(1,
                new DefaultThreadFactory(name + "-http-worker"));

        int idleSeconds = httpConfig.idleTimeout() / 1000;

        ServerBootstrap bootstrap = new ServerBootstrap();
        bootstrap.group(boss, worker)
                .channel(NioServerSocketChannel.class)
                .childHandler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) {
                        ChannelPipeline p = ch.pipeline();

                        // Idle timeout
                        if (idleSeconds > 0) {
                            p.addLast(new IdleStateHandler(0, 0, idleSeconds, TimeUnit.SECONDS));
                            p.addLast(new IdleCloseHandler());
                        }

                        p.addLast(new HttpServerCodec());
                        p.addLast(new HttpObjectAggregator(8 * 1024));

                        // Basic auth
                        if ("basic".equalsIgnoreCase(httpConfig.authMode())) {
                            p.addLast(new BasicAuthHandler(
                                    httpConfig.authUsername(),
                                    httpConfig.authPassword()));
                        }

                        p.addLast(new HttpDispatchHandler(services));
                    }
                });

        // Bind to configured address
        String bindAddr = httpConfig.bindAddress();
        bootstrap.bind(bindAddr, httpConfig.port()).sync();
        log.info("HTTP server [{}] listening on {}:{}", name, bindAddr, httpConfig.port());
    }

    public void stop() {
        if (boss != null) boss.shutdownGracefully();
        if (worker != null) worker.shutdownGracefully();
    }

    /**
     * Closes idle connections.
     */
    private static class IdleCloseHandler extends ChannelInboundHandlerAdapter {
        @Override
        public void userEventTriggered(ChannelHandlerContext ctx, Object evt) {
            if (evt instanceof IdleStateEvent) {
                log.debug("HTTP connection idle, closing: {}", ctx.channel().remoteAddress());
                ctx.close();
            }
        }
    }

    /**
     * HTTP Basic Authentication handler.
     */
    private static class BasicAuthHandler extends ChannelInboundHandlerAdapter {

        private final String expectedCredentials;

        BasicAuthHandler(String username, String password) {
            this.expectedCredentials = Base64.getEncoder()
                    .encodeToString((username + ":" + password).getBytes(StandardCharsets.UTF_8));
        }

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) {
            if (!(msg instanceof FullHttpRequest request)) {
                ctx.fireChannelRead(msg);
                return;
            }

            String authHeader = request.headers().get(HttpHeaderNames.AUTHORIZATION);

            if (authHeader == null || !authHeader.startsWith("Basic ")) {
                sendUnauthorized(ctx);
                request.release();
                return;
            }

            String credentials = authHeader.substring(6).trim();
            if (!expectedCredentials.equals(credentials)) {
                sendUnauthorized(ctx);
                request.release();
                return;
            }

            ctx.fireChannelRead(msg);
        }

        private void sendUnauthorized(ChannelHandlerContext ctx) {
            FullHttpResponse response = new DefaultFullHttpResponse(
                    HttpVersion.HTTP_1_1,
                    HttpResponseStatus.UNAUTHORIZED,
                    Unpooled.copiedBuffer("Unauthorized", StandardCharsets.UTF_8)
            );
            response.headers()
                    .set(HttpHeaderNames.WWW_AUTHENTICATE, "Basic realm=\"Socket Edge Admin\"")
                    .set(HttpHeaderNames.CONTENT_TYPE, "text/plain")
                    .setInt(HttpHeaderNames.CONTENT_LENGTH, response.content().readableBytes());

            ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
        }
    }
}
