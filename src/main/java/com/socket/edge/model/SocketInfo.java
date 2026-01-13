package com.socket.edge.model;

public record SocketInfo(
    String id,
    String name,
    int activeClient,
    int activeServer,
    String status
){}
