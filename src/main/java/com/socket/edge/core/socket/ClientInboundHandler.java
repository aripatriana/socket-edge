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

/**
 * Client-side Netty inbound handler for ISO 8583 message processing.
 *
 * <p>v3.0 changes:
 * <ul>
 *   <li>Removed {@code SocketManager} dependency</li>
 *   <li>Lifecycle orchestration delegated to {@link SocketLifecycleCoordinator}</li>
 *   <li>Fixed: PCI-DSS compliant logging</li>
 *   <li>Fixed: proper pattern matching with early return</li>
 * </ul>
 *
 * @author Ari Patriana
 * @since 3.0.0
 */
public final class ClientInboundHandler extends ChannelInboundHandlerAdapter {

    private static final Logger log = LoggerFactory.getLogger(ClientInboundHandler.class);

    private final DefaultClientSocket clientSocket;
    private final IsoParser isoParser;
    private final MessageContextProcess messageContextProcess;
    private final SocketLifecycleCoordinator coordinator;

    public ClientInboundHandler(
            DefaultClientSocket clientSocket,
            IsoParser isoParser,
            MessageContextProcess messageContextProcess,
            SocketLifecycleCoordinator coordinator
    ) {
        this.clientSocket = clientSocket;
        this.isoParser = isoParser;
        this.messageContextProcess = messageContextProcess;
        this.coordinator = coordinator;
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

        // v3.0 FIX: proper pattern matching with early return
        if (!(msg instanceof byte[] rawBytes)) {
            log.warn("Unsupported message type: {}", msg.getClass());
            return;
        }

        try {
            socketChannel.onMessage();

            if (log.isDebugEnabled()) {
                log.debug("{} received {} bytes", clientSocket.getId(), rawBytes.length);
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

        socketChannel.onDisconnect();

        // v3.0: delegate lifecycle to coordinator
        coordinator.onClientChannelInactive(clientSocket);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        log.error("{} exception occurred: {}", clientSocket.getId(), cause.getMessage());
        ctx.close();
    }
}
