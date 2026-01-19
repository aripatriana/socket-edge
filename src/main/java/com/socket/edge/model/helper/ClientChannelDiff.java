package com.socket.edge.model.helper;

import com.socket.edge.constant.ClientChannelField;
import com.socket.edge.model.ClientChannel;
import com.socket.edge.model.SocketEndpoint;

import java.util.List;
import java.util.Map;

public record ClientChannelDiff (
        ClientChannel oldClientChannel,
        ClientChannel newClientChannel,
        Map<ClientChannelField, FieldDiff> fieldChanges,
        List<SocketEndpoint> addedEndpoints,
        List<SocketEndpoint> removedEndpoints,
        List<SocketEndpointDiff> modifiedEndpoints
) {
    public boolean hasChanges() {
        return !fieldChanges.isEmpty()
                || !addedEndpoints.isEmpty()
                || !removedEndpoints.isEmpty()
                ||  modifiedEndpoints.stream().anyMatch(SocketEndpointDiff::hasChanges);
    }

    public StringBuffer toString(StringBuffer sb) {
        if (!removedEndpoints.isEmpty()) {
            removedEndpoints.forEach(endpoint -> {
                sb.append(">>> Removed client endpoint detected: ")
                        .append(" endpoint=").append(endpoint.id())
                        .append("\n");
            });
        }
        if (!addedEndpoints.isEmpty()) {
            addedEndpoints.forEach(endpoint ->{
                sb.append(">>> Added client endpoint detected: ")
                        .append(" endpoint=").append(endpoint.id())
                        .append("\n");
            });
        }
        if (!modifiedEndpoints.isEmpty()) {
            modifiedEndpoints.forEach(socketEndpointDiff -> {
                if (socketEndpointDiff.hasChanges()) {
                    sb.append(">>> Modified endpoint detected.")
                            .append("\n");
                    socketEndpointDiff.toString(sb);
                }
            });
        }
        if (!fieldChanges.isEmpty()) {
            fieldChanges.forEach((field, change) -> {
                sb.append(">>> Modified client channel field:")
                        .append(" field=").append(field)
                        .append(", old=").append(change.oldValue())
                        .append(", new=").append(change.newValue())
                        .append(", action=").append(change.impact().name())
                        .append("\n");
            });
        }
        return sb;
    }
}
