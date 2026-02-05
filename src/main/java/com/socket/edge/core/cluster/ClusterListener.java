package com.socket.edge.core.cluster;

import com.socket.edge.constant.NodeRole;

/**
 * Listener interface for cluster role change events.
 *
 * <p>{@code ClusterListener} is notified whenever the node role
 * in a clustered environment changes, for example from
 * {@link NodeRole#SLAVE} to {@link NodeRole#MASTER} or vice versa.</p>
 *
 * <p>Typical use cases:
 * <ul>
 *   <li>Activating or deactivating sockets</li>
 *   <li>Starting or stopping message processing</li>
 *   <li>Adjusting traffic flow based on node role</li>
 * </ul>
 *
 * <p>Implementations should return quickly and avoid blocking
 * operations, as role changes may be processed synchronously.</p>
 *
 * @author Ari Patriana
 * @since 1.0.0
 */
public interface ClusterListener {

    /**
     * Invoked when the node role has changed.
     *
     * @param nodeRole new node role
     */
    void onRoleChanged(NodeRole nodeRole);
}
