package com.socket.edge.model.helper;

import com.socket.edge.constant.SockeEndpointField;
import com.socket.edge.model.SocketEndpoint;

import java.util.Map;

public record SocketEndpointDiff (
        SocketEndpoint oldSocketEndpoint,
        SocketEndpoint newSocketEndpoint,
        Map<SockeEndpointField, FieldDiff> changes) {
    public boolean hasChanges() {
        return !changes.isEmpty();
    }
}
