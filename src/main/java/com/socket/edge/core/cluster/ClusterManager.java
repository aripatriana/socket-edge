package com.socket.edge.core.cluster;

import com.socket.edge.constant.ClusterState;
import com.socket.edge.constant.NodeRole;
import com.socket.edge.model.RolePolicy;
import org.jgroups.*;
import org.jgroups.util.Util;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicReference;

/**
 * Manages cluster membership and node role (MASTER / SLAVE)
 * using JGroups.
 *
 * <p>v3.0: Removed ConfigUtil dependency. Cluster name is passed
 * via constructor.</p>
 *
 * @author Ari Patriana
 * @since 3.0.0
 */
public class ClusterManager {

    private static final Logger log = LoggerFactory.getLogger(ClusterManager.class);

    private final JChannel channel;
    private final ClusterListener listener;
    private final RolePolicy rolePolicy;
    private final String clusterName;

    private final AtomicReference<ClusterState> state =
            new AtomicReference<>(ClusterState.STARTING);

    public ClusterManager(
            JChannel channel,
            RolePolicy rolePolicy,
            ClusterListener listener,
            String clusterName
    ) {
        this.channel = channel;
        this.rolePolicy = rolePolicy;
        this.listener = listener;
        this.clusterName = clusterName;
    }

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

            channel.connect(clusterName);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to start cluster manager", e);
        }
    }

    public boolean isMaster() {
        return state.get() == ClusterState.MASTER;
    }

    public ClusterState getState() {
        return state.get();
    }

    private void electInitialRole(View view) {
        if (tryAcquireMaster(view, "initial-election")) {
            return;
        }

        if (rolePolicy.failIfNotMaster()) {
            throw new IllegalStateException(
                    "Node configured as MASTER (strict), but master already exists");
        }

        transitionToSlave("master already elected");
    }

    private boolean tryAcquireMaster(View view, String reason) {
        if (state.get() == ClusterState.MASTER) {
            return true;
        }

        boolean promoting =
                state.compareAndSet(ClusterState.STARTING, ClusterState.PROMOTING)
                        || state.compareAndSet(ClusterState.SLAVE, ClusterState.PROMOTING);

        if (!promoting) {
            return false;
        }

        boolean coordinator = view.getCoord().equals(channel.getAddress());

        if (!coordinator) {
            transitionToSlave("not coordinator");
            return false;
        }

        transitionToMaster(reason);
        return true;
    }

    private void transitionToMaster(String reason) {
        if (!state.compareAndSet(ClusterState.PROMOTING, ClusterState.MASTER)) {
            return;
        }
        listener.onRoleChanged(NodeRole.MASTER);
        log.info("State => MASTER ({})", reason);
    }

    private void transitionToSlave(String reason) {
        if (state.get() == ClusterState.SLAVE) {
            return;
        }
        state.set(ClusterState.SLAVE);
        listener.onRoleChanged(NodeRole.SLAVE);
        log.info("State => SLAVE ({})", reason);
    }

    public void shutdown() {
        state.set(ClusterState.SHUTDOWN);
        Util.close(channel);
        log.info("ClusterManager shutdown");
    }
}
