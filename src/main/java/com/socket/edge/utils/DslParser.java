package com.socket.edge.utils;

import com.socket.edge.constant.Direction;
import com.socket.edge.model.*;

import java.util.*;

public final class DslParser {

    public Metadata parse(String content) {
        Metadata md = new Metadata(
                parseChannels(content),
                parseProfiles(content)
        );

        validateProfiles(md.channelCfgs(), md.profiles());
        return md;
    }

    record Block(String name, String body) {}

    private List<Block> extractBlocks(String content, String keyword) {

        List<Block> blocks = new ArrayList<>();
        int idx = 0;

        while ((idx = content.indexOf(keyword, idx)) != -1) {

            // boundary check (hindari myprofile, someprofileX)
            if (idx > 0 && Character.isLetterOrDigit(content.charAt(idx - 1))) {
                idx += keyword.length();
                continue;
            }

            int nameEnd = idx + keyword.length();

            // ⬇️ CARI '{' SETELAH keyword
            int braceIdx = content.indexOf("{", nameEnd);

            // ❗ FIX UTAMA: kalau tidak ada '{', ini BUKAN block
            if (braceIdx == -1) {
                idx += keyword.length();
                continue;
            }

            String name = null;
            String between = content.substring(nameEnd, braceIdx).trim();
            if (!between.isEmpty()) {
                name = between.split("\\s+")[0];
            }

            int depth = 1;
            int i = braceIdx + 1;

            while (i < content.length() && depth > 0) {
                char c = content.charAt(i++);
                if (c == '{') depth++;
                else if (c == '}') depth--;
            }

            if (depth != 0) {
                throw new IllegalStateException("Unclosed block: " + keyword);
            }

            String body = content.substring(braceIdx + 1, i - 1);
            blocks.add(new Block(name, body));
            idx = i;
        }

        return blocks;
    }

    private List<ChannelCfg> parseChannels(String content) {
        return extractBlocks(content, "channel")
            .stream()
                .map(b -> parseSingleChannel(b.body()))
                .toList();
    }

    private ChannelCfg parseSingleChannel(String block) {

        String name = null;
        String type = null;
        ServerChannel serverChannel = null;
        ClientChannel clientChannel = null;
        String profile = null;

        List<String> lines = Arrays.stream(block.split("\n"))
                .map(String::trim)
                .filter(l -> !l.isEmpty() && !l.startsWith("#"))
                .toList();

        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);

            if (line.startsWith("name")) {
                name = line.split("\\s+")[1];
            }

            else if (line.startsWith("type")) {
                type = line.split("\\s+")[1];
            }

            else if (line.startsWith("profile")) {
                profile = line.split("\\s+")[1];
            }

            else if (line.startsWith("server")) {
                StringBuilder sb = new StringBuilder();
                i = collectBlock(lines, i, sb);
                serverChannel = parseServer(sb.toString());
            }

