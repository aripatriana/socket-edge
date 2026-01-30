package com.socket.edge.core.cluster;

public interface ClusterListener {
    void changeToMaster();
    void changeToSlave();
}
