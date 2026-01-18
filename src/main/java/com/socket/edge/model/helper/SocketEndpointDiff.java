package com.socket.edge.model.helper;

import com.socket.edge.constant.SockeEndpointField;

import java.util.Map;

public record SocketEndpointDiff (
        Map<SockeEndpointField, FieldDiff> changes) {
    public boolean hasChanges() {
        return !changes.isEmpty();
    }
}
