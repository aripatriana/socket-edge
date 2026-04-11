package com.socket.edge.core.socket;

import com.socket.edge.constant.SocketState;
import com.socket.edge.model.ChannelCfg;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Represents a logical channel group — one server socket
 * paired with zero or more client sockets.
 *
 * <p>This is the missing abstraction that eliminates the need
 * for Netty handlers to query {@link SocketManager} for
 * related sockets. The server↔client relationship is now
 * explicit and traversable without global lookup.</p>
 *
 * <p>Thread safety: Uses {@link CopyOnWriteArrayList} for
 * the client socket list.</p>
 *
 * @author Ari Patriana
 * @since 3.0.0
 */
public class ChannelGroup {

    private final String name;
    private final ChannelCfg config;
    private volatile AbstractSocket server;
    private final List<AbstractSocket> clients = new CopyOnWriteArrayList<>();

    public ChannelGroup(String name, ChannelCfg config) {
        this.name = name;
        this.config = config;
    }

    public String name() {
        return name;
    }

    public ChannelCfg config() {
        return config;
    }

    public AbstractSocket server() {
        return server;
    }

    public List<AbstractSocket> clients() {
        return Collections.unmodifiableList(clients);
    }

    public void setServer(AbstractSocket server) {
        this.server = server;
    }

    public void addClient(AbstractSocket client) {
        clients.add(client);
    }

    public void removeClient(AbstractSocket client) {
        clients.remove(client);
    }

    /**
     * Returns client sockets that are in ACTIVE or WAIT state.
     */
    public List<AbstractSocket> activeClients() {
        return clients.stream()
                .filter(s -> s.getState() == SocketState.ACTIVE
                        || s.getState() == SocketState.WAIT)
                .toList();
    }
}
