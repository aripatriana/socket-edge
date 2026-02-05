package com.socket.edge.core.strategy;

/**
 * {@code WeightedCandidate} represents a selectable candidate
 * with weight and priority attributes.
 *
 * <p>
 * This interface is typically used by selection strategies
 * to influence routing and load distribution decisions.
 * </p>
 *
 * <p>
 * Semantics:
 * <ul>
 *   <li><b>Priority</b> determines eligibility — only candidates
 *       with the highest priority are considered</li>
 *   <li><b>Weight</b> determines frequency — higher weight candidates
 *       are selected more often within the same priority level</li>
 * </ul>
 * </p>
 *
 * <p>
 * Implementations should ensure that:
 * <ul>
 *   <li>Priority values are comparable (higher means more preferred)</li>
 *   <li>Weight values are non-negative</li>
 * </ul>
 * </p>
 *
 * @author Ari Patriana
 * @since 1.0.0
 */
public interface WeightedCandidate {

    /**
     * Returns the weight of this candidate.
     *
     * <p>
     * Weight influences how frequently this candidate
     * appears in a routing cycle.
     * </p>
     *
     * @return weight value (should be {@code >= 0})
     */
    int getWeight();

    /**
     * Returns the priority of this candidate.
     *
     * <p>
     * Only candidates with the highest priority
     * will be considered for selection.
     * </p>
     *
     * @return priority value (higher means more preferred)
     */
    int getPriority();
}
