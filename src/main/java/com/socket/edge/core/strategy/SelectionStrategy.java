package com.socket.edge.core.strategy;

import com.socket.edge.core.MessageContext;
import com.socket.edge.model.VersionedCandidates;

import java.util.List;

/**
 * {@code SelectionStrategy} defines a strategy for selecting
 * one candidate from a set of available candidates.
 *
 * <p>
 * Implementations may apply different algorithms such as
 * round-robin, least-connection, or hash-based selection.
 * </p>
 *
 * <p>
 * The strategy may take into account:
 * <ul>
 *   <li>Candidate attributes (e.g. weight, load)</li>
 *   <li>Message context</li>
 *   <li>A versioned snapshot of candidates</li>
 * </ul>
 * </p>
 *
 * @param <T> candidate type
 *
 * @author Ari Patriana
 * @since 1.0.0
 */
public interface SelectionStrategy<T> {

    /**
     * Selects the next candidate from the given versioned candidates.
     *
     * <p>
     * Implementations should call {@link #validate(List)} before
     * performing selection.
     * </p>
     *
     * @param vc versioned candidates snapshot
     * @param ctx message context that may influence selection
     * @return selected candidate
     * @throws IllegalStateException if no candidate is available
     */
    T next(VersionedCandidates<T> vc, MessageContext ctx);

    /**
     * Validates the list of available candidates.
     *
     * <p>
     * This default method provides a common validation logic
     * to ensure at least one candidate exists.
     * </p>
     *
     * @param candidates list of candidates
     * @throws IllegalStateException if candidates are {@code null}
     *                               or empty
     */
    default void validate(List<T> candidates) {
        if (candidates == null || candidates.isEmpty()) {
            throw new IllegalStateException("No candidates available");
        }
    }
}
