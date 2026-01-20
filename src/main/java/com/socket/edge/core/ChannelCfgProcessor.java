package com.socket.edge.core;

import com.socket.edge.constant.Direction;
import com.socket.edge.model.Metadata;
import com.socket.edge.model.SocketEndpoint;
import com.socket.edge.utils.DslParser;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

public class ChannelCfgProcessor {

    public Metadata process(Path path) throws IOException {
        String text = Files.readString(path);
        Metadata metadata = new DslParser().parse(text);
        validateMetadata(metadata);
        return metadata;
    }

    public void validateMetadata(Metadata metadata) {

        /* =========================
         * BASIC VALIDATION
         * ========================= */
        if (metadata.channelCfgs().isEmpty()) {
            throw new IllegalStateException("No channel defined");
        }

        /* =========================
         * 1. CHANNEL NAME MUST BE UNIQUE
         * ========================= */
        Set<String> channelNames = new HashSet<>();
        metadata.channelCfgs().forEach(channel -> {
            if (!channelNames.add(channel.name())) {
                throw new IllegalStateException(
                        "Duplicate channel name detected: " + channel.name());
            }
        });

        /* =========================
         * GLOBAL SERVER LISTEN CHECK
         * ========================= */
        Set<String> globalServerListen = new HashSet<>();

        /* =========================
         * CHANNEL LEVEL VALIDATION
         * ========================= */
        metadata.channelCfgs().forEach(channel -> {

            /* 2. TYPE MUST BE TCP */
            if (!"tcp".equalsIgnoreCase(channel.type())) {
                throw new IllegalStateException(
                        "Channel " + channel.name() + " has invalid type: " + channel.type());
            }

            /* 3. PROFILE MUST EXIST */
            if (channel.profile() == null) {
                throw new IllegalStateException(
                        "Channel " + channel.name() + " has no profile");
            }

            if (!metadata.profiles().containsKey(channel.profile())) {
                throw new IllegalStateException(
                        "Channel " + channel.name()
                                + " references unknown profile: " + channel.profile());
            }

            /* 4. MUST HAVE SERVER OR CLIENT */
            if (channel.server() == null && channel.client() == null) {
                throw new IllegalStateException(
                        "Channel " + channel.name() + " has neither server nor client defined");
            }

            /* =========================
             * SERVER VALIDATION
             * ========================= */
            if (channel.server() != null) {

                /* server.listen must be globally unique */
                String listenKey = channel.server().listenHost()
                        + ":" + channel.server().listenPort();

                if (!globalServerListen.add(listenKey)) {
                    throw new IllegalStateException(
                            "Duplicate server listen endpoint detected: "
                                    + listenKey + " (channel " + channel.name() + ")");
                }

                /* server.pool IP must be unique */
                Set<String> poolIps = new HashSet<>();
                channel.server().pool().forEach(endpoint -> {
                    if (!poolIps.add(endpoint.host())) {
                        throw new IllegalStateException(
                                "Duplicate server pool IP in channel "
                                        + channel.name() + ": " + endpoint.host());
                    }
                });
            }

            /* =========================
             * CLIENT VALIDATION
             * ========================= */
            if (channel.client() != null) {

                Set<String> clientEndpoints = new HashSet<>();

                for (SocketEndpoint endpoint : channel.client().endpoints()) {
                    String key = endpoint.host() + ":" + endpoint.port();

                    /* client ip + port must be unique */
                    if (!clientEndpoints.add(key)) {
                        throw new IllegalStateException(
                                "Duplicate client endpoint in channel "
                                        + channel.name() + ": " + key);
                    }

                    /* weight must be positive */
                    if (endpoint.weight() <= 0) {
                        throw new IllegalStateException(
                                "Invalid client weight in channel "
                                        + channel.name() + " for endpoint " + key);
                    }

                    /* priority must be >= 0 */
                    if (endpoint.priority() < 0) {
                        throw new IllegalStateException(
                                "Invalid client priority in channel "
                                        + channel.name() + " for endpoint " + key);
                    }
                }

                /* strategy validation */
                String strategy = channel.client().strategy();
                if (strategy != null &&
                        !Set.of("roundrobin", "least", "hash")
                                .contains(strategy.toLowerCase())) {
                    throw new IllegalStateException(
                            "Unknown client strategy in channel "
                                    + channel.name() + ": " + strategy);
                }
            }
        });

        /* =========================
         * PROFILE VALIDATION
         * ========================= */
        metadata.profiles().forEach((name, profile) -> {

            /* correlation must not be empty */
            if (profile.correlationFields().isEmpty()) {
                throw new IllegalStateException(
                        "Profile " + name + " has no correlation fields");
            }

            /* correlation fields must be unique */
            Set<String> corr = new HashSet<>();
            profile.correlationFields().forEach(field -> {
                if (!corr.add(field)) {
                    throw new IllegalStateException(
                            "Duplicate correlation field in profile "
                                    + name + ": " + field);
                }
            });

            /* inbound / outbound must exist */
            if (profile.valuesFor(Direction.INBOUND).isEmpty()) {
                throw new IllegalStateException(
                        "Profile " + name + " has no inbound rules");
            }

            if (profile.valuesFor(Direction.OUTBOUND).isEmpty()) {
                throw new IllegalStateException(
                        "Profile " + name + " has no outbound rules");
            }
        });
    }

}
