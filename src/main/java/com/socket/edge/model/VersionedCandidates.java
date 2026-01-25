package com.socket.edge.model;

import java.util.List;

public record VersionedCandidates<T>(
        long version,
        List<T> candidates
) {
}