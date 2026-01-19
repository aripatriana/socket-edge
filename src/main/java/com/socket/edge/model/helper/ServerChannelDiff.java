package com.socket.edge.model.helper;

import com.socket.edge.constant.ServerChannelField;
import com.socket.edge.model.ServerChannel;
import com.socket.edge.model.SocketEndpoint;

import java.util.List;
import java.util.Map;

public record ServerChannelDiff (
        ServerChannel oldServerChannel,
        ServerChannel newServerChannel,
        Map<ServerChannelField, FieldDiff> fieldChanges,
        List<SocketEndpoint> addedEndpoints,
        List<SocketEndpoint> removedEndpoints,
        List<SocketEndpointDiff> modifiedEndpoints
) {
    public boolean hasChanges() {
        return !modifiedEndpoints.isEmpty();
    }
}
