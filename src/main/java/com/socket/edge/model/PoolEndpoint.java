package com.socket.edge.model;

public record PoolEndpoint(
        String host,
        int port,
        int weight,
        int priority
) {}