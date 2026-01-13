package com.socket.edge.core.transport;

import com.socket.edge.model.ChannelCfg;
import com.socket.edge.model.SocketType;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

public final class TransportProvider {

    private final Map<String, Transport> transports =
            new ConcurrentHashMap<>();

    public void register(String key, Transport transport) {
        transports.put(key, transport);
    }

    public Transport resolve(ChannelCfg channelCfg, SocketType socketType) {
        Objects.requireNonNull(socketType, "Socket Type is null");
        SocketType targetChannel = socketType.equals(SocketType.SOCKET_SERVER) ? SocketType.SOCKET_CLIENT : SocketType.SOCKET_SERVER;
        Transport transport = transports.get(targetChannel.name() + "|" + channelCfg.name());
        Objects.requireNonNull(transport, "No transport for channelCfg " + channelCfg);
        return transport;
    }
}
