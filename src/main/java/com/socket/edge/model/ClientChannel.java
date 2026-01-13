package com.socket.edge.model;

import java.util.List;

public record ClientChannel(
        List<ClientEndpoint> endpoints,
        String strategy
) {}