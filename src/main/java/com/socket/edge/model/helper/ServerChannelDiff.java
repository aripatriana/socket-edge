package com.socket.edge.model.helper;

import com.socket.edge.constant.ClientChannelField;
import com.socket.edge.constant.ServerChannelField;
import com.socket.edge.model.SocketEndpoint;

import java.util.List;
import java.util.Map;

public record ServerChannelDiff (
        Map<ServerChannelField, FieldDiff> fieldChanges,
        List<SocketEndpoint> addedEndpoints,
        List<SocketEndpoint> removedEndpoints,
        List<SocketEndpointDiff> modifiedEndpoints
) {
    public boolean hasChanges() {
        return !modifiedEndpoints.isEmpty();
    }
}
