package com.socket.edge.core.socket;

import com.socket.edge.constant.SocketState;
import com.socket.edge.core.CompletionCallback;
import com.socket.edge.core.MessageContextProcess;
import com.socket.edge.core.MessageContext;
import com.socket.edge.constant.SocketType;
import com.socket.edge.utils.IsoParser;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public final class ServerInboundHandler
        extends ChannelInboundHandlerAdapter {

    private static final Logger log = LoggerFactory.getLogger(ServerInboundHandler.class);

    private final SocketManager sm;
    private final DefaultServerSocket serverSocket;
    private final IsoParser isoParser;
    private final MessageContextProcess messageContextProcess;

    public ServerInboundHandler(SocketManager sm, DefaultServerSocket serverSocket, IsoParser isoParser, MessageContextProcess messageContextProcess) {
        this.sm = sm;
        this.serverSocket = serverSocket;
        this.isoParser = isoParser;
        this.messageContextProcess = messageContextProcess;
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        long start = System.nanoTime();

        var socketChannel = serverSocket.channelPool().getChannel(ctx.channel());
        if (socketChannel == null) {
            log.warn("Skip channelRead: SocketChannel not found for {}", ctx.channel().id());
            return;
        }

        try {
            socketChannel.onMessage();

            if (!(msg instanceof byte[] rawBytes)) {
                log.warn("Unsupported message type: {}", msg.getClass());
            }

            byte[] rawBytes = (byte[]) msg;
            if (log.isInfoEnabled()) {
                log.info("{} read {}", serverSocket.getId(), new String(rawBytes));
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

        List<AbstractSocket> clientSockets = sm.getSocketClientByName(serverSocket.getName());
        if (clientSockets.isEmpty()) {
            log.warn("Skip channelActive: No client socket found for server socket {}", serverSocket.getId());
            ctx.close();
            return;
        }

        AtomicInteger remains = new AtomicInteger(clientSockets.size());
        AtomicInteger complete = new AtomicInteger(0);
        CompletionCallback callback = new CompletionCallback<DefaultClientSocket>() {
            List<DefaultClientSocket> failures = new ArrayList<>();
            @Override
            public void onComplete(DefaultClientSocket client) {
                complete.incrementAndGet();
                remains.decrementAndGet();
                afterEvent();
            }

            @Override
            public void onFailure(DefaultClientSocket client) {
                remains.decrementAndGet();
                failures.add(client);
                afterEvent();
            }

            private void afterEvent() {
                if (remains.get() == 0) {
                    if (complete.get() == 0) {
                        log.warn("All client socket failed to connect for server socket {}", serverSocket.getId());
                        ctx.close();
                    } else {
                        ctx.channel().config().setAutoRead(true);
                        failures.forEach(client -> client.scheduleReconnect());
                    }
                }
            }
        };

        clientSockets.forEach(client -> ((DefaultClientSocket)client).connect(callback));
        int totalAvailableChannels =
                clientSockets.stream()
                        .mapToInt(cs -> cs.channelPool().availableChannels().size())
                        .sum();
        if (totalAvailableChannels == 0) {
            log.info("No available channels in client sockets for server socket {}", serverSocket.getId());
            ctx.close();
            return;
        }

        if (serverSocket.getState() == SocketState.LISTEN) {
            log.info("{} state changed to ACTIVE", serverSocket.getId());
            serverSocket.changeState(SocketState.ACTIVE);
        };
        socketChannel.onConnect();
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        super.channelInactive(ctx);

        var socketChannel = serverSocket.channelPool().getChannel(ctx.channel());
        if (socketChannel == null) {
            log.warn("Skip channelInactive: SocketChannel not found for {}", ctx.channel().id());
            return;
        }

        List<AbstractSocket> clientSockets = sm.getSocketClientByName(serverSocket.getName());
        if (clientSockets.isEmpty()) {
            log.warn("Skip channelInactive: No client socket found for server socket {}", serverSocket.getId());
            return;
        }

        if (serverSocket.channelPool().getAllChannel().size() == 0
                && serverSocket.getState() == SocketState.ACTIVE) {

            clientSockets.forEach(client -> {
                try {
                    client.restart();
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            });

            log.info("{} state changed to ACTIVE", serverSocket.getId());
            serverSocket.changeState(SocketState.LISTEN);
        }
        socketChannel.onDisconnect();
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
