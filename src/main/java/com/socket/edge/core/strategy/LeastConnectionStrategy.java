package com.socket.edge.core.strategy;

import com.socket.edge.core.LoadAware;
import com.socket.edge.core.MessageContext;
import com.socket.edge.model.VersionedCandidates;

import java.util.Comparator;

public class LeastConnectionStrategy<T extends LoadAware>
        implements SelectionStrategy<T> {

    @Override
    public T next(VersionedCandidates<T> vc, MessageContext messageContext) {
        validate(vc.candidates());

        return vc.candidates().stream()
                .min(Comparator.comparingInt(LoadAware::inflight)
                        .thenComparingInt(Object::hashCode))
                .orElseThrow(() ->
                        new IllegalStateException("No candidates"));
    }
}