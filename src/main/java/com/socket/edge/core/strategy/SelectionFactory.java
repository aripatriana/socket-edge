package com.socket.edge.core.strategy;

import com.socket.edge.core.LoadAware;
import com.socket.edge.core.MessageContext;

import java.util.Objects;
import java.util.function.Function;

public class SelectionFactory {

    public static <T extends WeightedCandidate> SelectionStrategy<T> roundRobin() {
        return new RoundRobinStrategy<>();
    }

    public static <T extends LoadAware> SelectionStrategy<T> leastConnection() {
        return new LeastConnectionStrategy<>();
    }

    public static <T> SelectionStrategy<T> hash(
            Function<MessageContext, String> keyExtractor) {
        return new HashStrategy<>(keyExtractor);
    }

    public static <T> SelectionStrategy<T> create(
            String strategy,
            Function<MessageContext, String> keyExtractor) {

        return switch (strategy.toLowerCase()) {
            case "roundrobin" -> (SelectionStrategy<T>) roundRobin();
            case "least" -> (SelectionStrategy<T>) leastConnection();
            case "hash" -> hash(keyExtractor);
            default -> throw new IllegalArgumentException("Unknown strategy: " + strategy);
        };
    }

}
