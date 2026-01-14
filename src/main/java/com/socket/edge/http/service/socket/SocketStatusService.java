package com.socket.edge.http.service.socket;

import com.socket.edge.core.socket.AbstractSocket;
import com.socket.edge.core.socket.NettyServerSocket;
import com.socket.edge.core.socket.SocketChannel;
import com.socket.edge.model.SocketStatus;
import io.netty.channel.Channel;

import java.net.InetSocketAddress;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public class SocketStatusService {

    private final long startTime = System.currentTimeMillis();
    private List<AbstractSocket> sockets;

    public SocketStatusService(List<AbstractSocket> sockets) {
        this.sockets = sockets;
    }

    public long uptime() {
        return (System.currentTimeMillis() - startTime);
    }

    public List<SocketStatus> getSocketStatus() {
        return sockets.stream()
                .map(socket -> {
                    List<SocketChannel> channels =
                            socket.channelPool().activeChannels();
                    int active = channels.size();
                    String localHost;
                    if (socket instanceof NettyServerSocket server) {
                        localHost = extractServerLocalHost(server);
                    } else {
                        localHost = extractLocalHost(channels);
                    }
                    String remoteHost = extractRemoteHosts(channels);
                    String status = socket.isUp() ? "UP" : "DOWN";

                    return new SocketStatus(
                            socket.getId(),
                            socket.getName(),
                            socket.getType().name(),
                            localHost,
                            remoteHost,
                            active,
                            socket.getUptime(),
                            status
                    );
                })
                .sorted(Comparator.comparing(SocketStatus::id))
                .toList();
    }

    private String extractLocalHost(List<SocketChannel> channels) {

        if (channels == null || channels.isEmpty()) {
            return "-";
        }

        return channels.stream()
                .map(SocketChannel::channel)
                .filter(Objects::nonNull)
                .filter(Channel::isActive)
                .map(Channel::localAddress)
                .filter(InetSocketAddress.class::isInstance)
                .map(InetSocketAddress.class::cast)
                .map(addr -> addr.getHostString() + ":" + addr.getPort())
                .distinct()
                .findFirst()
                .orElse("-");
    }

    private String extractRemoteHosts(List<SocketChannel> channels) {
        if (channels == null || channels.isEmpty()) {
            return "-";
        }

        return channels.stream()
                .map(SocketChannel::channel)
                .filter(Objects::nonNull)
                .filter(Channel::isActive)
                .map(Channel::remoteAddress)
                .filter(InetSocketAddress.class::isInstance)
                .map(InetSocketAddress.class::cast)
                .map(addr -> addr.getHostString() + ":" + addr.getPort())
                .distinct()
                .collect(Collectors.joining(","));
    }

    private String extractServerLocalHost(NettyServerSocket socket) {
        Channel ch = socket.getServerChannel();
        if (ch == null) return "-";

        InetSocketAddress addr =
                (InetSocketAddress) ch.localAddress();

        return ":" + addr.getPort();
    }
}
