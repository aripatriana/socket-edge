package com.socket.edge.core.strategy;

import com.socket.edge.core.LoadAware;
import com.socket.edge.core.MessageContext;

import java.util.Comparator;
import java.util.List;

public class LeastConnectionStrategy<T extends LoadAware>
        implements SelectionStrategy<T> {

    @Override
    public T next(List<T> candidates, MessageContext messageContext) {
        return candidates.stream()
                .min(Comparator.comparingInt(LoadAware::inflight))
                .orElseThrow(() ->
                        new IllegalStateException("No candidates"));
    }
}