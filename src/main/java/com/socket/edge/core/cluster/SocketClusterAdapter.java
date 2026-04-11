package com.socket.edge.core.cluster;

import com.socket.edge.constant.NodeRole;
import com.socket.edge.constant.SocketState;
import com.socket.edge.core.socket.AbstractSocket;
import com.socket.edge.core.socket.DefaultClientSocket;
import com.socket.edge.core.socket.SocketManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Cluster listener adapter that coordinates socket lifecycle
 * based on node role changes.
 *
 * @author Ari Patriana
 * @since 3.0.0
 */
public class SocketClusterAdapter implements ClusterListener {

    private static final Logger log = LoggerFactory.getLogger(SocketClusterAdapter.class);

    private final SocketManager socketManager;

    public SocketClusterAdapter(SocketManager socketManager) {
        this.socketManager = socketManager;
    }

    @Override
    public void onRoleChanged(NodeRole nodeRole) {
        if (nodeRole == NodeRole.MASTER) {
            onMasterEvent();
        } else {
            onSlaveEvent();
        }
    }

    private void onMasterEvent() {
        socketManager.getSockets().forEach(socket -> {
            socket.changeRole(NodeRole.MASTER);

            if (socket.getState() == SocketState.ACTIVE) {
                log.info("{} is already ACTIVE, no action on master transition", socket.getId());
            } else if (socket.getState() == SocketState.DOWN
                    || socket.getState() == SocketState.STANDBY
                    || socket.getState() == SocketState.ERROR) {
                log.info("{} transition to {} on master role",
                        socket.getId(),
                        socket instanceof DefaultClientSocket ? "WAIT" : "LISTEN");
                socketManager.start(socket);
            } else {
                log.warn("{} in state {}, cannot transition on master role",
                        socket.getId(), socket.getState());
            }
        });
    }

    private void onSlaveEvent() {
        socketManager.getSockets().forEach(socket -> {
            socket.changeRole(NodeRole.SLAVE);

            if (socket.getState() == SocketState.STANDBY) {
                log.info("{} is already in STANDBY", socket.getId());
            } else if (socket.getState() == SocketState.DOWN) {
                log.info("{} transition to STANDBY on slave role", socket.getId());
                socketManager.start(socket);
            } else if (socket.getState() == SocketState.ACTIVE) {
                log.info("{} transition to STANDBY on slave role", socket.getId());
                socketManager.restart(socket);
            } else {
                log.warn("{} in state {}, cannot transition on slave role",
                        socket.getId(), socket.getState());
            }
        });
    }
}
