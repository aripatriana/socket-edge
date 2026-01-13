package com.socket.edge.core.cache;

public interface CorrelationStore {
    void put(String key, io.netty.channel.Channel inbound);
    io.netty.channel.Channel get(String key);
    void remove(String key);
}
