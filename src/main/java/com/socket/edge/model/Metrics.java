package com.socket.edge.model;

public record Metrics(String id, String name, String type,
                      long avgLatency, long minLatency, long maxLatency, long msgIn, long msgOut,
                      long queue, long errCnt, long lastErr, long tps, long lastMsg, long lastConnect,
                      long lastDisconnect){


}
