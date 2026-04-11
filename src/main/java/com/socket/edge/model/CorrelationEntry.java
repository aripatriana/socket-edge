package com.socket.edge.model;

import java.io.Serializable;

public record CorrelationEntry(
    String correlationKey,
    String replySocketId,
    String replyChannelId,
    long createdAt) implements Serializable {

    public static CorrelationEntry newEntry(
            String correlationKey,
            String replySocketId,
            String replyChannelId
    ) {
        return new CorrelationEntry(
                correlationKey,
                replySocketId,
                replyChannelId,
                System.currentTimeMillis()
        );
    }
}
