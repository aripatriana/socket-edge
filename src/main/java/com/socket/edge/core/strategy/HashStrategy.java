package com.socket.edge.core.strategy;

import com.socket.edge.core.MessageContext;
import com.socket.edge.model.VersionedCandidates;

import java.util.Objects;
import java.util.function.Function;

public final class HashStrategy<T>
        implements SelectionStrategy<T> {

    private final Function<MessageContext, String> keyExtractor;

    public HashStrategy(Function<MessageContext, String> keyExtractor) {
        this.keyExtractor = Objects.requireNonNull(keyExtractor);
    }
    @Override
    public T next(VersionedCandidates<T> vc, MessageContext ctx) {
        validate(vc.candidates());

        String key = keyExtractor.apply(ctx);
        if (key == null) {
            throw new IllegalStateException("Hash key must not be null");
        }

        int idx = Math.floorMod(key.hashCode(), vc.candidates().size());
        return vc.candidates().get(idx);
    }
}
