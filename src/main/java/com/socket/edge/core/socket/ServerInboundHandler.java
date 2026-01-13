package com.socket.edge.core.socket;

import com.socket.edge.core.ForwardService;
import com.socket.edge.core.MessageContext;
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

    public ServerInboundHandler(NettyServerSocket serverSocket, IsoParser isoParser, ForwardService forwardService) {
        this.serverSocket = serverSocket;
        this.isoParser = isoParser;
        this.forwardService = forwardService;
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        super.channelRead(ctx, msg);
        long start = System.currentTimeMillis();

        byte[] rawBytes = (byte[]) msg;

        log.info("{} read {}", serverSocket.getId(), new String(rawBytes));

        Map<String, String> parsedIsoFields = isoParser.parse(rawBytes);
        MessageContext msgCtx = new MessageContext(parsedIsoFields, rawBytes);
        msgCtx.setChannel(ctx.channel());
        msgCtx.setLocalAddress((InetSocketAddress) ctx.channel().localAddress());
        msgCtx.setRemoteAddress((InetSocketAddress) ctx.channel().remoteAddress());
        msgCtx.setSocketType(SocketType.SOCKET_SERVER);
        msgCtx.addProperty("received_time", start);

        forwardService.forward(msgCtx);
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