            else if (line.startsWith("client")) {
                StringBuilder sb = new StringBuilder();
                i = collectBlock(lines, i, sb);
                clientChannel = parseClient(sb.toString());
            }
        }

        if (name == null) {
            throw new IllegalStateException("channel.name is required");
        }
        if (type == null) {
            throw new IllegalStateException("channel.type is required");
        }
        if (serverChannel == null) {
            throw new IllegalStateException("channel.server block is required");
        }
        if (clientChannel == null) {
            throw new IllegalStateException("channel.client block is required");
        }

        return new ChannelCfg(name, type, serverChannel, clientChannel, profile);
    }

    private int collectBlock(
            List<String> lines,
            int startIndex,
            StringBuilder out
    ) {
        int depth = 0;

        for (int i = startIndex; i < lines.size(); i++) {
            String l = lines.get(i);

            if (l.contains("{")) depth++;
            if (l.contains("}")) depth--;

            // ⬇️ SKIP header line (direction {...}, correlation {...})
            if (i != startIndex && depth > 0) {
                if (!l.contains("{") && !l.contains("}")) {
                    out.append(l).append("\n");
                }
            }

            if (depth == 0 && i != startIndex) {
                return i;
            }
        }

        throw new IllegalStateException("Unclosed block");
    }

    private ServerChannel parseServer(String block) {

        String listenHost = null;
        int listenPort = -1;
        List<SocketEndpoint> pool = new ArrayList<>();
        String strategy = null;

        for (String line : block.split("\n")) {
            line = line.trim();

            if (line.startsWith("listen")) {
                String[] p = line.split("\\s+");
                listenHost = p[1];
                listenPort = Integer.parseInt(p[2]);
            }

            else if (line.startsWith("pool")) {

                String[] p = line.split("\\s+");

                String host = p[1];
                int port = listenPort; // inherit
                int weight = 1;
                int priority = 0;

                for (int i = 2; i < p.length - 1; i++) {
                    if ("weight".equals(p[i])) {
                        weight = Integer.parseInt(p[i + 1]);
                    }
                    else if ("priority".equals(p[i])) {
                        priority = Integer.parseInt(p[i + 1]);
                    }
                    else if ("port".equals(p[i])) {
                        port = Integer.parseInt(p[i + 1]);
                    }
                }

                if (weight <= 0) {
                    throw new IllegalStateException("weight must be > 0");
                }
                if (priority < 0) {
                    throw new IllegalStateException("priority must be >= 0");
                }

                pool.add(
                        new SocketEndpoint(host, port, weight, priority)
                );
            }

            else if (line.startsWith("strategy")) {
                strategy = Strategy.valueOf(
                        line.split("\\s+")[1].toUpperCase()
                ).name().toLowerCase();
            }
        }

        if (pool.isEmpty()) {
            throw new IllegalStateException("server.pool must have at least one endpoint");
        }

        Set<String> poolSeen = new HashSet<>();
        for (SocketEndpoint e : pool) {
            String key = e.host() + ":" + e.port();
            if (!poolSeen.add(key)) {
                throw new IllegalStateException("duplicate pool endpoint: " + key);
            }
        }

        if (listenPort <= 0 || listenPort > 65535) {
            throw new IllegalStateException("invalid server.listen port: " + listenPort);
        }

        return new ServerChannel(
                listenHost,
                listenPort,
                pool,
                strategy
        );
    }

    private ClientChannel parseClient(String block) {

        List<SocketEndpoint> endpoints = new ArrayList<>();
        String strategy = Strategy.ROUNDROBIN.name();

        for (String line : block.split("\n")) {
            line = line.trim();

            if (line.startsWith("connect")) {

                String[] p = line.split("\\s+");

                String host = p[1];
                int port = Integer.parseInt(p[2]);

                int weight = 1;     // default
                int priority = 0;   // default

                for (int i = 3; i < p.length - 1; i++) {
                    if ("weight".equals(p[i])) {
                        weight = Integer.parseInt(p[i + 1]);
                    }
                    else if ("priority".equals(p[i])) {
                        priority = Integer.parseInt(p[i + 1]);
                    }
                }

                if (port <= 0 || port > 65535) {
                    throw new IllegalStateException("invalid client connect port: " + port);
                }

                if (weight <= 0) {
                    throw new IllegalStateException("weight must be > 0");
                }
                if (priority < 0) {
                    throw new IllegalStateException("priority must be >= 0");
                }

                endpoints.add(
                        new SocketEndpoint(host, port, weight, priority)
                );
            }

            else if (line.startsWith("strategy")) {
                strategy = Strategy.valueOf(
                        line.split("\\s+")[1].toUpperCase()
                ).name().toLowerCase();
            }
        }

        if (endpoints.size() > 1 && strategy == null) {
            throw new IllegalStateException(
                    "client.strategy is required when multiple connect endpoints are defined"
            );
        }

        Set<String> seen = new HashSet<>();
        for (SocketEndpoint e : endpoints) {
            String key = e.host() + ":" + e.port();
            if (!seen.add(key)) {
                throw new IllegalStateException("duplicate client endpoint: " + key);
            }
        }


        if (endpoints.isEmpty()) {
            throw new IllegalStateException("client must have at least one connect");
        }

        return new ClientChannel(endpoints, strategy);
    }

    private Map<String, Iso8583Profile> parseProfiles(String content) {

        Map<String, Iso8583Profile> profiles = new HashMap<>();

        for (Block b : extractBlocks(content, "profile")) {
            profiles.put(
                    b.name(),
                    parseSingleProfile(b.body())
            );
        }
        return profiles;
    }

    private Iso8583Profile parseSingleProfile(String block) {

        Map<Direction, Set<String>> directions = new EnumMap<>(Direction.class);
        List<String> correlation = new ArrayList<>();

        List<String> lines = Arrays.stream(block.split("\n"))
                .map(String::trim)
                .filter(l -> !l.isEmpty())
                .toList();

        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);

            if (line.startsWith("direction")) {
                Direction dir = Direction.valueOf(
                        line.split("\\s+")[1].toUpperCase()
                );

                StringBuilder sb = new StringBuilder();
                i = collectBlock(lines, i, sb);

                directions.put(dir, parseDirection(sb.toString()));
            }

            else if (line.startsWith("correlation")) {
                StringBuilder sb = new StringBuilder();
                i = collectBlock(lines, i, sb);
                correlation = parseCorrelation(sb.toString());

                if (correlation.isEmpty()) {
                    throw new IllegalStateException("profile.correlation must not be empty");
                }
            }
        }

        return new Iso8583Profile(directions, correlation);
    }

    private Set<String> parseDirection(String block) {

        Set<String> values = new HashSet<>();

        for (String line : block.split("\n")) {
            line = line.trim();

            if (line.contains(" in ")) {
                String list = line.substring(line.indexOf("["));
                values.addAll(DslUtil.extractList(list));
            }
            else if (line.contains("=")) {
                values.add(
                        DslUtil.trimQuotes(
                                line.split("=")[1].trim()
                        )
                );
            }
        }

        if (values.isEmpty()) {
            throw new IllegalStateException("direction block must not be empty");
        }

        return values;
    }

    private List<String> parseCorrelation(String block) {

        return Arrays.stream(block.split("\n"))
                .map(String::trim)
                .filter(l -> !l.isEmpty())
                .toList();
    }

    private void validateProfiles(
            List<ChannelCfg> channels,
            Map<String, Iso8583Profile> profiles
    ) {
        for (ChannelCfg c : channels) {
            if (c.profile() != null && !profiles.containsKey(c.profile())) {
                throw new IllegalStateException(
                        "channel '" + c.name() + "' references unknown profile '" + c.profile() + "'"
                );
            }
        }
    }

}
