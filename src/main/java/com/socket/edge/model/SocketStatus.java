package com.socket.edge.model;

public record SocketStatus(
    String id,
    String name,
    String type,
    String localHost,
    String remoteHost,
    int active,
    long uptime,
    String status
){}
