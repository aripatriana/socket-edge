package com.socket.edge.core.socket;

import com.socket.edge.constant.SocketState;
import com.socket.edge.core.CompletionCallback;
import io.netty.channel.Channel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Coordinates lifecycle events between server and client sockets
 * within a {@link ChannelGroup}.
 *
 * <p>This class replaces the implicit orchestration that was previously
 * embedded in {@code ServerInboundHandler} and {@code ClientInboundHandler},
 * eliminating the circular dependency between handlers and {@link SocketManager}.</p>
 *
 * <p>Single Responsibility: lifecycle coordination only.
 * Does NOT handle I/O, parsing, or message routing.</p>
 *
 * @author Ari Patriana
 * @since 3.0.0
 */
public class SocketLifecycleCoordinator {

    private static final Logger log =
            LoggerFactory.getLogger(SocketLifecycleCoordinator.class);

    private final ChannelGroupRegistry registry;

    public SocketLifecycleCoordinator(ChannelGroupRegistry registry) {
        this.registry = registry;
    }

    /**
     * Called when a server socket accepts a new inbound connection.
     * Ensures paired client sockets are connected.
     */
    public void onServerChannelActive(DefaultServerSocket server, Channel inboundChannel) {
        ChannelGroup group = registry.bySocketId(server.getId());
        if (group == null) {
            log.warn("No ChannelGroup for server {}", server.getId());
            inboundChannel.close();
            return;
        }

        List<AbstractSocket> clients = group.clients();
        if (clients.isEmpty()) {
            log.warn("No client sockets in group {}", group.name());
            inboundChannel.close();
            return;
        }

        // Check if clients are already active
        long activeCount = clients.stream()
                .filter(c -> c.getState() == SocketState.ACTIVE)
                .count();

        if (activeCount > 0) {
            activateServerIfNeeded(server);
            return;
        }

        // Trigger connect to all clients, aggregate results
        List<DefaultClientSocket> clientSockets = clients.stream()
                .filter(DefaultClientSocket.class::isInstance)
                .map(DefaultClientSocket.class::cast)
                .toList();

        if (clientSockets.isEmpty()) {
            log.warn("No DefaultClientSocket instances in group {}", group.name());
            inboundChannel.close();
            return;
        }

        AtomicInteger remaining = new AtomicInteger(clientSockets.size());
        AtomicInteger succeeded = new AtomicInteger(0);
        List<DefaultClientSocket> failures = new CopyOnWriteArrayList<>();

        CompletionCallback<DefaultClientSocket> callback = new CompletionCallback<>() {
            @Override
            public void onComplete(DefaultClientSocket client) {
                succeeded.incrementAndGet();
                checkAllDone();
            }

            @Override
            public void onFailure(DefaultClientSocket client) {
                failures.add(client);
                checkAllDone();
            }

            private void checkAllDone() {
                if (remaining.decrementAndGet() > 0) return;

                if (succeeded.get() == 0) {
                    log.warn("All client connects failed for group {}", group.name());
                    inboundChannel.close();
                } else {
                    activateServerIfNeeded(server);
                    failures.forEach(DefaultClientSocket::scheduleReconnect);
                }
            }
        };

        clientSockets.forEach(client -> client.connect(callback));
    }

    /**
     * Called when a server socket channel disconnects.
     * Manages paired client socket lifecycle.
     */
    public void onServerChannelInactive(DefaultServerSocket server, Channel disconnectedChannel) {
        ChannelGroup group = registry.bySocketId(server.getId());
        if (group == null) return;

        // Only react when ALL server channels are gone
        if (!server.channelPool().activeChannels().isEmpty()) {
            return;
        }

        if (server.getState() == SocketState.ACTIVE) {
            server.changeState(SocketState.LISTEN);
            log.info("{} state => LISTEN (no active channels)", server.getId());

            group.clients().forEach(client -> {
                try {
                    client.restart();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    log.error("Interrupted restarting client {}", client.getId());
                }
            });
        }
    }

    /**
     * Called when a client socket loses connection.
     * Determines reconnect strategy based on server socket state.
     */
    public void onClientChannelInactive(DefaultClientSocket client) {
        ChannelGroup group = registry.bySocketId(client.getId());
        if (group == null) {
            client.scheduleReconnect();
            return;
        }

        AbstractSocket server = group.server();
        if (server == null) {
            client.scheduleReconnect();
            return;
        }

        boolean serverHasChannels = !server.channelPool().availableChannels().isEmpty();

        if (serverHasChannels) {
            client.scheduleReconnect();
        } else {
            try {
                client.restart();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private void activateServerIfNeeded(DefaultServerSocket server) {
        if (server.getState() == SocketState.LISTEN) {
            server.changeState(SocketState.ACTIVE);
            log.info("{} state => ACTIVE", server.getId());
        }
    }
}
