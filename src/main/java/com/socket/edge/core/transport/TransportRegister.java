package com.socket.edge.core.transport;

import com.socket.edge.core.socket.AbstractSocket;
import com.socket.edge.core.socket.NettyClientSocket;
import com.socket.edge.core.socket.NettyServerSocket;
import com.socket.edge.core.socket.SocketChannel;
import com.socket.edge.core.strategy.SelectionFactory;
import com.socket.edge.core.strategy.SelectionStrategy;
import com.socket.edge.model.ChannelCfg;
import com.socket.edge.constant.SocketType;

import java.util.List;
import java.util.Objects;

public class TransportRegister {

    public TransportProvider transportProvider;

    public TransportRegister(TransportProvider transportProvider) {
        this.transportProvider = transportProvider;
    }

    public void unregisterServerTransport(ChannelCfg cfg) {
        transportProvider.unregister(SocketType.SOCKET_SERVER.name() + "|" + cfg.name());
    }

    public void unregisterClientTransport(ChannelCfg cfg) {
        transportProvider.unregister(SocketType.SOCKET_CLIENT.name() + "|" + cfg.name());
    }

    public void registerServerTransport(ChannelCfg cfg, NettyServerSocket socket) {
        SelectionStrategy<SocketChannel> strategy =
                SelectionFactory.create(cfg.client().strategy());

        transportProvider.register(socket.getType().name() + "|" + cfg.name(),
                new ServerTransport(socket, strategy)
        );
    }

    public void registerClientTransport(ChannelCfg cfg, List<NettyClientSocket> clientSockets) {
        Objects.requireNonNull(clientSockets, "clientSockets must not be null");
        if (clientSockets.isEmpty()) {
            throw new IllegalArgumentException("clientSockets must not be empty");
        }
        SelectionStrategy<SocketChannel> strategy =
                SelectionFactory.create(cfg.client().strategy());

        transportProvider.register(
                clientSockets.get(0).getType().name() + "|" + cfg.name(),
                new ClientTransport(clientSockets, strategy)
        );
    }

    public void destroy() {
        transportProvider.destroy();
    }
}
