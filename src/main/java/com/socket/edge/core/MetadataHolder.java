package com.socket.edge.core;

import com.socket.edge.model.Metadata;

public class MetadataHolder {

    private volatile Metadata metadata;

    public MetadataHolder(Metadata metadata) {
        this.metadata = metadata;
    }

    public void replaceWith(Metadata newMetadata) {
        this.metadata = newMetadata;
    }

    public Metadata get() {
        return metadata;
    }
}
