package com.socket.edge.model;

public record ClientEndpoint(
        String host,
        int port,
        int weight,
        int priority
) {}