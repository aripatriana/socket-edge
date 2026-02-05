package com.socket.edge.core.strategy;

import com.socket.edge.core.MessageContext;
import com.socket.edge.model.VersionedCandidates;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * {@code RoundRobinStrategy} is a weighted round-robin selection strategy
 * with priority awareness and version-based cache invalidation.
 *
 * <p>
 * This strategy works by:
 * <ul>
 *   <li>Selecting only candidates with the highest priority</li>
 *   <li>Building an exact routing cycle based on candidate weights</li>
 *   <li>Iterating over the cycle in round-robin order</li>
 * </ul>
 * </p>
 *
 * <p>
 * To minimize runtime cost, the routing cycle is cached and rebuilt
 * only when the candidate version changes.
 * </p>
 *
 * <p>
 * Thread-safety model:
 * <ul>
 *   <li>Fast-path reads are lock-free</li>
 *   <li>Cycle rebuild is synchronized</li>
 *   <li>Index increment is atomic</li>
 * </ul>
 * </p>
 *
 * @param <T> candidate type supporting {@link WeightedCandidate}
 */
public final class RoundRobinStrategy<T extends WeightedCandidate>
        implements SelectionStrategy<T> {

    /**
     * Version of candidates currently loaded in the cycle.
     */
    private volatile long loadedVersion = -1;

    /**
     * Precomputed routing cycle.
     */
    private volatile Object[] cycle;

    /**
     * Atomic index for round-robin traversal.
     */
    private final AtomicInteger idx = new AtomicInteger(0);

    /**
     * Selects the next candidate using weighted round-robin.
     *
     * <p>
     * If the candidate version differs from the cached version,
     * the routing cycle will be rebuilt.
     * </p>
     *
     * @param vc versioned candidates snapshot
     * @param messageContext message context (not used in this strategy)
     * @return selected candidate
     * @throws IllegalStateException if no candidate is available
     */
    @Override
    @SuppressWarnings("unchecked")
    public T next(VersionedCandidates<T> vc, MessageContext messageContext) {
        validate(vc.candidates());

        long v = vc.version();

        // VERY CHEAP check
        if (v != loadedVersion) {
            rebuild(vc);
        }

        Object[] c = cycle;
        int i = Math.floorMod(idx.getAndIncrement(), c.length);
        return (T) c[i];
    }

    /**
     * Rebuilds the routing cycle when candidate version changes.
     *
     * <p>
     * This method is synchronized to ensure that the cycle
     * is rebuilt only once per version.
     * </p>
     *
     * @param vc versioned candidates
     */
    private synchronized void rebuild(VersionedCandidates vc) {
        if (vc.version() != loadedVersion) {
            cycle = buildPriorityCycle(vc.candidates());
            loadedVersion = vc.version();
            idx.set(0);
        }
    }

    /**
     * Builds a routing cycle using only candidates
     * with the highest priority.
     *
     * @param candidates available candidates
     * @return routing cycle
     * @throws IllegalStateException if no active candidates exist
     */
    private Object[] buildPriorityCycle(List<T> candidates) {

        // Determine highest priority
        int highestPriority = Integer.MIN_VALUE;
        for (T c : candidates) {
            highestPriority = Math.max(
                    highestPriority,
                    c.getPriority()
            );
        }

        // Select only highest-priority candidates
        List<T> active = new ArrayList<>();
        for (T c : candidates) {
            if (c.getPriority() == highestPriority) {
                active.add(c);
            }
        }

        if (active.isEmpty()) {
            throw new IllegalStateException(
                    "No active candidates"
            );
        }

        // Build exact weighted cycle
        return buildRoutingTable(active);
    }

    /**
     * Builds an exact routing table based on candidate weights.
     *
     * <p>
     * Guarantees:
     * <ul>
     *   <li>Each candidate appears at least once</li>
     *   <li>Higher weight candidates appear more frequently</li>
     *   <li>Back-to-back selection of the same candidate is minimized</li>
     * </ul>
     * </p>
     *
     * @param candidates candidates to include
     * @return routing cycle
     */
    private Object[] buildRoutingTable(List<T> candidates) {

        class Node {
            final T c;
            int remain;
            boolean seen = false;

            Node(T c) {
                this.c = c;
                this.remain = Math.max(1, c.getWeight());
            }
        }

        List<Node> nodes = new ArrayList<>();
        int total = 0;

        for (T c : candidates) {
            Node n = new Node(c);
            nodes.add(n);
            total += n.remain;
        }

        Object[] cycle = new Object[total];
        Node last = null;

        for (int i = 0; i < total; i++) {

            Node pick = null;

            // Priority: candidates not yet selected
            for (Node n : nodes) {
                if (n.remain > 0 && !n.seen) {
                    pick = n;
                    break;
                }
            }

            // All seen → pick largest remaining weight,
            // avoiding the last pick if possible
            if (pick == null) {
                for (Node n : nodes) {
                    if (n.remain <= 0) continue;
                    if (last != null && n == last) continue;

                    if (pick == null || n.remain > pick.remain) {
                        pick = n;
                    }
                }
            }

            // Fallback: only one candidate left
            if (pick == null) {
                for (Node n : nodes) {
                    if (n.remain > 0) {
                        pick = n;
                        break;
                    }
                }
            }

            cycle[i] = pick.c;
            pick.remain--;
            pick.seen = true;
            last = pick;
        }

        return cycle;
    }
}
