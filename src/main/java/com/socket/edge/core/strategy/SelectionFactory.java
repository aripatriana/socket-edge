package com.socket.edge.core.strategy;

import java.util.Objects;

public class SelectionFactory {

    public static SelectionStrategy create(String strategy, String...args) {
        Objects.requireNonNull(strategy, "Strategy is null");
        if (strategy.equalsIgnoreCase("roundrobin")) {
            return new RoundRobinStrategy();
        } else if (strategy.equalsIgnoreCase("least")) {
            return new LeastConnectionStrategy();
        } else if (strategy.equalsIgnoreCase("hash")) {
            Objects.requireNonNull(args, "Argument strategy required");
            return new HashStrategy<>(ctx -> ctx.field(args[0]));
        }
        throw new IllegalArgumentException("No strategy found");
    }
}
