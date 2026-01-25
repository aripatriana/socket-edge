package com.socket.edge.core.socket;

import com.socket.edge.model.EndpointKey;
import com.socket.edge.model.SocketEndpoint;
import io.netty.channel.Channel;
import io.netty.channel.ChannelId;

import java.net.InetSocketAddress;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class SocketChannelPooling {

    private AbstractSocket abstractSocket;
    private final ConcurrentMap<ChannelId, SocketChannel> activeChannels = new ConcurrentHashMap<>();
    private final ConcurrentMap<EndpointKey, Set<SocketChannel>> endpointIndex = new ConcurrentHashMap<>();

    public SocketChannelPooling(AbstractSocket abstractSocket) {
        this.abstractSocket = abstractSocket;
    }

    public boolean addChannel(Channel ch) {
        String remoteIp =
                ((InetSocketAddress) ch.remoteAddress())
                        .getAddress()
                        .getHostAddress();

        if (!abstractSocket.allowlist().isEmpty() && abstractSocket.allowlist().stream()
                .noneMatch(ep -> remoteIp.equals(ep.host()))) {
            ch.close();
            return false;
        }

        int remotePort =
                ((InetSocketAddress) ch.remoteAddress())
                        .getPort();

        SocketEndpoint se = abstractSocket.resolveEndpoint(remoteIp, remotePort);
        if (se == null) {
            ch.close();
            return false;
        }

        SocketChannel sc = new SocketChannel(abstractSocket.getId(), ch, se, abstractSocket.resolveTelemetry(se.id()));
        SocketChannel existing = activeChannels.putIfAbsent(ch.id(), sc);
        if (existing != null) {
            return false;
        }

        endpointIndex
                .computeIfAbsent(EndpointKey.from(se), k -> ConcurrentHashMap.newKeySet())
                .add(sc);
        return true;
    }

    public int removeByEndpoint(EndpointKey key) {
        Set<SocketChannel> channels = endpointIndex.remove(key);
        if (channels == null || channels.isEmpty()) {
            return 0;
        }

        channels.forEach(sc -> {
            activeChannels.remove(sc.channelId());
            if (sc.isActive()) {
                sc.close();
            }
        });

        return channels.size();
    }

    public void removeChannel(Channel ch) {
        SocketChannel sc = activeChannels.remove(ch.id());
        if (sc == null) {
            return;
        }

        EndpointKey key = endpointKey(sc.getSocketEndpoint());
        Set<SocketChannel> set = endpointIndex.get(key);

        if (set != null) {
            set.remove(sc);
            if (set.isEmpty()) {
                endpointIndex.remove(key);
            }
        }
    }

    public SocketChannel get(Channel ch) {
        return activeChannels.get(ch.id());
    }

    public Set<SocketChannel> getAllByEndpoint(SocketEndpoint se) {
        return endpointIndex.getOrDefault(EndpointKey.from(se), Set.of());
    }

    public List<SocketChannel> activeChannels() {
        return activeChannels.values().stream()
                .filter(SocketChannel::isActive)
                .toList();
    }

    public List<SocketChannel> getAllChannel() {
        return activeChannels.values().stream().toList();
    }

    private EndpointKey endpointKey(SocketEndpoint se) {
        if (se == null) {
            throw new IllegalStateException("SocketEndpoint cannot be null");
        }
        return EndpointKey.from(se);
    }

    public void closeAll() {
        activeChannels.values().forEach(ch -> {
            if (ch.isActive()) {
                ch.close();
            }
        });
        activeChannels.clear();
        endpointIndex.clear();
    }

}
