package com.socket.edge.core.socket;

import com.socket.edge.core.LoadAware;
import com.socket.edge.core.strategy.WeightedCandidate;
import com.socket.edge.model.SocketEndpoint;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicInteger;

public class SocketChannel implements WeightedCandidate, LoadAware {

    private static final Logger log = LoggerFactory.getLogger(SocketChannel.class);

    private final ChannelId channelId;
    private final Channel channel;
    private final AtomicInteger inflight = new AtomicInteger(0);
    private String socketId;
    private SocketEndpoint se;

    public SocketChannel(String socketId, Channel channel, SocketEndpoint se) {
        this.socketId = socketId;
        this.channel = channel;
        this.channelId = channel.id();
        this.se = se;
    }

    public void setSocketEndpoint(SocketEndpoint se) {
        this.se = se;
    }

    public SocketEndpoint getSocketEndpoint() {
        return se;
    }

    public Channel channel() {
        return channel;
    }

    public ChannelId channelId() { return channelId; }

    public boolean send(byte[] bytes) {
        Channel ch = this.channel;

        if (ch == null || !ch.isActive()) {
            return false;
        }

        if (log.isDebugEnabled()) {
            log.debug("{} send {}", socketId, new String(bytes));
        }

        ch.writeAndFlush(Unpooled.wrappedBuffer(bytes));
        return true;
    }

    @Override
    public int inflight() {
        return inflight.get();
    }

    @Override
    public void increment() {
        inflight.incrementAndGet();
    }

    @Override
    public void decrement() {
        inflight.decrementAndGet();
    }

    public boolean isActive() {
        return channel.isActive();
    }

    public ChannelFuture close() {
        return channel.close();
    }

    @Override
    public int getWeight() {
        return se.getWeight();
    }

    @Override
    public int getPriority() {
        return se.getPriority();
    }
}
