package com.socket.edge.core.strategy;

import com.socket.edge.core.MessageContext;

import java.util.List;

public interface SelectionStrategy<T> {
    T next(List<T> candidates, MessageContext ctx);
}
