package com.socket.edge.core.cluster;

import com.socket.edge.constant.NodeRole;

public interface ClusterListener {
    void onRoleChanged(NodeRole nodeRole);

}
