package com.socket.edge.core;

import com.socket.edge.model.ChannelCfg;
import com.socket.edge.constant.SocketType;
import com.socket.edge.model.ClientChannel;
import com.socket.edge.model.ServerChannel;

import java.net.InetSocketAddress;
import java.util.List;
import java.util.Objects;

public final class ChannelCfgSelector {

    public ChannelCfg select(String channelName, SocketType socketType,
                             InetSocketAddress local,
                             InetSocketAddress remote,
                             List<ChannelCfg> channelCfgs
    ) {
        Objects.requireNonNull(channelCfgs, "channelCfgs");
        Objects.requireNonNull(local, "local");
        Objects.requireNonNull(remote, "remote");
        Objects.requireNonNull(socketType, "socketType");

        int localPort = local.getPort();
        String remoteHost = remote.getHostString();
        int remotePort = remote.getPort();

        return channelCfgs.stream()
                .filter(ch -> ch.name().equals(channelName))
                .filter(ch -> matches(ch, socketType, localPort, remoteHost, remotePort))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "No channel matched for localPort="
                                + localPort + ", remoteHost=" + remoteHost + ", remotePort=" + remotePort
                ));
    }

    private boolean matches(
            ChannelCfg ch,
            SocketType socketType,
            int localPort,
            String remoteHost,
            int remotePort
    ) {
        return switch (socketType) {
            case SERVER -> matchesServer(ch.server(), localPort, remoteHost);
            case CLIENT -> matchesClient(ch.client(), remoteHost, remotePort);
        };
    }

    private boolean matchesServer(ServerChannel server, int localPort, String remoteHost) {
        return server != null
                && server.listenPort() == localPort
                && server.pool().stream()
                .anyMatch(p -> p.host().equalsIgnoreCase(remoteHost));
    }

    private boolean matchesClient(ClientChannel client, String remoteHost, int remotePort) {
        return client != null
                && client.endpoints().stream()
                .anyMatch(e ->
                        e.host().equalsIgnoreCase(remoteHost)
                                && e.port() == remotePort
                );
    }
}
