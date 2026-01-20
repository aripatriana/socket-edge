package com.socket.edge.core.strategy;

import com.socket.edge.core.MessageContext;

import java.util.List;

public interface SelectionStrategy<T> {

    T next(List<T> candidates, MessageContext ctx);

    default void validate(List<T> candidates) {
        if (candidates == null || candidates.isEmpty()) {
            throw new IllegalStateException("No candidates available");
        }
    }
}
