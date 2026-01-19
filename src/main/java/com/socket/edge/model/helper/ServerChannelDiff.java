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
        return !fieldChanges.isEmpty()
                || !addedEndpoints.isEmpty()
                || !removedEndpoints.isEmpty()
                || modifiedEndpoints.stream().anyMatch(SocketEndpointDiff::hasChanges);
    }

    public StringBuffer toString(StringBuffer sb) {
        if (!removedEndpoints.isEmpty()) {
            removedEndpoints.forEach(endpoint -> {
                sb.append("... Removed server endpoint detected: ")
                        .append(" endpoint=").append(endpoint.host())
                        .append("\n");
            });
        }
        if (!addedEndpoints.isEmpty()) {
            addedEndpoints.forEach(endpoint ->{
                sb.append("... Added server endpoint detected: ")
                        .append(" endpoint=").append(endpoint.host())
                        .append("\n");
            });
        }
        if (!modifiedEndpoints.isEmpty()) {
            modifiedEndpoints.forEach(socketEndpointDiff -> {
                if (socketEndpointDiff.hasChanges()) {
                    sb.append("... Modified endpoint detected.")
                            .append("\n");
                    socketEndpointDiff.toString(sb);
                }
            });
        }
        if (!fieldChanges.isEmpty()) {
            fieldChanges.forEach((field, change) -> {
                sb.append("... Modified server channel field:")
                        .append(" endpoint=").append(newServerChannel().id())
                        .append(", field=").append(field)
                        .append(", old=").append(change.oldValue())
                        .append(", new=").append(change.newValue())
                        .append(", action=").append(change.impact().name())
                        .append("\n");
            });
        }
        return sb;
    }
}
