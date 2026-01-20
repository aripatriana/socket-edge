package com.socket.edge.core.socket;

import com.socket.edge.model.SocketEndpoint;
import com.socket.edge.constant.SocketType;
import io.netty.channel.Channel;
import io.netty.channel.ChannelId;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SocketChannelPool {

    private String socketId;
    private SocketType socketType;
    private final Map<ChannelId, SocketChannel> activeChannels = new HashMap<>();
    private List<SocketEndpoint> allowlist = new ArrayList<>();

    public SocketChannelPool(String socketId, SocketType socketType, List<SocketEndpoint> allowlist) {
        this.socketId = socketId;
        this.socketType = socketType;
        this.allowlist = allowlist;
    }

    public boolean addChannel(Channel ch) {
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

        activeChannels.putIfAbsent(ch.id(), new SocketChannel(socketId, ch, se));
        return true;
    }

    public void removeChannel(Channel ch) {
        activeChannels.remove(ch.id());
    }

    public SocketChannel get(Channel ch) {
        return activeChannels.get(ch.id());
    }

    public List<SocketChannel> activeChannels() {
        return activeChannels.values().stream()
                .filter(SocketChannel::isActive)
                .toList();
    }

    public List<SocketChannel> getAllChannel() {
        return activeChannels.values().stream().toList();
    }

    public void closeAll() {
        activeChannels.values().forEach(ch -> {
            if (ch.isActive()) {
                ch.close();
            }
        });
        activeChannels.clear();
    }

}
