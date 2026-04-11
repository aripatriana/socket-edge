package com.socket.edge.core.socket;

import java.util.Collection;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Registry of all {@link ChannelGroup} instances.
 *
 * <p>Provides O(1) lookup by channel name or by socket ID,
 * replacing the implicit name-based lookup that previously
 * required {@link SocketManager}.</p>
 *
 * @author Ari Patriana
 * @since 3.0.0
 */
public class ChannelGroupRegistry {

    private final ConcurrentMap<String, ChannelGroup> byName = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, ChannelGroup> bySocketId = new ConcurrentHashMap<>();

    public void register(ChannelGroup group) {
        byName.put(group.name(), group);
    }

    public void unregister(String name) {
        ChannelGroup removed = byName.remove(name);
        if (removed != null) {
            if (removed.server() != null) {
                bySocketId.remove(removed.server().getId());
            }
            removed.clients().forEach(c -> bySocketId.remove(c.getId()));
        }
    }

    /**
     * Maps a socket ID to its owning ChannelGroup.
     */
    public void mapSocket(String socketId, ChannelGroup group) {
        bySocketId.put(socketId, group);
    }

    public void unmapSocket(String socketId) {
        bySocketId.remove(socketId);
    }

    public ChannelGroup byName(String name) {
        return byName.get(name);
    }

    public ChannelGroup bySocketId(String socketId) {
        return bySocketId.get(socketId);
    }

    public Collection<ChannelGroup> all() {
        return byName.values();
    }
}
