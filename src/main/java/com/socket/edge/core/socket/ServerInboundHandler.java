package com.socket.edge.core.socket;

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

/**
 * Server-side Netty inbound handler for ISO 8583 message processing.
 *
 * <p>v3.0 changes:
 * <ul>
 *   <li>Removed {@code SocketManager} dependency — no more circular dependency</li>
 *   <li>Lifecycle orchestration delegated to {@link SocketLifecycleCoordinator}</li>
 *   <li>Fixed: dead code in channelRead (pattern matching + explicit cast)</li>
 *   <li>Fixed: PCI-DSS compliant logging (no raw message content)</li>
 *   <li>Fixed: NPE guard on getSocketClientByName return value</li>
 * </ul>
 *
 * @author Ari Patriana
 * @since 3.0.0
 */
public final class ServerInboundHandler extends ChannelInboundHandlerAdapter {

    private static final Logger log = LoggerFactory.getLogger(ServerInboundHandler.class);

    private final DefaultServerSocket serverSocket;
    private final IsoParser isoParser;
    private final MessageContextProcess messageContextProcess;
    private final SocketLifecycleCoordinator coordinator;

    public ServerInboundHandler(
            DefaultServerSocket serverSocket,
            IsoParser isoParser,
            MessageContextProcess messageContextProcess,
            SocketLifecycleCoordinator coordinator
    ) {
        this.serverSocket = serverSocket;
        this.isoParser = isoParser;
        this.messageContextProcess = messageContextProcess;
        this.coordinator = coordinator;
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        long start = System.nanoTime();

        var socketChannel = serverSocket.channelPool().getChannel(ctx.channel());
        if (socketChannel == null) {
            log.warn("Skip channelRead: SocketChannel not found for {}", ctx.channel().id());
            return;
        }

        // v3.0 FIX: proper pattern matching with early return
        if (!(msg instanceof byte[] rawBytes)) {
            log.warn("Unsupported message type: {}", msg.getClass());
            return;
        }

        try {
            socketChannel.onMessage();

            // v3.0 FIX: PCI-DSS safe logging — no raw message content
            if (log.isDebugEnabled()) {
                log.debug("{} received {} bytes", serverSocket.getId(), rawBytes.length);
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
            msgCtx.setSocketChannel(socketChannel);

            messageContextProcess.process(msgCtx);
        } catch (Exception e) {
            log.error("{} error read message: {}", serverSocket.getId(), e.getMessage());
            socketChannel.onError();
        }
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        super.channelActive(ctx);

        var socketChannel = serverSocket.channelPool().getChannel(ctx.channel());
        if (socketChannel == null) {
            log.warn("Skip channelActive: SocketChannel not found for {}", ctx.channel().id());
            ctx.close();
            return;
        }

        socketChannel.onConnect();

        // v3.0: delegate lifecycle orchestration to coordinator
        coordinator.onServerChannelActive(serverSocket, ctx.channel());
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        super.channelInactive(ctx);

        var socketChannel = serverSocket.channelPool().getChannel(ctx.channel());
        if (socketChannel == null) {
            log.warn("Skip channelInactive: SocketChannel not found for {}", ctx.channel().id());
            return;
        }

        socketChannel.onDisconnect();

        // v3.0: delegate lifecycle orchestration to coordinator
        coordinator.onServerChannelInactive(serverSocket, ctx.channel());
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        log.error("{} exception occurred: {}", serverSocket.getId(), cause.getMessage());
        serverSocket.channelPool().removeChannel(ctx.channel());
        ctx.close();
    }
}
