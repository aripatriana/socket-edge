package com.socket.edge.core.strategy;

import com.socket.edge.core.MessageContext;

import java.util.List;
import java.util.function.Function;

public final class HashStrategy<T>
        implements SelectionStrategy<T> {

    private final Function<MessageContext, String> keyExtractor;

    public HashStrategy(Function<MessageContext, String> keyExtractor) {
        this.keyExtractor = keyExtractor;
    }

    @Override
    public T next(List<T> candidates, MessageContext ctx) {

        String key = keyExtractor.apply(ctx);

        int hash = Math.abs(key.hashCode());
        int idx = hash % candidates.size();

        return candidates.get(idx);
    }
}
