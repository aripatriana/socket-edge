package com.socket.edge.core.socket;

import io.netty.channel.Channel;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;

public class SocketChannelPool {

    String socketId;
    List<SocketChannel> activeChannels = new ArrayList<>();
    List<String> allowlist = new ArrayList<>();

    public SocketChannelPool(String socketId, List<String> allowlist) {
        this.socketId = socketId;
        this.allowlist = allowlist;
    }

    public boolean register(Channel ch) {
        String remoteIp =
                ((InetSocketAddress) ch.remoteAddress())
                        .getAddress()
                        .getHostAddress();

        if (!allowlist.isEmpty() && !allowlist.contains(remoteIp)) {
            ch.close();
            return false;
        }

        activeChannels.add(new SocketChannel(socketId, ch));
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
