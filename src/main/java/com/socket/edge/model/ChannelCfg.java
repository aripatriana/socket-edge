package com.socket.edge.model;

public final class ChannelCfg {

    private final String name;
    private final String type;
    private final ServerChannel serverChannel;
    private final ClientChannel clientChannel;
    private final String profile;

    public ChannelCfg(String name, String type, ServerChannel serverChannel, ClientChannel clientChannel, String profile) {
        this.name = name;
        this.type = type;
        this.serverChannel = serverChannel;
        this.clientChannel = clientChannel;
        this.profile = profile;
    }

    public String name() { return name; }
    public String type() { return type; }
    public ServerChannel server() { return serverChannel; }
    public ClientChannel client() { return clientChannel; }
    public String profile() { return profile; }
}