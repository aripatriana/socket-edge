package com.socket.edge.model;

import java.util.List;

public record ClientChannel(
        List<SocketEndpoint> endpoints,
        String strategy
) {}