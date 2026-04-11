package com.socket.edge.core.socket;

import com.socket.edge.core.LoadAware;
import com.socket.edge.core.SocketTelemetry;
import com.socket.edge.core.strategy.WeightedCandidate;
import com.socket.edge.model.SocketEndpoint;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Wrapper around a Netty {@link Channel} enriched with endpoint metadata,
 * load awareness, and telemetry support.
 *
 * <p>v3.0 changes:
 * <ul>
 *   <li>Fixed: {@code send()} no longer blocks event loop with {@code awaitUninterruptibly()}</li>
 *   <li>Fixed: PCI-DSS compliant logging (no raw message content)</li>
 *   <li>Improved: fail tracking with proper cooldown mechanism</li>
 * </ul>
 *
 * @author Ari Patriana
 * @since 3.0.0
 */
public class SocketChannel implements WeightedCandidate, LoadAware {

    private static final Logger log = LoggerFactory.getLogger(SocketChannel.class);

    private final ChannelId channelId;
    private final Channel channel;
    private final AtomicInteger inflight = new AtomicInteger(0);
    private final String socketId;

    private volatile SocketEndpoint socketEndpoint;
    private volatile SocketTelemetry socketTelemetry;

    private final AtomicInteger failCount = new AtomicInteger(0);
    private volatile long unhealthyUntil = 0L;

    private final int maxFails;
    private final int failTimeout;

    public SocketChannel(
            String socketId,
            Channel channel,
            SocketEndpoint socketEndpoint,
            SocketTelemetry socketTelemetry
    ) {
        this.socketId = socketId;
        this.channel = channel;
        this.channelId = channel.id();
        this.socketEndpoint = socketEndpoint;
        this.socketTelemetry = socketTelemetry;
        this.maxFails = socketEndpoint.maxfails();
        this.failTimeout = socketEndpoint.failTimeout();
    }

    public SocketEndpoint getSocketEndpoint() {
        return socketEndpoint;
    }

    public void setSocketEndpoint(SocketEndpoint socketEndpoint) {
        this.socketEndpoint = socketEndpoint;
    }

    public Channel channel() {
        return channel;
    }

    public ChannelId channelId() {
        return channelId;
    }

    /**
     * Sends raw bytes through this channel.
     *
     * <p>v3.0: Non-blocking write. Does NOT call {@code awaitUninterruptibly()}
     * which would block the Netty event loop and cause cascading timeouts.</p>
     *
     * @param bytes payload to send
     * @return {@code true} if the write was initiated successfully
     */
    public boolean send(byte[] bytes) {
        Channel ch = this.channel;

        if (ch == null || !ch.isActive()) {
            return false;
        }

        try {
            ChannelFuture future = ch.writeAndFlush(Unpooled.wrappedBuffer(bytes));

            future.addListener((ChannelFutureListener) f -> {
                if (!f.isSuccess()) {
                    log.warn("{} send failed: {}", socketId,
                            f.cause() != null ? f.cause().getMessage() : "unknown");
                }
            });

            if (log.isDebugEnabled()) {
                log.debug("{} send {} bytes", socketId, bytes.length);
            }

            return true;
        } catch (Exception e) {
            log.error("{} send exception: {}", socketId, e.getMessage());
            return false;
        }
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

    public boolean isAvailable(long now) {
        return now >= unhealthyUntil && channel.isActive();
    }

    public void markSuccess() {
        failCount.set(0);
        unhealthyUntil = 0L;
    }

    public void markFailure(long now) {
        int fails = failCount.incrementAndGet();

        if (fails >= maxFails) {
            unhealthyUntil = now + failTimeout;
            failCount.set(0);
        }
    }

    public ChannelFuture close() {
        return channel.close();
    }

    @Override
    public int getWeight() {
        return socketEndpoint.getWeight();
    }

    @Override
    public int getPriority() {
        return socketEndpoint.getPriority();
    }

    public void onMessage() {
        socketTelemetry.onMessage();
    }

    public void onComplete(long latencyNs) {
        socketTelemetry.onComplete(latencyNs);
    }

    public void onError() {
        socketTelemetry.onError();
    }

    public void onConnect() {
        socketTelemetry.onConnect();
    }

    public void onDisconnect() {
        socketTelemetry.onDisconnect();
    }
}
