package com.socket.edge.model;

import java.util.List;

public record ServerChannel(
        String listenHost,
        int listenPort,
        List<SocketEndpoint> pool,
        String strategy
) {}