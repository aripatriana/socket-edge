package com.socket.edge.model.helper;

import com.socket.edge.constant.ProfileField;

import java.util.Map;

public record Iso8583ProfileDiff(
        String profileName,
        Map<ProfileField, FieldDiff> changes
) {
    public boolean hasChanges() {
        return !changes.isEmpty();
    }
}
