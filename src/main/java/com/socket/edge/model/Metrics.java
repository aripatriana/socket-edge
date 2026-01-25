package com.socket.edge.model;

public record Metrics(String hashId,
                      String id,
                      String name,
                      String type,
                      long avgLatency,
                      long minLatency,
                      long maxLatency,
                      long latencyP90Ns,
                      long latencyP95Ns,
                      long pressureTps,
                      long minPressureTps,
                      long maxPressureTps,
                      long pressureTpsP90,
                      long pressureTpsP95,
                      long throughputTps,
                      long minThroughputTps,
                      long maxThroughputTps,
                      long throughputTpsP90,
                      long throughputTpsP95
                      ){
}
