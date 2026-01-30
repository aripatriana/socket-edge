package com.socket.edge.core.cluster;

import com.socket.edge.constant.NodeRole;
import com.socket.edge.constant.SocketState;
import com.socket.edge.core.socket.SocketManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SocketClusterAdapter implements ClusterListener {

    private static final Logger log = LoggerFactory.getLogger(SocketClusterAdapter.class);

    private final SocketManager socketManager;

    public SocketClusterAdapter(SocketManager socketManager) {
        this.socketManager = socketManager;
    }

    @Override
    public void changeToMaster() {
        socketManager.getSockets().forEach(socket -> {
            socket.changeRole(NodeRole.MASTER);
            if (socket.getState() == SocketState.ACTIVE) {
                log.info("{} is already ACTIVE, no action on master transition", socket.getId());
            } else if (socket.getState() == SocketState.DOWN) {
                log.info("{} transition to ACTIVE on master role", socket.getId());
                socketManager.start(socket);
            } else if (socket.getState() == SocketState.STANDBY) {
                log.info("{} transition to ACTIVE on master role", socket.getId());
                socketManager.activate(socket);
            }else {
                log.warn("{} in state {}, cannot transition to ACTIVE on master role", socket.getId(), socket.getState());
            }
        });
    }

    @Override
    public void changeToSlave() {
        socketManager.getSockets().forEach(socket -> {
            socket.changeRole(NodeRole.SLAVE);
            if (socket.getState() == SocketState.STANDBY) {
                log.info("{} is already in STANDBY, no action on slave transition", socket.getId());
            } else if (socket.getState() == SocketState.DOWN) {
                log.info("{} transition to STANDBY on slave role", socket.getId());
                socketManager.start(socket);
            } else if (socket.getState() == SocketState.ACTIVE) {
                log.info("{} transition to STANDBY on slave role", socket.getId());
                socketManager.standby(socket);
            } else {
                log.warn("{} in state {}, cannot transition to STANDBY on slave role", socket.getId(), socket.getState());
            }
        });
    }
}
