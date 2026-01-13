package com.socket.edge.core;

import com.socket.edge.model.Metadata;
import com.socket.edge.utils.DslParser;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class ChannelCfgProcessor {

    public Metadata process(Path path) throws IOException {
        String text = Files.readString(path);
        Metadata metadata = new DslParser().parse(text);
        validateMetadata(metadata);
        return metadata;
    }

    public void validateMetadata(Metadata metadata) {

        if (metadata.channelCfgs().isEmpty()) {
            throw new IllegalStateException("No channel defined");
        }

        metadata.channelCfgs().forEach(channel -> {
            if (channel.profile() == null) {
                throw new IllegalStateException(
                        "ChannelCfg " + channel.name() + " has no profile");
            }
        });

        metadata.profiles().forEach((name, profile) -> {
            if (profile.correlationFields().isEmpty()) {
                throw new IllegalStateException(
                        "Profile " + name + " has no correlation fields");
            }
        });
    }
}
