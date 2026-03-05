package com.socket.edge.core.socket;

import com.socket.edge.core.LoadAware;
import com.socket.edge.core.SocketTelemetry;
import com.socket.edge.core.strategy.WeightedCandidate;
import com.socket.edge.model.SocketEndpoint;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Wrapper around a Netty {@link Channel} enriched with endpoint metadata,
 * load awareness, and telemetry support.
 *
 * <p>{@code SocketChannel} represents a single logical communication channel
 * belonging to an {@link AbstractSocket}. It provides:
 * <ul>
 *   <li>Inflight request tracking ({@link LoadAware})</li>
 *   <li>Weighted and priority-based selection ({@link WeightedCandidate})</li>
 *   <li>Association with {@link SocketEndpoint}</li>
 *   <li>Integration with {@link SocketTelemetry}</li>
 * </ul>
 *
 * <p>This abstraction allows routing and load-balancing decisions
 * to be made independently of the underlying Netty channel.</p>
 *
 * <p>Thread safety:
 * <ul>
 *   <li>Inflight counter uses {@link AtomicInteger}</li>
 *   <li>Channel lifecycle is managed by Netty</li>
 *   <li>Endpoint and telemetry references may be updated at runtime</li>
 * </ul>
 *
 * @author Ari Patriana
 * @since 1.0.0
 */
public class SocketChannel implements WeightedCandidate, LoadAware {

    private static final Logger log = LoggerFactory.getLogger(SocketChannel.class);

    /**
     * Netty channel identifier.
     */
    private final ChannelId channelId;

    /**
     * Underlying Netty channel.
     */
    private final Channel channel;

    /**
     * Number of in-flight operations on this channel.
     */
    private final AtomicInteger inflight = new AtomicInteger(0);

    /**
     * Owning socket identifier.
     */
    private final String socketId;

    /**
     * Associated socket endpoint.
     */
    private volatile SocketEndpoint socketEndpoint;

    /**
     * Telemetry associated with this channel.
     */
    private volatile SocketTelemetry socketTelemetry;

    private final AtomicInteger failCount = new AtomicInteger(0);
    private volatile long unhealthyUntil = 0L;

    private final int maxFails;
    private final int failTimeout;

    /**
     * Creates a new socket channel wrapper.
     *
     * @param socketId        owning socket identifier
     * @param channel         Netty channel
     * @param socketEndpoint  endpoint associated with this channel
     * @param socketTelemetry telemetry instance
     */
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
        this.failTimeout =socketEndpoint.failTimeout();
    }

    /**
     * Returns the associated socket endpoint.
     *
     * @return socket endpoint
     */
    public SocketEndpoint getSocketEndpoint() {
        return socketEndpoint;
    }

    /**
     * Updates the socket endpoint associated with this channel.
     *
     * <p>Typically invoked when endpoint configuration is reloaded.</p>
     *
     * @param socketEndpoint new socket endpoint
     */
    public void setSocketEndpoint(SocketEndpoint socketEndpoint) {
        this.socketEndpoint = socketEndpoint;
    }

    /**
     * Returns the underlying Netty channel.
     *
     * @return Netty channel
     */
    public Channel channel() {
        return channel;
    }

    /**
     * Returns the Netty channel identifier.
     *
     * @return channel id
     */
    public ChannelId channelId() {
        return channelId;
    }

    /**
     * Sends raw bytes through this channel.
     *
     * @param bytes payload to send
     * @return {@code true} if the send was initiated, {@code false} otherwise
     */
    public boolean send(byte[] bytes) {
        Channel ch = this.channel;

        if (ch == null || !ch.isActive()) {
            return false;
        }

        try {
            ChannelFuture future = ch.writeAndFlush(Unpooled.wrappedBuffer(bytes));

            future.awaitUninterruptibly();

            if (!future.isSuccess()) {
                log.warn("{} send failed", socketId, future.cause());
                return false;
            }

            if (log.isInfoEnabled()) {
                log.info("{} send {}", socketId, new String(bytes));
            }

            return true;
        } catch (Exception e) {
            log.error("{} send exception", socketId, e);
            return false;
        }
    }

    /**
     * Returns the current number of in-flight operations.
     *
     * @return inflight count
     */
    @Override
    public int inflight() {
        return inflight.get();
    }

    /**
     * Increments the in-flight operation counter.
     */
    @Override
    public void increment() {
        inflight.incrementAndGet();
    }

    /**
     * Decrements the in-flight operation counter.
     */
    @Override
    public void decrement() {
        inflight.decrementAndGet();
    }

    /**
     * Indicates whether the channel is active.
     *
     * @return {@code true} if active
     */
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
            failCount.set(0); // reset counter after mark unhealthy
        }
    }

    /**
     * Closes the underlying channel.
     *
     * @return future representing the close operation
     */
    public ChannelFuture close() {
        return channel.close();
    }

    /**
     * Returns the weight of this channel for load-balancing decisions.
     *
     * @return channel weight
     */
    @Override
    public int getWeight() {
        return socketEndpoint.getWeight();
    }

    /**
     * Returns the priority of this channel for routing decisions.
     *
     * @return channel priority
     */
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
