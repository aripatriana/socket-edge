package com.socket.edge.core.cluster;

import com.socket.edge.constant.ClusterState;
import com.socket.edge.constant.NodeRole;
import com.socket.edge.model.RolePolicy;
import com.socket.edge.utils.ConfigUtil;
import org.jgroups.*;
import org.jgroups.util.Util;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicReference;

/**
 * Manages cluster membership and node role (MASTER / SLAVE)
 * using JGroups.
 *
 * <p>{@code ClusterManager} is responsible for:
 * <ul>
 *   <li>Joining a JGroups cluster</li>
 *   <li>Electing MASTER or SLAVE role</li>
 *   <li>Handling cluster view changes</li>
 *   <li>Notifying {@link ClusterListener} on role transitions</li>
 * </ul>
 *
 * <p>Leader election strategy:
 * <ul>
 *   <li>The JGroups coordinator is elected as MASTER</li>
 *   <li>Only one MASTER exists at a time</li>
 *   <li>Other nodes become SLAVE</li>
 * </ul>
 *
 * <p>Role election is influenced by {@link RolePolicy}, allowing:
 * <ul>
 *   <li>Forced SLAVE mode</li>
 *   <li>Strict MASTER-only startup</li>
 *   <li>Fail-fast behavior when MASTER already exists</li>
 * </ul>
 *
 * <p>State machine:
 * <pre>
 * STARTING → PROMOTING → MASTER
 *     └──────────────→ SLAVE
 * </pre>
 *
 * <p>This class is thread-safe and designed to be used
 * as a singleton within the application.</p>
 *
 * @author Ari Patriana
 * @since 1.0.0
 */
public class ClusterManager {

    private static final Logger log = LoggerFactory.getLogger(ClusterManager.class);

    /**
     * Configuration utility.
     */
    private final ConfigUtil cu = new ConfigUtil();

    /**
     * JGroups channel for cluster communication.
     */
    private final JChannel channel;

    /**
     * Listener notified on role changes.
     */
    private final ClusterListener listener;

    /**
     * Policy defining role election behavior.
     */
    private final RolePolicy rolePolicy;

    /**
     * Current cluster state.
     */
    private final AtomicReference<ClusterState> state =
            new AtomicReference<>(ClusterState.STARTING);

    /**
     * Creates a new ClusterManager.
     *
     * @param channel    JGroups channel
     * @param rolePolicy role election policy
     * @param listener   role change listener
     */
    public ClusterManager(
            JChannel channel,
            RolePolicy rolePolicy,
            ClusterListener listener
    ) {
        this.channel = channel;
        this.rolePolicy = rolePolicy;
        this.listener = listener;
    }

    /**
     * Starts the cluster manager and joins the cluster.
     *
     * <p>Registers a JGroups receiver to listen for
     * view changes and trigger leader election.</p>
     *
     * @throws IllegalStateException if startup fails
     */
    public void start() {
        try {
            if (!rolePolicy.allowMasterElection()) {
                transitionToSlave("configured as SLAVE");
            }

            channel.setReceiver(new Receiver() {
                @Override
                public void viewAccepted(View new_view) {
                    log.info("JGroups view changed: {}", new_view);
                    electInitialRole(new_view);
                }
            });

            channel.connect(
                    cu.getString(
                            "cluster.cluster-name",
                            "socket-edge-cluster"
                    )
            );
        } catch (Exception e) {
            throw new IllegalStateException(
                    "Failed to start cluster manager",
                    e
            );
        }
    }

    /**
     * Indicates whether this node is currently MASTER.
     *
     * @return {@code true} if MASTER
     */
    public boolean isMaster() {
        return state.get() == ClusterState.MASTER;
    }

    /**
     * Returns the current cluster state.
     *
     * @return cluster state
     */
    public ClusterState getState() {
        return state.get();
    }

    /**
     * Performs initial role election based on cluster view.
     *
     * @param view current cluster view
     */
    private void electInitialRole(View view) {
        if (tryAcquireMaster(view, "initial-election")) {
            return;
        }

        if (rolePolicy.failIfNotMaster()) {
            throw new IllegalStateException(
                    "Node configured as MASTER (strict), but master already exists"
            );
        }

        transitionToSlave("master already elected");
    }

    /**
     * Attempts to acquire MASTER role.
     *
     * @param view   current cluster view
     * @param reason transition reason
     * @return {@code true} if MASTER role is acquired
     */
    private boolean tryAcquireMaster(View view, String reason) {
        if (state.get() == ClusterState.MASTER) {
            return true;
        }

        boolean promoting =
                state.compareAndSet(
                        ClusterState.STARTING,
                        ClusterState.PROMOTING
                )
                        || state.compareAndSet(
                        ClusterState.SLAVE,
                        ClusterState.PROMOTING
                );

        if (!promoting) {
            return false;
        }

        boolean coordinator =
                view.getCoord().equals(channel.getAddress());

        if (!coordinator) {
            transitionToSlave("not coordinator");
            return false;
        }

        transitionToMaster(reason);
        return true;
    }

    /**
     * Transitions the node to MASTER role.
     *
     * @param reason transition reason
     */
    private void transitionToMaster(String reason) {
        if (!state.compareAndSet(
                ClusterState.PROMOTING,
                ClusterState.MASTER
        )) {
            return;
        }

        listener.onRoleChanged(NodeRole.MASTER);
        log.info("State => MASTER ({})", reason);
    }

    /**
     * Transitions the node to SLAVE role.
     *
     * @param reason transition reason
     */
    private void transitionToSlave(String reason) {
        if (state.get() == ClusterState.SLAVE) {
            return;
        }

        state.set(ClusterState.SLAVE);
        listener.onRoleChanged(NodeRole.SLAVE);
        log.info("State => SLAVE ({})", reason);
    }

    /**
     * Shuts down the cluster manager and leaves the cluster.
     */
    public void shutdown() {
        state.set(ClusterState.SHUTDOWN);
        Util.close(channel);
        log.info("ClusterManager shutdown");
    }
}
