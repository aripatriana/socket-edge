package com.socket.edge.model;

/**
 * Policy controlling cluster failover behavior.
 *
 * <p>Encapsulates three concerns:
 * <ul>
 *   <li><b>Quorum</b> — minimum members required for valid cluster operation</li>
 *   <li><b>Split-brain</b> — resolution strategy when network partition occurs</li>
 *   <li><b>Failover timeout</b> — delay before promoting slave after master loss</li>
 * </ul>
 *
 * @author Ari Patriana
 * @since 3.0.0
 */
public record FailoverPolicy(
        int quorumSize,
        SplitBrainPolicy splitBrainPolicy,
        long failoverTimeoutMs
) {

    /**
     * Checks whether the given member count satisfies quorum.
     *
     * @param memberCount current number of cluster members
     * @return {@code true} if quorum is met
     */
    public boolean hasQuorum(int memberCount) {
        return memberCount >= quorumSize;
    }

    /**
     * Split-brain resolution strategies.
     */
    public enum SplitBrainPolicy {

        /**
         * The node that has been MASTER longest keeps the role.
         * Other masters demote to SLAVE.
         * Simplest strategy — works well for 2-node clusters.
         */
        KEEP_OLDEST,

        /**
         * The partition with more members keeps MASTER.
         * Minority partition demotes all nodes to SLAVE.
         * Requires odd total member count for deterministic resolution.
         */
        KEEP_MAJORITY,

        /**
         * Minority partition shuts down entirely.
         * Safest for financial systems — prevents any split-brain processing.
         */
        SHUTDOWN_MINORITY
    }

    /**
     * Parses split-brain policy from config string.
     *
     * @param value config value (e.g. "keep-oldest", "keep-majority", "shutdown-minority")
     * @return parsed policy
     * @throws IllegalArgumentException if value is unknown
     */
    public static SplitBrainPolicy parseSplitBrainPolicy(String value) {
        return switch (value.toLowerCase().replace("-", "_").replace(" ", "_")) {
            case "keep_oldest" -> SplitBrainPolicy.KEEP_OLDEST;
            case "keep_majority" -> SplitBrainPolicy.KEEP_MAJORITY;
            case "shutdown_minority" -> SplitBrainPolicy.SHUTDOWN_MINORITY;
            default -> throw new IllegalArgumentException(
                    "Unknown split-brain-policy: " + value
                            + ". Supported: keep-oldest, keep-majority, shutdown-minority");
        };
    }
}
