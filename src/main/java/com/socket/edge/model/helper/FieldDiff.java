package com.socket.edge.model.helper;

import com.socket.edge.model.ChangeImpact;

public record FieldDiff(
        Object oldValue,
        Object newValue,
        ChangeImpact impact
) {}
