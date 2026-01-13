package com.socket.edge.core;

import com.socket.edge.model.SocketType;
import com.socket.edge.model.ChannelCfg;
import com.socket.edge.model.Direction;
import com.socket.edge.model.Iso8583Profile;
import io.netty.channel.Channel;

import java.net.InetSocketAddress;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class MessageContext {

    private final Map<String, String> isoFields;
    private Direction direction;
    private ChannelCfg channelCfg;
    private io.netty.channel.Channel channel;
    private String correlationKey;
    private byte[] rawBytes;
    private InetSocketAddress localAddress;
    private InetSocketAddress remoteAddress;
    private SocketType socketType;
    private Iso8583Profile profile;
    private Map<String, Object> properties = new ConcurrentHashMap<>();

    public MessageContext(Map<String, String> isoFields, byte[] rawBytes) {
        this.isoFields = isoFields;
        this.rawBytes = rawBytes;
    }

    public byte[] getRawBytes() {
        return rawBytes;
    }

    public void setRawBytes(byte[] rawBytes) {
        this.rawBytes = rawBytes;
    }

    public String field(String de) {
        return isoFields.get(de);
    }

    public void setDirection(Direction direction) {
        this.direction = direction;
    }

    public Direction getDirection() {
        return direction;
    }

    public Channel getChannel() {
        return channel;
    }

    public void setChannel(Channel channel) {
        this.channel = channel;
    }

    public ChannelCfg getChannelCfg() {
        return channelCfg;
    }

    public void setChannelCfg(ChannelCfg channelCfg) {
        this.channelCfg = channelCfg;
    }

    public void setCorrelationKey(String key) {
        this.correlationKey = key;
    }

    public String getCorrelationKey() {
        return correlationKey;
    }

    public InetSocketAddress getLocalAddress() {
        return localAddress;
    }

    public void setLocalAddress(InetSocketAddress localAddress) {
        this.localAddress = localAddress;
    }

    public InetSocketAddress getRemoteAddress() {
        return remoteAddress;
    }

    public void setRemoteAddress(InetSocketAddress remoteAddress) {
        this.remoteAddress = remoteAddress;
    }

    public SocketType getSocketType() {
        return socketType;
    }

    public void setSocketType(SocketType socketType) {
        this.socketType = socketType;
    }

    public void setProfile(Iso8583Profile profile) {
        this.profile = profile;
    }

    public Iso8583Profile getProfile() {
        return profile;
    }

    public void addProperty(String key, Object obj) {
        properties.put(key, obj);
    }

    public Object getProperty(String key) {
        return properties.get(key);
    }

    public Object removeProperty(String key) {
        return properties.remove(key);
    }
}
