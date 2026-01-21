package com.socket.edge.core;

import com.socket.edge.core.socket.AbstractSocket;
import com.socket.edge.core.socket.NettyServerSocket;
import com.socket.edge.core.socket.SocketChannel;
import com.socket.edge.model.Metrics;
import com.socket.edge.model.RuntimeState;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;
import io.netty.channel.Channel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;
import java.util.stream.Collectors;

public class SocketTelemetry {

    private static final Logger log = LoggerFactory.getLogger(SocketTelemetry.class);

    private final long SLOW_THRESHOLD_NS = TimeUnit.MILLISECONDS.toNanos(50);

    private String id;
    private String name;
    private String type;
    private AbstractSocket socket;

    /* ===== MICROMETER METERS ===== */
    private final Counter msgIn;
    private final Counter msgOut;
    private final Counter errCnt;
    private final Timer latency;

    /* ===== INTERNAL STATE (HOT PATH SAFE) ===== */
    private final AtomicLong minLatency = new AtomicLong(Long.MAX_VALUE);
    private final AtomicLong maxLatency = new AtomicLong(0);
    private final AtomicLong lastErr = new AtomicLong(0);
    private final AtomicLong lastMsg = new AtomicLong(0);
    private final AtomicLong lastConnect = new AtomicLong(0);
    private final AtomicLong lastDisconnect = new AtomicLong(0);
    private final AtomicLong queue = new AtomicLong(0);
    private final AtomicLong channelCounter = new AtomicLong(0);

    /* TPS calculation */
    private final LongAdder tpsCounter = new LongAdder();
    private final AtomicLong lastTpsTime = new AtomicLong(System.currentTimeMillis());
    private final AtomicLong tps = new AtomicLong(0);
    private final AtomicLong minTps = new AtomicLong(Long.MAX_VALUE);
    private final AtomicLong maxTps = new AtomicLong(0);

    public SocketTelemetry(MeterRegistry registry,
                           AbstractSocket socket) {

        this.id = socket.getId();
        this.name = socket.getName();
        this.type = socket.getType().name();
        this.socket = socket;

        Tags tags = Tags.of(
                "name", name,
                "id", id,
                "type", type
        );

        this.msgIn = Counter.builder("socket.msg.in")
                .tags(tags)
                .register(registry);

        this.msgOut = Counter.builder("socket.msg.out")
                .tags(tags)
                .register(registry);

        this.errCnt = Counter.builder("socket.error.count")
                .tags(tags)
                .register(registry);

        this.latency = Timer.builder("socket.latency")
                .tags(tags)
                .publishPercentileHistogram()
                .publishPercentiles(0.95, 0.99)
                .register(registry);

        /* ===== GAUGES ===== */
        registry.gauge("socket.queue.depth", tags, queue);
        registry.gauge("socket.tps", tags, tps);
        registry.gauge("socket.tps.min", tags, minTps);
        registry.gauge("socket.tps.max", tags, maxTps);
        registry.gauge("socket.latency.min", tags, minLatency);
        registry.gauge("socket.latency.max", tags, maxLatency);
        registry.gauge("socket.last.msg", tags, lastMsg);
        registry.gauge("socket.last.connect", tags, lastConnect);
        registry.gauge("socket.last.disconnect", tags, lastDisconnect);
        registry.gauge("socket.last.error", tags, lastErr);
    }

    public void onMessage() {
        msgIn.increment();
        queue.incrementAndGet();
        tpsCounter.increment();
        lastMsg.set(System.currentTimeMillis());
        calculateTps();
    }

    public void onComplete(long latencyNs) {
        msgOut.increment();
        queue.updateAndGet(v -> Math.max(0, v - 1));
        lastMsg.set(System.currentTimeMillis());

        latency.record(latencyNs, TimeUnit.NANOSECONDS);
        minLatency.accumulateAndGet(latencyNs, Math::min);
        maxLatency.accumulateAndGet(latencyNs, Math::max);

        log.info("Complete took time {}ns", latencyNs);
        if (latencyNs > SLOW_THRESHOLD_NS) {
            log.warn("Slow socket {} latency {} ms",
                    id, latencyNs / 1_000_000d);
        }
    }

