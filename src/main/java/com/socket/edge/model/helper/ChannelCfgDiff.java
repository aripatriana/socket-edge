package com.socket.edge.model.helper;

import com.socket.edge.constant.ChannelCfgField;
import com.socket.edge.model.ChannelCfg;

import java.util.Map;

public record ChannelCfgDiff(
        ChannelCfg oldCfg,
        ChannelCfg newCfg,
        Map<ChannelCfgField, FieldDiff> fieldChanges,
        ServerChannelDiff serverChannelDiff,
        ClientChannelDiff clientChannelDiff
) {
    public boolean hasChanges() {
        return !fieldChanges.isEmpty()
                || serverChannelDiff.hasChanges()
                || clientChannelDiff.hasChanges();
    }

    public StringBuffer toString(StringBuffer sb) {
        if (serverChannelDiff != null && serverChannelDiff().hasChanges()) {
            sb.append(".. Modified server channel detected.")
                    .append("\n");
            serverChannelDiff.toString(sb);
        }
        if (clientChannelDiff != null && clientChannelDiff().hasChanges()) {
            sb.append(".. Modified client channel detected.")
                    .append("\n");
            clientChannelDiff.toString(sb);
        }
        if (!fieldChanges.isEmpty()) {
            fieldChanges.forEach((field, change) -> {
                sb.append(".. Modified channel field:")
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