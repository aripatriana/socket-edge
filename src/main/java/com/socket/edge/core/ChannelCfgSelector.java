package com.socket.edge.core;

import com.socket.edge.model.ChannelCfg;
import com.socket.edge.constant.SocketType;
import com.socket.edge.model.ClientChannel;
import com.socket.edge.model.ServerChannel;

import java.net.InetSocketAddress;
import java.util.List;
import java.util.Objects;

/**
 * Selects the appropriate {@link ChannelCfg} based on socket context.
 *
 * <p>{@code ChannelCfgSelector} resolves a channel configuration
 * by matching runtime socket attributes against configured
 * channel definitions.</p>
 *
 * <p>Selection is performed using:
 * <ul>
 *   <li>Channel name</li>
 *   <li>Socket type (SERVER or CLIENT)</li>
 *   <li>Local port</li>
 *   <li>Remote host and port</li>
 * </ul>
 *
 * <p>The selection logic is deterministic:
 * <ul>
 *   <li>Only the first matching channel is returned</li>
 *   <li>Ambiguous or missing matches result in failure</li>
 * </ul>
 *
 * <p>This class is stateless and thread-safe.</p>
 *
 * @author Ari Patriana
 * @since 1.0.0
 */
public final class ChannelCfgSelector {

    /**
     * Selects a matching {@link ChannelCfg}.
     *
     * @param channelName logical channel name
     * @param socketType  socket type (SERVER or CLIENT)
     * @param local       local socket address
     * @param remote      remote socket address
     * @param channelCfgs available channel configurations
     * @return matched channel configuration
     * @throws NullPointerException  if required parameters are {@code null}
     * @throws IllegalStateException if no channel matches the given criteria
     */
    public ChannelCfg select(
            String channelName,
            SocketType socketType,
            InetSocketAddress local,
            InetSocketAddress remote,
            List<ChannelCfg> channelCfgs
    ) {
        Objects.requireNonNull(channelCfgs, "channelCfgs");
        Objects.requireNonNull(local, "local");
        Objects.requireNonNull(remote, "remote");
        Objects.requireNonNull(socketType, "socketType");

        int localPort = local.getPort();
        String remoteHost = remote.getHostString();
        int remotePort = remote.getPort();

        return channelCfgs.stream()
                .filter(ch -> ch.name().equals(channelName))
                .filter(ch -> matches(ch, socketType, localPort, remoteHost, remotePort))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "No channel matched for localPort="
                                + localPort
                                + ", remoteHost=" + remoteHost
                                + ", remotePort=" + remotePort
                ));
    }

    /**
     * Determines whether a channel configuration matches
     * the given socket attributes.
     */
    private boolean matches(
            ChannelCfg ch,
            SocketType socketType,
            int localPort,
            String remoteHost,
            int remotePort
    ) {
        return switch (socketType) {
            case SERVER -> matchesServer(ch.server(), localPort, remoteHost);
            case CLIENT -> matchesClient(ch.client(), remoteHost, remotePort);
        };
    }

    /**
     * Matches a server channel configuration.
     *
     * <p>A server channel matches when:
     * <ul>
     *   <li>The server listens on the given local port</li>
     *   <li>The remote host exists in the server pool</li>
     * </ul>
     */
    private boolean matchesServer(
            ServerChannel server,
            int localPort,
            String remoteHost
    ) {
        return server != null
                && server.listenPort() == localPort
                && server.pool().stream()
                .anyMatch(p ->
                        p.host().equalsIgnoreCase(remoteHost)
                );
    }

    /**
     * Matches a client channel configuration.
     *
     * <p>A client channel matches when:
     * <ul>
     *   <li>The remote host and port match a configured endpoint</li>
     * </ul>
     */
    private boolean matchesClient(
            ClientChannel client,
            String remoteHost,
            int remotePort
    ) {
        return client != null
                && client.endpoints().stream()
                .anyMatch(e ->
                        e.host().equalsIgnoreCase(remoteHost)
                                && e.port() == remotePort
                );
    }
}

