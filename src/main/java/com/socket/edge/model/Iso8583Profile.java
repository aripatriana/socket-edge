package com.socket.edge.model;

import com.socket.edge.constant.Direction;
import com.socket.edge.constant.ProfileField;
import com.socket.edge.model.helper.FieldDiff;
import com.socket.edge.model.helper.Iso8583ProfileDiff;
import com.socket.edge.utils.CommonUtil;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public record Iso8583Profile (
        Map<Direction, Set<String>> directions,
        List<String> correlationFields)
{

    public Set<String> valuesFor(Direction direction) {
        return directions.getOrDefault(direction, Set.of());
    }

    public Iso8583ProfileDiff diffWith(
            String profileName,
            Iso8583Profile newOne
    ) {
        Map<ProfileField, FieldDiff> changes = new HashMap<>();

        // 1. directions
        CommonUtil.diff(
                changes,
                ProfileField.DIRECTIONS,
                this.directions,
                newOne.directions,
                ChangeImpact.LIVE   // atomic swap
        );

        // 2. correlation fields
        CommonUtil.diff(
                changes,
                ProfileField.CORRELATION_FIELDS,
                this.correlationFields,
                newOne.correlationFields,
                ChangeImpact.LIVE
        );

        return new Iso8583ProfileDiff(profileName, changes);
    }
}
