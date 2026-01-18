package com.socket.edge.core.transport;

import com.socket.edge.model.ChannelCfg;
import com.socket.edge.constant.SocketType;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

public final class TransportProvider {

    private final Map<String, Transport> transports =
            new ConcurrentHashMap<>();

    public void register(String key, Transport transport) {
        transports.put(key, transport);
    }

    public void unregister(String key) {
        transports.remove(key);
    }

    public void destroy() {
        transports.clear();
    }


    public Transport resolve(ChannelCfg channelCfg, SocketType outboundType) {
        Objects.requireNonNull(outboundType, "Outbound Type is null");
        Transport transport = transports.get(outboundType.name() + "|" + channelCfg.name());
        Objects.requireNonNull(transport, "No transport for channelCfg " + channelCfg);
        return transport;
    }
}
