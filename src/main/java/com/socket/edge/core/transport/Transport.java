package com.socket.edge.core.transport;

import com.socket.edge.core.MessageContext;

public interface Transport {
    void send(MessageContext ctx);
    boolean isUp();
    void shutdown();
}
