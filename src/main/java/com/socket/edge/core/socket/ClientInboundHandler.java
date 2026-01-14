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

public final class ClientInboundHandler
        extends ChannelInboundHandlerAdapter {

    private static final Logger log = LoggerFactory.getLogger(ClientInboundHandler.class);

    private final NettyClientSocket clientSocket;
    private IsoParser isoParser;
    private ForwardService forwardService;
    private SocketTelemetry socketTelemetry;

    public ClientInboundHandler(NettyClientSocket clientSocket, SocketTelemetry socketTelemetry, IsoParser isoParser, ForwardService forwardService) {
        this.clientSocket = clientSocket;
        this.isoParser = isoParser;
        this.forwardService = forwardService;
        this.socketTelemetry = socketTelemetry;
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        super.channelRead(ctx, msg);
        socketTelemetry.onMessage();

        long start = System.nanoTime();
        byte[] rawBytes = (byte[]) msg;

        log.info("{} read {}", clientSocket.getId(), new String(rawBytes));

        Map<String, String> parsedIsoFields = isoParser.parse(rawBytes);
        MessageContext msgCtx = new MessageContext(parsedIsoFields, rawBytes);
        msgCtx.setSocketId(clientSocket.getId());
        msgCtx.setChannelName(clientSocket.getName());
        msgCtx.setChannel(ctx.channel());
        msgCtx.setLocalAddress((InetSocketAddress) ctx.channel().localAddress());
        msgCtx.setRemoteAddress((InetSocketAddress) ctx.channel().remoteAddress());
        msgCtx.setInboundSocketType(SocketType.SOCKET_CLIENT);
        msgCtx.setOutboundSocketType(SocketType.SOCKET_SERVER);
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
        clientSocket.onDisconnect(ctx.channel());
        socketTelemetry.onDisconnect();
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        log.error("{} exception occured {}", clientSocket.getId(), cause);
        ctx.close();
    }
}
