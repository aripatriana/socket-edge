package com.socket.edge.core.socket;

import com.socket.edge.core.ForwardService;
import com.socket.edge.core.MessageContext;
import com.socket.edge.core.SocketTelemetry;
import com.socket.edge.model.SocketType;
import com.socket.edge.utils.IsoParser;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.util.Map;

public final class ServerInboundHandler
        extends ChannelInboundHandlerAdapter {

    private static final Logger log = LoggerFactory.getLogger(ServerInboundHandler.class);

    private final NettyServerSocket serverSocket;
    private final IsoParser isoParser;
    private final ForwardService forwardService;
    private final SocketTelemetry socketTelemetry;

    public ServerInboundHandler(NettyServerSocket serverSocket, SocketTelemetry socketTelemetry, IsoParser isoParser, ForwardService forwardService) {
        this.serverSocket = serverSocket;
        this.isoParser = isoParser;
        this.forwardService = forwardService;
        this.socketTelemetry = socketTelemetry;
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        super.channelRead(ctx, msg);
        long start = System.nanoTime();
        socketTelemetry.onMessage();

        byte[] rawBytes = (byte[]) msg;

        log.info("{} read {}", serverSocket.getId(), new String(rawBytes));

        Map<String, String> parsedIsoFields = isoParser.parse(rawBytes);
        MessageContext msgCtx = new MessageContext(parsedIsoFields, rawBytes);
        msgCtx.setSocketId(serverSocket.getId());
        msgCtx.setChannelName(serverSocket.getName());
        msgCtx.setChannel(ctx.channel());
        msgCtx.setLocalAddress((InetSocketAddress) ctx.channel().localAddress());
        msgCtx.setRemoteAddress((InetSocketAddress) ctx.channel().remoteAddress());
        msgCtx.setInboundSocketType(SocketType.SOCKET_SERVER);
        msgCtx.setOutboundSocketType(SocketType.SOCKET_CLIENT);
        msgCtx.addProperty("received_time", start);
        msgCtx.setMetricsCounter(socketTelemetry);

        forwardService.forward(msgCtx);
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        super.channelActive(ctx);
        socketTelemetry.onConnect();
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        super.channelInactive(ctx);
        socketTelemetry.onDisconnect();
    }

    @Override
    public void exceptionCaught(
            ChannelHandlerContext ctx,
            Throwable cause
    ) {
        log.error("{} exception occured {}", serverSocket.getId(), cause);
        serverSocket.channelPool().unregister(ctx.channel());
        ctx.close();
    }
}
