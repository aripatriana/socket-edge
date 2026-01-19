package com.socket.edge.http.service;

import com.socket.edge.core.ChannelCfgProcessor;
import com.socket.edge.core.socket.SocketManager;
import com.socket.edge.model.Metadata;
import com.socket.edge.model.helper.MetadataDiff;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Objects;

public class ReloadCfgService {

    private static final Logger log = LoggerFactory.getLogger(ReloadCfgService.class);

    private final SocketManager socketManager;
    private final Metadata metadata;
    private final ChannelCfgProcessor channelCfgProcessor;
    private static final String CHANNEL_CONF = "channel.conf";

    public ReloadCfgService(SocketManager socketManager, Metadata metadata, ChannelCfgProcessor channelCfgProcessor) {
        this.socketManager = socketManager;
        this.metadata = metadata;
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
            return metadata.diffWith(newMetadata);
        } catch (Exception e) {
            log.error("Failed to validate channel config", e);
            throw new RuntimeException("Failed to validate channel configuration", e);
        }
    }

    public void reload() throws IOException {
        Metadata newMetadata = channelCfgProcessor.process(channelConfigPath());
        Objects.requireNonNull(newMetadata, "Invalid channel.conf");
        reloadConfig(newMetadata);
    }

    public void reloadConfig(Metadata newMetadata) {
        try {
            MetadataDiff metadataDiff = metadata.diffWith(newMetadata);
            metadataDiff.deletedChannelCfgs().forEach(cfg -> {
                log.info("Deleted channel config detected for channel: {}", cfg.name());
                socketManager.destroySocket(cfg);
            });

            metadataDiff.addedChannelCfgs().forEach(cfg -> {
                log.info("Added channel config detected for channel: {}", cfg.name());
                socketManager.createSocket(cfg);
            });

            metadataDiff.modifiedChannelCfg().forEach(k -> {
                k.clientChannelDiff().removedEndpoints().forEach(endpoint -> {
                    socketManager.destroyClientSocket(k.newCfg(), endpoint);
                });

                k.clientChannelDiff().addedEndpoints().forEach(endpoint -> {
                    socketManager.createClientSockets(k.newCfg(), endpoint);
                });
            });

            metadata.replaceWith(newMetadata);
        } catch (Exception e) {
            log.error("Failed to reload channel config, metadata not replaced", e);
            throw new RuntimeException("Failed to reload channel configuration", e);
        }
    }

}
