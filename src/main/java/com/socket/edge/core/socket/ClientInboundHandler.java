package com.socket.edge.core.socket;

import com.socket.edge.constant.SocketState;
import com.socket.edge.core.MessageContextProcess;
import com.socket.edge.core.MessageContext;
import com.socket.edge.constant.SocketType;
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

    private SocketManager sm;
    private final DefaultClientSocket clientSocket;
    private IsoParser isoParser;
    private MessageContextProcess messageContextProcess;

    public ClientInboundHandler(SocketManager sm, DefaultClientSocket clientSocket, IsoParser isoParser, MessageContextProcess messageContextProcess) {
        this.sm = sm;
        this.clientSocket = clientSocket;
        this.isoParser = isoParser;
        this.messageContextProcess = messageContextProcess;
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        long start = System.nanoTime();

        var socketChannel = clientSocket.channelPool().getChannel(ctx.channel());
        if (socketChannel == null) {
            log.warn("Skip channelRead: SocketChannel not found for {}", ctx.channel().id());
            ctx.channel().close();
            return;
        }

        try {
            socketChannel.onMessage();

            if (!(msg instanceof byte[])) {
                log.warn("Unsupported message type: {}", msg.getClass());
            }

            byte[] rawBytes = (byte[]) msg;
            if (log.isInfoEnabled()) {
                log.info("{} read {}", clientSocket.getId(), new String(rawBytes));
            }

            Map<String, String> parsedIsoFields = isoParser.parse(rawBytes);
            MessageContext msgCtx = new MessageContext(parsedIsoFields, rawBytes);
            msgCtx.setSocketId(clientSocket.getId());
            msgCtx.setChannelName(clientSocket.getName());
            msgCtx.setChannel(ctx.channel());
            msgCtx.setLocalAddress((InetSocketAddress) ctx.channel().localAddress());
            msgCtx.setRemoteAddress((InetSocketAddress) ctx.channel().remoteAddress());
            msgCtx.setInboundType(SocketType.CLIENT);
            msgCtx.setOutboundType(SocketType.SERVER);
            msgCtx.addProperty("receivedTimeNs", start);
            msgCtx.setSocketChannel(socketChannel);

            messageContextProcess.process(msgCtx);
        } catch (Exception e) {
            log.error("{} error read message: {}", clientSocket.getId(), e.getMessage());
            socketChannel.onError();
        }
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        super.channelActive(ctx);
        var socketChannel = clientSocket.channelPool().getChannel(ctx.channel());
        if (socketChannel == null) {
            log.warn("Skip channelActive: SocketChannel not found for {}", ctx.channel().id());
            ctx.channel().close();
            return;
        }
        clientSocket.changeState(SocketState.ACTIVE);
        socketChannel.onConnect();
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        super.channelInactive(ctx);

        var socketChannel = clientSocket.channelPool().getChannel(ctx.channel());
        if (socketChannel == null) {
            log.warn("Skip channelInactive: SocketChannel not found for {}", ctx.channel().id());
            return;
        }

        AbstractSocket socketServer = sm.getSocketServerByName(clientSocket.getName());
        if (socketServer == null) {
            log.warn("Skip channelActive: No server socket found for client socket {}", clientSocket.getId());
            clientSocket.restart();
            return;
        }

        int totalAvailableChannels = socketServer.channelPool().availableChannels().size();
        if (totalAvailableChannels == 0) {
            log.warn("No available channels in server socket {} for client socket {}", socketServer.getId(), clientSocket.getId());
            clientSocket.restart();
        } else {
            clientSocket.scheduleReconnect();
        }
        socketChannel.onDisconnect();
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        log.error("{} exception occured {}", clientSocket.getId(), cause);
        ctx.close();
    }
}
