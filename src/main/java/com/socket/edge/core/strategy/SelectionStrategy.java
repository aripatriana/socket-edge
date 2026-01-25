package com.socket.edge.core.strategy;

import com.socket.edge.core.MessageContext;
import com.socket.edge.model.VersionedCandidates;

import java.util.List;

public interface SelectionStrategy<T> {

    T next(VersionedCandidates<T> vc, MessageContext ctx);

    default void validate(List<T> candidates) {
        if (candidates == null || candidates.isEmpty()) {
            throw new IllegalStateException("No candidates available");
        }
    }
}
