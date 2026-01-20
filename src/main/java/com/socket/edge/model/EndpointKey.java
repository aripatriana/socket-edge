package com.socket.edge.model;

public record EndpointKey(String host, int port) {
    public static EndpointKey from(SocketEndpoint se) {
        return new EndpointKey(se.host(), se.port());
    }
}
