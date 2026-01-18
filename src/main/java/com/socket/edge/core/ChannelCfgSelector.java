package com.socket.edge.core;

import com.socket.edge.model.ChannelCfg;
import com.socket.edge.constant.SocketType;
import com.socket.edge.model.ClientChannel;
import com.socket.edge.model.ServerChannel;

import java.net.InetSocketAddress;
import java.util.List;

public final class ChannelCfgSelector {

    public ChannelCfg select(String channelName, SocketType socketType,
                             InetSocketAddress local,
                             InetSocketAddress remote,
                             List<ChannelCfg> channelCfgs
    ) {
        int localPort = local.getPort();
        String remoteHost = remote.getAddress().getHostAddress();
        int remotePort = remote.getPort();

        for (ChannelCfg ch : channelCfgs) {
            if (ch.name().equals(channelName)) {
                if (socketType.equals(SocketType.SOCKET_SERVER)) {
                    ServerChannel serverChannel = ch.server();
                    if (serverChannel.listenPort() == localPort
                            && serverChannel.pool()
                                    .stream()
                                    .anyMatch(p -> p.host().equalsIgnoreCase(remoteHost))) {
                        return ch;
                    }
                } else if (socketType.equals(SocketType.SOCKET_CLIENT)) {
                    ClientChannel clientChannel = ch.client();
                    if (clientChannel.endpoints()
                            .stream()
                            .anyMatch(e ->
                                    e.host().equalsIgnoreCase(remoteHost)
                                            && e.port() == remotePort
                            )) {
                        return ch;
                    }
                }
            }
        }

        throw new IllegalStateException(
                "No channel matched for localPort="
                        + localPort + ", remoteHost=" + remoteHost + ", remotePort=" + remotePort
        );
    }
}
