package com.socket.edge.core.socket;

import com.socket.edge.core.ForwardService;
import com.socket.edge.core.MessageContext;
import com.socket.edge.core.SocketTelemetry;
import com.socket.edge.constant.SocketType;
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
        long start = System.nanoTime();
        socketTelemetry.onMessage();

        try {
            if (!(msg instanceof byte[] rawBytes)) {
                log.warn("Unsupported message type: {}", msg.getClass());
            }

            byte[] rawBytes = (byte[]) msg;
            if (log.isDebugEnabled()) {
                log.debug("{} read {}", serverSocket.getId(), new String(rawBytes));
            }

            Map<String, String> parsedIsoFields = isoParser.parse(rawBytes);
            MessageContext msgCtx = new MessageContext(parsedIsoFields, rawBytes);
            msgCtx.setSocketId(serverSocket.getId());
            msgCtx.setChannelName(serverSocket.getName());
            msgCtx.setChannel(ctx.channel());
            msgCtx.setLocalAddress((InetSocketAddress) ctx.channel().localAddress());
            msgCtx.setRemoteAddress((InetSocketAddress) ctx.channel().remoteAddress());
            msgCtx.setInboundType(SocketType.SERVER);
            msgCtx.setOutboundType(SocketType.CLIENT);
            msgCtx.addProperty("receivedTimeNs", start);
            msgCtx.setSocketTelemetry(socketTelemetry);

            var socketChannel = serverSocket.channelPool().get(ctx.channel());
            if (socketChannel == null) {
                log.warn("SocketChannel not found for {}", ctx.channel().id());
                return;
            }
            msgCtx.setSocketChannel(socketChannel);

            forwardService.forward(msgCtx);
        } catch (Exception e) {
            log.error("{} error read message: {}", serverSocket.getId(), e.getMessage());
            socketTelemetry.onError();
        }
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
        // TODO harus ada handling proper
        log.error("{} exception occured {}", serverSocket.getId(), cause);
        serverSocket.channelPool().removeChannel(ctx.channel());
        ctx.close();
    }
}
