package com.socket.edge.model.helper;

import com.socket.edge.constant.SockeEndpointField;
import com.socket.edge.model.SocketEndpoint;

import java.util.Map;

public record SocketEndpointDiff (
        SocketEndpoint oldSocketEndpoint,
        SocketEndpoint newSocketEndpoint,
        Map<SockeEndpointField, FieldDiff> fieldChanges) {
    public boolean hasChanges() {
        return !fieldChanges.isEmpty();
    }

    public StringBuffer toString(StringBuffer sb) {
        if (hasChanges()) {
            fieldChanges.forEach((field, change) -> {
                sb.append(">>>> Modified endpoint field:")
                        .append(" endpoint=").append(oldSocketEndpoint().id())
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
