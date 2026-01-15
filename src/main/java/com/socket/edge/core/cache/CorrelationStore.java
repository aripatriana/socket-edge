package com.socket.edge.core.cache;

import com.socket.edge.model.ReplyInbound;

public interface CorrelationStore {
    void put(String key, ReplyInbound inbound);
    ReplyInbound get(String key);
    void remove(String key);
}
