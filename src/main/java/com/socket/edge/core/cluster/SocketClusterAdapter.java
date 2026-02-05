package com.socket.edge.core.cluster;

import com.socket.edge.constant.NodeRole;
import com.socket.edge.constant.SocketState;
import com.socket.edge.core.socket.SocketManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Cluster listener adapter that coordinates socket lifecycle
 * based on node role changes.
 *
 * <p>{@code SocketClusterAdapter} bridges cluster role events
 * ({@link NodeRole#MASTER} / {@link NodeRole#SLAVE}) with
 * {@link AbstractSocket} state transitions.</p>
 *
 * <p>Responsibilities:
 * <ul>
 *   <li>Promote sockets to ACTIVE when node becomes MASTER</li>
 *   <li>Demote sockets to STANDBY when node becomes SLAVE</li>
 *   <li>Ensure socket role and state remain consistent</li>
 * </ul>
 *
 * <p>State transition rules (simplified):
 * <ul>
 *   <li>MASTER:
 *     <ul>
 *       <li>DOWN    → start → ACTIVE</li>
 *       <li>STANDBY → activate → ACTIVE</li>
 *       <li>ACTIVE  → no-op</li>
 *     </ul>
 *   </li>
 *   <li>SLAVE:
 *     <ul>
 *       <li>DOWN    → start → STANDBY</li>
 *       <li>ACTIVE  → standby → STANDBY</li>
 *       <li>STANDBY → no-op</li>
 *     </ul>
 *   </li>
 * </ul>
 *
 * <p>This adapter ensures that only the MASTER node
 * actively handles traffic, while SLAVE nodes remain
 * in standby mode for fast failover.</p>
 *
 * <p>Implementations should be fast and non-blocking,
 * as role changes may affect multiple sockets at once.</p>
 *
 * @author Ari Patriana
 * @since 1.0.0
 */
public class SocketClusterAdapter implements ClusterListener {

    private static final Logger log = LoggerFactory.getLogger(SocketClusterAdapter.class);

    /**
     * Socket manager responsible for controlling socket lifecycle.
     */
    private final SocketManager socketManager;

    /**
     * Creates a new cluster adapter for socket lifecycle coordination.
     *
     * @param socketManager socket manager
     */
    public SocketClusterAdapter(SocketManager socketManager) {
        this.socketManager = socketManager;
    }

    /**
     * Handles cluster role change events.
     *
     * @param nodeRole new node role
     */
    @Override
    public void onRoleChanged(NodeRole nodeRole) {
        if (nodeRole == NodeRole.MASTER) {
            onMasterEvent();
        } else {
            onSlaveEvent();
        }
    }

    /**
     * Handles transition when the node becomes MASTER.
     *
     * <p>All sockets are promoted to ACTIVE state
     * where applicable.</p>
     */
    private void onMasterEvent() {
        socketManager.getSockets().forEach(socket -> {
            socket.changeRole(NodeRole.MASTER);

            if (socket.getState() == SocketState.ACTIVE) {
                log.info(
                        "{} is already ACTIVE, no action on master transition",
                        socket.getId()
                );
            } else if (socket.getState() == SocketState.DOWN) {
                log.info(
                        "{} transition to ACTIVE on master role",
                        socket.getId()
                );
                socketManager.start(socket);
            } else if (socket.getState() == SocketState.STANDBY) {
                log.info(
                        "{} transition to ACTIVE on master role",
                        socket.getId()
                );
                socketManager.activate(socket);
            } else {
                log.warn(
                        "{} in state {}, cannot transition to ACTIVE on master role",
                        socket.getId(),
                        socket.getState()
                );
            }
        });
    }

    /**
     * Handles transition when the node becomes SLAVE.
     *
     * <p>All sockets are demoted to STANDBY state
     * where applicable.</p>
     */
    private void onSlaveEvent() {
        socketManager.getSockets().forEach(socket -> {
            socket.changeRole(NodeRole.SLAVE);

            if (socket.getState() == SocketState.STANDBY) {
                log.info(
                        "{} is already in STANDBY, no action on slave transition",
                        socket.getId()
                );
            } else if (socket.getState() == SocketState.DOWN) {
                log.info(
                        "{} transition to STANDBY on slave role",
                        socket.getId()
                );
                socketManager.start(socket);
            } else if (socket.getState() == SocketState.ACTIVE) {
                log.info(
                        "{} transition to STANDBY on slave role",
                        socket.getId()
                );
                socketManager.standby(socket);
            } else {
                log.warn(
                        "{} in state {}, cannot transition to STANDBY on slave role",
                        socket.getId(),
                        socket.getState()
                );
            }
        });
    }
}
