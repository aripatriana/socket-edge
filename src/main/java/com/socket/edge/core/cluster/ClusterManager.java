package com.socket.edge.core.cluster;

import com.socket.edge.constant.ClusterState;
import com.socket.edge.constant.NodeRole;
import com.socket.edge.model.FailoverPolicy;
import com.socket.edge.model.FailoverPolicy.SplitBrainPolicy;
import com.socket.edge.model.RolePolicy;
import org.jgroups.*;
import org.jgroups.util.Util;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Manages cluster membership and node role (MASTER / SLAVE)
 * using JGroups.
 *
 * <p>v3.0 additions:
 * <ul>
 *   <li><b>Quorum enforcement</b> — MASTER election only proceeds when
 *       {@code view.size() >= quorumSize}. Below quorum, existing MASTER
 *       is demoted to prevent split-brain operation.</li>
 *   <li><b>Split-brain policy</b> — when a view change suggests a partition
 *       heal (view size increases), the policy determines which node retains
 *       MASTER role.</li>
 *   <li><b>Failover timeout</b> — when MASTER leaves the view, SLAVE waits
 *       {@code failoverTimeoutMs} before promoting. This prevents premature
 *       promotion during transient network glitches.</li>
 * </ul>
 *
 * <h3>View change flow:</h3>
 * <pre>
 *   viewAccepted(newView)
 *       │
 *       ├── view.size() < quorumSize?
 *       │       YES → demote to SLAVE ("quorum lost")
 *       │             no election allowed
 *       │       NO  ↓
 *       ├── am I coordinator?
 *       │       NO  → transitionToSlave
 *       │       YES ↓
 *       ├── was I already MASTER?
 *       │       YES → stay MASTER (no-op)
 *       │       NO  ↓
 *       └── schedule promotion after failoverTimeout
 *               ↓ (after delay)
 *           re-check: still coordinator? still have quorum?
 *               YES → transitionToMaster
 *               NO  → cancel, stay SLAVE
 * </pre>
 *
 * @author Ari Patriana
 * @since 3.0.0
 */
public class ClusterManager {

    private static final Logger log = LoggerFactory.getLogger(ClusterManager.class);

    private final JChannel channel;
    private final ClusterListener listener;
    private final RolePolicy rolePolicy;
    private final FailoverPolicy failoverPolicy;
    private final String clusterName;

    private final AtomicReference<ClusterState> state =
            new AtomicReference<>(ClusterState.STARTING);

    /**
     * Timestamp when this node became MASTER (epoch ms).
     * Used for KEEP_OLDEST split-brain resolution.
     */
    private volatile long masterSince = 0;

    /**
     * Total expected members (from config).
     * Used for KEEP_MAJORITY split-brain check.
     */
    private final int totalExpectedMembers;

    /**
     * Scheduler for delayed failover promotion.
     */
    private final ScheduledExecutorService failoverScheduler =
            Executors.newSingleThreadScheduledExecutor(r ->
                    new Thread(r, "cluster-failover"));

    /**
     * Pending failover promotion task (if any).
     */
    private volatile ScheduledFuture<?> pendingPromotion;

    public ClusterManager(
            JChannel channel,
            RolePolicy rolePolicy,
            FailoverPolicy failoverPolicy,
            ClusterListener listener,
            String clusterName,
            int totalExpectedMembers
    ) {
        this.channel = channel;
        this.rolePolicy = rolePolicy;
        this.failoverPolicy = failoverPolicy;
        this.listener = listener;
        this.clusterName = clusterName;
        this.totalExpectedMembers = totalExpectedMembers;
    }

    public void start() {
        try {
            if (!rolePolicy.allowMasterElection()) {
                transitionToSlave("configured as SLAVE");
            }

            channel.setReceiver(new Receiver() {
                @Override
                public void viewAccepted(View newView) {
                    log.info("JGroups view changed: {} (size={})", newView, newView.size());
                    handleViewChange(newView);
                }
            });

            channel.connect(clusterName);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to start cluster manager", e);
        }
    }

    /**
     * Core view change handler — enforces quorum and failover policy.
     */
    private synchronized void handleViewChange(View view) {
        int memberCount = view.size();

        // --- QUORUM CHECK ---
        if (!failoverPolicy.hasQuorum(memberCount)) {
            log.warn("Quorum lost: members={}, required={}",
                    memberCount, failoverPolicy.quorumSize());

            cancelPendingPromotion();

            if (state.get() == ClusterState.MASTER) {
                // Demote: operating without quorum is dangerous
                log.error("MASTER demoted — quorum lost. "
                        + "No traffic will be processed until quorum is restored.");
                transitionToSlave("quorum lost (members="
                        + memberCount + ", required=" + failoverPolicy.quorumSize() + ")");
            }
            return;
        }

        // --- ROLE POLICY CHECK ---
        if (!rolePolicy.allowMasterElection()) {
            transitionToSlave("configured as SLAVE");
            return;
        }

        boolean isCoordinator = view.getCoord().equals(channel.getAddress());

        if (!isCoordinator) {
            cancelPendingPromotion();

            if (state.get() == ClusterState.MASTER) {
                // I was MASTER but I'm no longer coordinator — split-brain resolved
                handleSplitBrainDemotion(view);
            } else {
                transitionToSlave("not coordinator");
            }
            return;
        }

        // --- I AM COORDINATOR ---
        if (state.get() == ClusterState.MASTER) {
            // Already MASTER, still coordinator — no-op
            log.debug("Still MASTER and coordinator, no action needed");
            return;
        }

        // --- FAILOVER: schedule delayed promotion ---
        if (state.get() == ClusterState.STARTING) {
            // First startup — promote immediately (no delay)
            promoteIfStillValid(view, "initial-election");
        } else {
            // SLAVE → MASTER promotion — apply failover timeout
            schedulePromotion(view);
        }
    }

