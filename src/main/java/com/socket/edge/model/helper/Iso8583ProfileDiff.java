package com.socket.edge.model.helper;

import com.socket.edge.constant.ProfileField;

import java.util.Map;

public record Iso8583ProfileDiff(
        String profileName,
        Map<ProfileField, FieldDiff> fieldChanges
) {
    public boolean hasChanges() {
        return !fieldChanges.isEmpty();
    }

    public StringBuffer toString(StringBuffer sb) {
        if (!fieldChanges.isEmpty()) {
            fieldChanges.forEach((field, change) -> {
                sb.append(">>> Modified profiles field:")
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
