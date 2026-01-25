package com.socket.edge.model;

public record RuntimeState(
    String hashId,
    String id,
    String name,
    String type,
    String localHost,
    String remoteHost,
    int active,
    long startTime,
    long lastConnect,
    long lastDisconnect,
    String status
){}
