package com.socket.edge.model;

public record Metrics(String id, String name, String type,
                      long avgLatency, long minLatency, long maxLatency, long msgIn, long msgOut,
                      long queue, long avgTps, long minTps, long maxTps,
                      long errCnt, long lastErr, long lastMsg){


}
