package com.socket.edge.http.service;

import com.socket.edge.core.ChannelCfgProcessor;
import com.socket.edge.core.MetadataHolder;
import com.socket.edge.core.socket.AbstractSocket;
import com.socket.edge.core.socket.SocketManager;
import com.socket.edge.model.ChannelCfg;
import com.socket.edge.model.Metadata;
import com.socket.edge.model.SocketEndpoint;
import com.socket.edge.model.helper.ChannelCfgDiff;
import com.socket.edge.model.helper.ClientChannelDiff;
import com.socket.edge.model.helper.MetadataDiff;
import com.socket.edge.model.helper.ServerChannelDiff;
import com.socket.edge.utils.CommonUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

public class ReloadCfgService {

    private static final Logger log = LoggerFactory.getLogger(ReloadCfgService.class);

    private final SocketManager socketManager;
    private final MetadataHolder metadataHolder;
    private final ChannelCfgProcessor channelCfgProcessor;
    private static final String CHANNEL_CONF = "channel.conf";

    public ReloadCfgService(SocketManager socketManager, MetadataHolder metadataHolder, ChannelCfgProcessor channelCfgProcessor) {
        this.socketManager = socketManager;
        this.metadataHolder = metadataHolder;
        this.channelCfgProcessor = channelCfgProcessor;
    }

    private Path channelConfigPath() {
        return Path.of(
                System.getProperty("base.dir"),
                "conf",
                CHANNEL_CONF
        );
    }
    public MetadataDiff validate() throws IOException {
        try {
            Metadata newMetadata = channelCfgProcessor.process(channelConfigPath());
            Objects.requireNonNull(newMetadata, "Invalid channel.conf");
            return metadataHolder.get().diffWith(newMetadata);
        } catch (Exception e) {
            log.error("Failed to validate channel config", e);
            throw new RuntimeException("Failed to validate channel configuration", e);
        }
    }

    public void reload() throws IOException {
        try {
            Metadata newMetadata = channelCfgProcessor.process(channelConfigPath());
            Objects.requireNonNull(newMetadata, "Invalid channel.conf");
            reloadConfig(newMetadata);
        } catch (Exception e) {
            log.error("Failed to reload channel config", e);
            throw new RuntimeException("Failed to reload channel configuration", e);
        }
    }

    public synchronized void reloadConfig(Metadata newMetadata) {
        log.info("Reloading channel configuration...");

        MetadataDiff diff = metadataHolder.get().diffWith(newMetadata);

        try {
            handleDeletedChannels(diff);
            handleAddedChannels(diff);
            handleModifiedChannels(diff);

            metadataHolder.replaceWith(newMetadata);
            log.info("Channel configuration reload completed successfully");

        } catch (Exception e) {
            log.error("Failed to reload channel configuration. Metadata not replaced.", e);
            throw new RuntimeException("Failed to reload channel configuration", e);
        }
    }

    private void handleDeletedChannels(MetadataDiff diff) {
        diff.deletedChannelCfgs().forEach(cfg -> {
            log.info("Deleted channel config detected: {}", cfg.name());
            socketManager.destroySocket(cfg);
        });
    }

    private void handleAddedChannels(MetadataDiff diff) {
        diff.addedChannelCfgs().forEach(cfg -> {
            log.info("Added channel config detected: {}", cfg.name());
            List<AbstractSocket> sockets = socketManager.createSocket(cfg);
            socketManager.startAll(sockets);
        });
    }

    private void handleModifiedChannels(MetadataDiff diff) {
        diff.modifiedChannelCfg().forEach(channelDiff -> {
            handleClientChannelDiff(channelDiff);
            handleServerChannelDiff(channelDiff);
        });
    }

    private void handleClientChannelDiff(ChannelCfgDiff channelDiff) {
        ChannelCfg newCfg = channelDiff.newCfg();
        ClientChannelDiff clientDiff = channelDiff.clientChannelDiff();

        // Removed endpoints
        clientDiff.removedEndpoints().forEach(endpoint -> {
            log.info("Client endpoint removed: {}:{}", endpoint.host(), endpoint.port());
            socketManager.destroyClientSocket(newCfg, endpoint);
        });

        // Added endpoints
        clientDiff.addedEndpoints().forEach(endpoint -> {
            log.info("Client endpoint added: {}:{}", endpoint.host(), endpoint.port());
            AbstractSocket socket = socketManager.createClientSockets(newCfg, endpoint);
            socketManager.start(socket);
        });

        // Modified endpoints
        clientDiff.modifiedEndpoints().forEach(diff -> {
            SocketEndpoint newEp = diff.newSocketEndpoint();
            String clientId = CommonUtil.clientId(
                    newCfg.name(),
                    newEp.host(),
                    newEp.port()
            );

            AbstractSocket socket = socketManager.getSocket(clientId);
            if (socket == null) {
                log.warn("Client socket not found for {}", clientId);
                return;
            }

            log.info(
                    "Updating client endpoint properties [{}] host={} port={} weight={} priority={}",
                    newCfg.name(),
                    newEp.host(),
                    newEp.port(),
                    newEp.weight(),
                    newEp.priority()
            );

            socket.updateEndpointProperties(newEp);
        });
    }

    private void handleServerChannelDiff(ChannelCfgDiff channelDiff) {
        ChannelCfg newCfg = channelDiff.newCfg();
        ServerChannelDiff serverDiff = channelDiff.serverChannelDiff();

        serverDiff.removedEndpoints().forEach(endpoint -> {
            log.info("Server endpoint removed: {}:{}", endpoint.host(), endpoint.port());
            String serverId = CommonUtil.serverId(
                    newCfg.name(),
                    endpoint.port()
            );

            AbstractSocket socket = socketManager.getSocket(serverId);
            if (socket == null) {
                log.warn("Server socket not found for {}", serverId);
                return;
            }

            socket.removeEndpoint(endpoint.id());
        });

        // Added endpoints
        serverDiff.addedEndpoints().forEach(endpoint -> {
            log.info("Server endpoint added: {}:{}", endpoint.host(), endpoint.port());
            String serverId = CommonUtil.serverId(
                    newCfg.name(),
                    endpoint.port()
            );

            AbstractSocket socket = socketManager.getSocket(serverId);
            if (socket == null) {
                log.warn("Server socket not found for {}", serverId);
                return;
            }

            socket.addEndpoint(endpoint);
        });

        serverDiff.modifiedEndpoints().forEach(diff -> {
            SocketEndpoint newEp = diff.newSocketEndpoint();
            String serverId = CommonUtil.serverId(
                    newCfg.name(),
                    newEp.port()
            );

            AbstractSocket socket = socketManager.getSocket(serverId);
            if (socket == null) {
                log.warn("Server socket not found for {}", serverId);
                return;
            }

            log.info(
                    "Updating server endpoint properties [{}] port={} weight={} priority={}",
                    newCfg.name(),
                    newEp.port(),
                    newEp.weight(),
                    newEp.priority()
            );

            socket.updateEndpointProperties(newEp);
        });
    }

}
