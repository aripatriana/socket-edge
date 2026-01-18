package com.socket.edge.http.service;

import com.socket.edge.core.socket.SocketManager;
import com.socket.edge.model.Metadata;
import com.socket.edge.model.helper.MetadataDiff;

public class ReloadCfgService {

    SocketManager socketManager;
    Metadata metadata;

    public ReloadCfgService(SocketManager socketManager, Metadata metadata) {
        this.socketManager = socketManager;
        this.metadata = metadata;
    }

    public void reloadConfig(Metadata newMetadata) {
        MetadataDiff metadataDiff = metadata.diffWith(newMetadata);
        metadataDiff.deletedChannelCfgs().forEach(cfg -> {
            try {
                socketManager.destroySocket(cfg);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        });

        metadataDiff.addedChannelCfgs().forEach(cfg -> {
            try {
                socketManager.createSocket(cfg);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        });

        metadataDiff.modifiedChannelCfg().forEach(k -> {
            // TODO
        });

        metadata.replaceWith(newMetadata);
    }

}