    /**
     * Schedules a delayed MASTER promotion.
     * Re-validates quorum and coordinator status after the delay.
     */
    private void schedulePromotion(View triggerView) {
        cancelPendingPromotion();

        long delay = failoverPolicy.failoverTimeoutMs();

        log.info("Scheduling MASTER promotion in {}ms (failover-timeout)", delay);

        pendingPromotion = failoverScheduler.schedule(() -> {
            synchronized (ClusterManager.this) {
                View currentView = channel.getView();
                if (currentView == null) {
                    log.warn("Promotion cancelled: no current view");
                    return;
                }
                promoteIfStillValid(currentView, "failover-promotion");
            }
        }, delay, TimeUnit.MILLISECONDS);
    }

    /**
     * Promotes to MASTER only if still coordinator and quorum is met.
     */
    private void promoteIfStillValid(View view, String reason) {
        if (!failoverPolicy.hasQuorum(view.size())) {
            log.warn("Promotion cancelled: quorum lost (members={}, required={})",
                    view.size(), failoverPolicy.quorumSize());
            transitionToSlave("quorum lost during promotion");
            return;
        }

        boolean stillCoordinator = view.getCoord().equals(channel.getAddress());
        if (!stillCoordinator) {
            log.warn("Promotion cancelled: no longer coordinator");
            transitionToSlave("lost coordinator during promotion");
            return;
        }

        if (rolePolicy.failIfNotMaster()
                && state.get() != ClusterState.STARTING
                && state.get() != ClusterState.PROMOTING) {
            throw new IllegalStateException(
                    "Node configured as MASTER (strict), but master already exists");
        }

        transitionToMaster(reason);
    }

    /**
     * Handles demotion when split-brain is detected.
     * This node was MASTER but is no longer coordinator — another node
     * also thinks it's coordinator (split-brain resolved by JGroups merge).
     */
    private void handleSplitBrainDemotion(View view) {
        SplitBrainPolicy policy = failoverPolicy.splitBrainPolicy();

        switch (policy) {
            case KEEP_OLDEST -> {
                // JGroups coordinator = the oldest node. Since I'm NOT coordinator,
                // the other partition's master is older — I demote.
                log.warn("Split-brain resolved (keep-oldest): "
                        + "demoting to SLAVE — other master is older");
                transitionToSlave("split-brain: keep-oldest, not coordinator");
            }
            case KEEP_MAJORITY -> {
                // Check if I'm in the majority partition
                int myPartitionSize = view.size();
                boolean isMajority = myPartitionSize > (totalExpectedMembers / 2);

                if (isMajority) {
                    log.info("Split-brain resolved (keep-majority): "
                            + "I'm in majority partition ({}/{}), but not coordinator — demoting",
                            myPartitionSize, totalExpectedMembers);
                }
                // Either way, non-coordinator demotes
                transitionToSlave("split-brain: keep-majority, not coordinator");
            }
            case SHUTDOWN_MINORITY -> {
                int myPartitionSize = view.size();
                boolean isMinority = myPartitionSize <= (totalExpectedMembers / 2);

                if (isMinority) {
                    log.error("Split-brain resolved (shutdown-minority): "
                            + "I'm in minority partition ({}/{}). SHUTTING DOWN.",
                            myPartitionSize, totalExpectedMembers);
                    state.set(ClusterState.SHUTDOWN);
                    listener.onRoleChanged(NodeRole.SLAVE);
                    // Signal application to shut down
                    Util.close(channel);
                    throw new IllegalStateException(
                            "Minority partition shutdown — split-brain-policy=shutdown-minority");
                } else {
                    transitionToSlave("split-brain: shutdown-minority, not coordinator");
                }
            }
        }
    }

    private void transitionToMaster(String reason) {
        ClusterState prev = state.get();
        if (prev == ClusterState.MASTER) return;

        // Try CAS from STARTING or SLAVE → PROMOTING → MASTER
        boolean promoting = state.compareAndSet(ClusterState.STARTING, ClusterState.PROMOTING)
                || state.compareAndSet(ClusterState.SLAVE, ClusterState.PROMOTING);

        if (!promoting && prev != ClusterState.PROMOTING) {
            log.warn("Cannot promote from state {}", prev);
            return;
        }

        if (!state.compareAndSet(ClusterState.PROMOTING, ClusterState.MASTER)) {
            return;
        }

        masterSince = System.currentTimeMillis();
        listener.onRoleChanged(NodeRole.MASTER);
        log.info("State => MASTER ({}) masterSince={}", reason, masterSince);
    }

    private void transitionToSlave(String reason) {
        if (state.get() == ClusterState.SLAVE) return;

        state.set(ClusterState.SLAVE);
        masterSince = 0;
        listener.onRoleChanged(NodeRole.SLAVE);
        log.info("State => SLAVE ({})", reason);
    }

    private void cancelPendingPromotion() {
        ScheduledFuture<?> pending = pendingPromotion;
        if (pending != null && !pending.isDone()) {
            pending.cancel(false);
            log.debug("Pending promotion cancelled");
        }
        pendingPromotion = null;
    }

    public boolean isMaster() {
        return state.get() == ClusterState.MASTER;
    }

    public ClusterState getState() {
        return state.get();
    }

    public long getMasterSince() {
        return masterSince;
    }

    public void shutdown() {
        cancelPendingPromotion();
        failoverScheduler.shutdownNow();
        state.set(ClusterState.SHUTDOWN);
        Util.close(channel);
        log.info("ClusterManager shutdown");
    }
}
