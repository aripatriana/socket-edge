package com.socket.edge.core.transport;

import com.socket.edge.model.ChannelCfg;
import com.socket.edge.constant.SocketType;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

public final class TransportProvider {

    private final Map<String, Transport> transports =
            new ConcurrentHashMap<>();


    public boolean registerIfAbsent(String key, Transport transport) {
        Objects.requireNonNull(key, "key must not be null");
        Objects.requireNonNull(transport, "transport must not be null");

        return transports.putIfAbsent(key, transport) == null;
    }

    public void register(String key, Transport transport) {
        Objects.requireNonNull(key, "key must not be null");
        Objects.requireNonNull(transport, "transport must not be null");

        transports.put(key, transport);
    }

    public Transport unregister(String key) {
        Transport removed = transports.remove(key);
        if (removed != null) {
            removed.shutdown(); // IMPORTANT: lifecycle cleanup
        }
        return removed;
    }

    public Transport get(String key) {
        return transports.get(key);
    }

    public boolean contains(String key) {
        return transports.containsKey(key);
    }

    public void destroy() {
        transports.values().forEach(Transport::shutdown);
        transports.clear();
    }

    public Transport resolve(ChannelCfg channelCfg, SocketType outboundType) {
        Objects.requireNonNull(outboundType, "Outbound Type is null");
        Transport transport = transports.get(outboundType.name() + "|" + channelCfg.name());
        Objects.requireNonNull(transport, "No transport for channelCfg " + channelCfg);
        return transport;
    }
}
