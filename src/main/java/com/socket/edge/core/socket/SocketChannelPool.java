package com.socket.edge.core.socket;

import com.socket.edge.model.SocketEndpoint;
import com.socket.edge.model.SocketType;
import io.netty.channel.Channel;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;

public class SocketChannelPool {

    String socketId;
    SocketType socketType;
    List<SocketChannel> activeChannels = new ArrayList<>();
    List<SocketEndpoint> allowlist = new ArrayList<>();

    public SocketChannelPool(String socketId, SocketType socketType, List<SocketEndpoint> allowlist) {
        this.socketId = socketId;
        this.socketType = socketType;
        this.allowlist = allowlist;
    }

    public boolean register(Channel ch) {
        String remoteIp =
                ((InetSocketAddress) ch.remoteAddress())
                        .getAddress()
                        .getHostAddress();

        if (!allowlist.isEmpty() && allowlist.stream()
                .noneMatch(ep -> remoteIp.equals(ep.host()))) {
            ch.close();
            return false;
        }

        int remotePort =
                ((InetSocketAddress) ch.remoteAddress())
                        .getPort();

        SocketEndpoint se =
                socketType.equals(SocketType.SOCKET_SERVER)
                        ? allowlist.stream()
                        .filter(ep -> remoteIp.equals(ep.host()))
                        .findFirst()
                        .orElse(null)
                        : allowlist.stream()
                        .filter(ep ->
                                remoteIp.equals(ep.host()) && remotePort == ep.port()
                        )
                        .findFirst()
                        .orElse(null);

        activeChannels.add(new SocketChannel(socketId, ch, se));
        return true;
    }

    public void unregister(Channel ch) {
        activeChannels.removeIf(ctx -> ctx.channel() == ch);
    }

    public List<SocketChannel> activeChannels() {
        return activeChannels.stream()
                .filter(SocketChannel::isActive)
                .toList();
    }

}
