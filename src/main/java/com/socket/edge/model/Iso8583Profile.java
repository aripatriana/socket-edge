package com.socket.edge.model;

import java.util.List;
import java.util.Map;
import java.util.Set;

public class Iso8583Profile {
    private final Map<Direction, Set<String>> directions;
    private final List<String> correlationFields;

    public Iso8583Profile(
            Map<Direction, Set<String>> directions,
            List<String> correlationFields
    ) {
        this.directions = directions;
        this.correlationFields = correlationFields;
    }

    public Set<String> valuesFor(Direction direction) {
        return directions.getOrDefault(direction, Set.of());
    }

    public List<String> correlationFields() {
        return correlationFields;
    }
}