    public void onError() {
        errCnt.increment();
        lastErr.set(System.currentTimeMillis());
    }

    public void onConnect() {
        lastConnect.set(System.currentTimeMillis());
        lastDisconnect.set(0);
    }

    public void onDisconnect() {
        lastDisconnect.set(System.currentTimeMillis());
        lastConnect.set(0);
    }

    /* ========================================================= */
    /* ====================== TPS LOGIC ======================== */
    /* ========================================================= */

    private void calculateTps() {
        long now = System.currentTimeMillis();
        long last = lastTpsTime.get();

        if (now - last >= 1000) {
            if (lastTpsTime.compareAndSet(last, now)) {
                long currentTps = tpsCounter.sumThenReset();
                tps.set(currentTps);

                // MIN TPS
                minTps.accumulateAndGet(currentTps, Math::min);

                // MAX TPS
                maxTps.accumulateAndGet(currentTps, Math::max);
            }
        }
    }

    /* ========================================================= */
    /* ======================= SNAPSHOT ======================== */
    /* ========================================================= */

    public RuntimeState getRuntimeState() {
        List<SocketChannel> channels =
                socket.channelPool().activeChannels();
        int active = channels.size();
        String localHost;
        if (socket instanceof NettyServerSocket server) {
            localHost = extractServerLocalHost(server);
        } else {
            localHost = extractLocalHost(channels);
        }
        String remoteHost = extractRemoteHosts(channels);
        String status = socket.getState().name();

        return new RuntimeState(
                socket.getId(),
                socket.getName(),
                socket.getType().name(),
                localHost,
                remoteHost,
                active,
                socket.getStartTime(),
                lastConnect.get(),
                lastDisconnect.get(),
                status);
    }

    public Metrics getMetrics() {
        long count = latency.count();
        long totalLatencyNano = (long) latency.totalTime(TimeUnit.NANOSECONDS);
        long avgLatencyNano = count == 0 ? 0 : totalLatencyNano / count;
        long minLatencyNano = minLatency.get() == Long.MAX_VALUE ? 0 : minLatency.get();
        long maxLatencyNano = maxLatency.get() == 0 ? 0 : maxLatency.get();
        long minTpsVal = minTps.get() == Long.MAX_VALUE ? 0 : minTps.get();
        long maxTpsVal = maxTps.get();
        return new Metrics(
                id,
                name,
                type,
                avgLatencyNano,
                minLatencyNano,
                maxLatencyNano,
                (long) msgIn.count(),
                (long) msgOut.count(),
                queue.get(),
                tps.get(),
                minTpsVal,
                maxTpsVal,
                (long) errCnt.count(),
                lastErr.get(),
                lastMsg.get()
        );
    }

    private String extractLocalHost(List<SocketChannel> channels) {

        if (channels == null || channels.isEmpty()) {
            return "-";
        }

        return channels.stream()
                .map(SocketChannel::channel)
                .filter(Objects::nonNull)
                .filter(Channel::isActive)
                .map(Channel::localAddress)
                .filter(InetSocketAddress.class::isInstance)
                .map(InetSocketAddress.class::cast)
                .map(addr -> addr.getHostString() + ":" + addr.getPort())
                .distinct()
                .findFirst()
                .orElse("-");
    }

    private String extractRemoteHosts(List<SocketChannel> channels) {
        if (channels == null || channels.isEmpty()) {
            return "-";
        }

        return channels.stream()
                .map(SocketChannel::channel)
                .filter(Objects::nonNull)
                .filter(Channel::isActive)
                .map(Channel::remoteAddress)
                .filter(InetSocketAddress.class::isInstance)
                .map(InetSocketAddress.class::cast)
                .map(addr -> addr.getHostString() + ":" + addr.getPort())
                .distinct()
                .collect(Collectors.joining(","));
    }

    private String extractServerLocalHost(NettyServerSocket socket) {
        Channel ch = socket.getServerChannel();
        if (ch == null) return "-";

        InetSocketAddress addr =
                (InetSocketAddress) ch.localAddress();

        return ":" + addr.getPort();
    }
}
