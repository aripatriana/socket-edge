package com.socket.edge.core;

import com.socket.edge.constant.SocketState;
import com.socket.edge.core.socket.AbstractSocket;
import com.socket.edge.core.socket.NettyServerSocket;
import com.socket.edge.core.socket.SocketChannel;
import com.socket.edge.model.Metrics;
import com.socket.edge.model.Queue;
import com.socket.edge.model.RuntimeState;
import io.micrometer.core.instrument.*;
import io.micrometer.core.instrument.distribution.HistogramSnapshot;
import io.micrometer.core.instrument.distribution.ValueAtPercentile;
import io.netty.channel.Channel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.*;
import java.util.stream.Collectors;

public class SocketTelemetry {

    private static final Logger log = LoggerFactory.getLogger(SocketTelemetry.class);

    private static final long SLOW_THRESHOLD_NS =
            TimeUnit.MILLISECONDS.toNanos(50);

    private static final long TPS_WINDOW_MS = 1000;

    private final String hashId;
    private final String id;
    private final String name;
    private final String type;
    private AbstractSocket socket;

    /* ================= MICROMETER ================= */

    private Counter msgIn;
    private Counter msgOut;
    private Counter errCnt;
    private Timer latency;

    /* ================= HOT STATE ================= */

    private final AtomicLong queue = new AtomicLong(0);
    private final AtomicLong lastMsg = new AtomicLong(0);
    private final AtomicLong lastErr = new AtomicLong(0);
    private final AtomicLong lastConnect = new AtomicLong(0);
    private final AtomicLong lastDisconnect = new AtomicLong(0);

    private final AtomicLong minLatency = new AtomicLong(Long.MAX_VALUE);
    private final AtomicLong maxLatency = new AtomicLong(0);

    // pressure TPS window
    private final AtomicLong pressureWindow = new AtomicLong(-1);
    private final LongAdder pressureCounter = new LongAdder();

    private final AtomicLong pressureTps = new AtomicLong(0);
    private final AtomicLong minPressureTps = new AtomicLong(Long.MAX_VALUE);
    private final AtomicLong maxPressureTps = new AtomicLong(0);

    private DistributionSummary pressureSummary;

    // throughput TPS window
    private final AtomicLong throughputWindow = new AtomicLong(-1);
    private final LongAdder throughputCounter = new LongAdder();


    private final AtomicLong throughputTps = new AtomicLong(0);
    private final AtomicLong minThroughputTps = new AtomicLong(Long.MAX_VALUE);
    private final AtomicLong maxThroughputTps = new AtomicLong(0);

    private DistributionSummary throughputSummary;

    private final MeterRegistry registry;
    private final List<Meter> meters = new CopyOnWriteArrayList<>();
    private final AtomicBoolean disposed = new AtomicBoolean(false);

    public SocketTelemetry(MeterRegistry registry, AbstractSocket socket, String hashId) {

        this.registry = registry;
        this.socket = socket;

        this.hashId = hashId;
        this.id = socket.getId();
        this.name = socket.getName();
        this.type = socket.getType().name();

        Tags tags = Tags.of(
                "name", name,
                "id", id,
                "type", type
        );

        initMeters(tags);
    }

    private void initMeters(Tags tags) {

        /* ===== Counters ===== */

        this.msgIn = Counter.builder("socket.msg.in")
                .tags(tags)
                .register(registry);

        this.msgOut = Counter.builder("socket.msg.out")
                .tags(tags)
                .register(registry);

        this.errCnt = Counter.builder("socket.error.count")
                .tags(tags)
                .register(registry);

        meters.add(msgIn);
        meters.add(msgOut);
        meters.add(errCnt);

        /* ===== Latency ===== */

        this.latency = Timer.builder("socket.latency")
                .tags(tags)
                .publishPercentileHistogram()
                .publishPercentiles(0.90, 0.95)
                .register(registry);

        meters.add(latency);

        /* ===== TPS Distribution ===== */

        this.pressureSummary = DistributionSummary.builder("socket.pressure.tps")
                .tags(tags)
                .publishPercentiles(0.90, 0.95)
                .publishPercentileHistogram()
                .register(registry);

        this.throughputSummary = DistributionSummary.builder("socket.throughput.tps")
                .tags(tags)
                .publishPercentiles(0.90, 0.95)
                .publishPercentileHistogram()
                .register(registry);

        meters.add(pressureSummary);
        meters.add(throughputSummary);

        /* ===== Gauges ===== */

        registerGauge("socket.queue.depth", queue);
        registerGauge("socket.pressure.tps.current", pressureTps);
        registerGauge("socket.throughput.tps.current", throughputTps);

        registerGauge("socket.pressure.tps.min", minPressureTps);
        registerGauge("socket.pressure.tps.max", maxPressureTps);

        registerGauge("socket.throughput.tps.min", minThroughputTps);
        registerGauge("socket.throughput.tps.max", maxThroughputTps);

        registerGauge("socket.latency.min", minLatency);
        registerGauge("socket.latency.max", maxLatency);

        registerGauge("socket.last.msg", lastMsg);
        registerGauge("socket.last.error", lastErr);
        registerGauge("socket.last.connect", lastConnect);
        registerGauge("socket.last.disconnect", lastDisconnect);
    }


    private void registerGauge(String name, AtomicLong ref) {
        meters.add(Gauge.builder(name, ref, AtomicLong::get)
                .register(registry));
    }

    public void onMessage() {
        long now = System.currentTimeMillis();

        msgIn.increment();
        queue.incrementAndGet();
        lastMsg.set(now);

        recordPressure(now);
    }

    public void onComplete(long latencyNs) {
        long now = System.currentTimeMillis();

        msgOut.increment();
        queue.updateAndGet(v -> Math.max(0, v - 1));
        lastMsg.set(now);

        recordThroughput(now);

        latency.record(latencyNs, TimeUnit.NANOSECONDS);

        minLatency.accumulateAndGet(latencyNs, Math::min);
        maxLatency.accumulateAndGet(latencyNs, Math::max);

        if (log.isDebugEnabled()) {
            log.debug("Complete took time {}ns", latencyNs);
        }
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

    public void resetWindowMetrics() {
        // RESET TPS window state (important for zero-allocation TPS)
        pressureCounter.reset();
        throughputCounter.reset();

        pressureWindow.set(-1);
        throughputWindow.set(-1);

        // TPS current
        pressureTps.set(0);
        throughputTps.set(0);

        // TPS min/max
        minPressureTps.set(Long.MAX_VALUE);
        maxPressureTps.set(0);

        minThroughputTps.set(Long.MAX_VALUE);
        maxThroughputTps.set(0);

        // latency min/max
        minLatency.set(Long.MAX_VALUE);
        maxLatency.set(0);

        log.info("SocketTelemetry window metrics reset id={}", id);
    }

    public void resetTpsBuffers() {
        pressureCounter.reset();
        throughputCounter.reset();

        pressureWindow.set(-1);
        throughputWindow.set(-1);

        pressureTps.set(0);
        throughputTps.set(0);

        log.info("SocketTelemetry TPS counters reset id={}", id);
    }

    public synchronized void resetAllMeters() {

        meters.forEach(registry::remove);
        meters.clear();

        Tags tags = Tags.of(
                "name", name,
                "id", id,
                "type", type
        );

        initMeters(tags);
        resetWindowMetrics();

        log.warn("SocketTelemetry FULL meter reset id={}", id);
    }

    private void recordThroughput(long now) {
        long window = now / TPS_WINDOW_MS;

        long prev = throughputWindow.get();
        if (prev != window && throughputWindow.compareAndSet(prev, window)) {
            throughputCounter.reset();
        }

        throughputCounter.increment();
        long current = throughputCounter.sum();

        throughputTps.set(current);
        minThroughputTps.accumulateAndGet(current, Math::min);
        maxThroughputTps.accumulateAndGet(current, Math::max);

        throughputSummary.record(current);
    }

    private void recordPressure(long now) {
        long window = now / TPS_WINDOW_MS;

        long prev = pressureWindow.get();
        if (prev != window && pressureWindow.compareAndSet(prev, window)) {
            pressureCounter.reset();
        }

        pressureCounter.increment();
        long current = pressureCounter.sum();

        pressureTps.set(current);
        minPressureTps.accumulateAndGet(current, Math::min);
        maxPressureTps.accumulateAndGet(current, Math::max);

        pressureSummary.record(current);
    }

    private static long extract(HistogramSnapshot snap, double p) {
        for (ValueAtPercentile v : snap.percentileValues()) {
            if (Double.compare(v.percentile(), p) == 0) {
                return (long) v.value();
            }
        }
        return 0;
    }

    public RuntimeState getRuntimeState() {
        long starttime = 0;
        String localHost = ":";
        String remoteHost = ":";
        int active = 0;
        String state = SocketState.DOWN.name();

        if (socket != null) {
            starttime = socket.getStartTime();
            List<SocketChannel> channels =
                    socket.channelPool().activeChannels();
            active = channels.size();
            if (socket instanceof NettyServerSocket server) {
                localHost = extractServerLocalHost(server);
            } else {
                localHost = extractLocalHost(channels);
            }
            remoteHost = extractRemoteHosts(channels);
            state = socket.getState().name();
        }

        return new RuntimeState(hashId,
                id,
                name,
                type,
                localHost,
                remoteHost,
                active,
                starttime,
                lastConnect.get(),
                lastDisconnect.get(),
                state
        );
    }

    public Queue getQueue() {
        return new Queue(hashId,
                id,
                name,
                type,
                (long) msgIn.count(),
                (long) msgOut.count(),
                queue.get(),
                (long) errCnt.count(),
                lastErr.get(),
                lastMsg.get());
    }

    public Metrics getMetrics() {
        long count = latency.count();
        long total = (long) latency.totalTime(TimeUnit.NANOSECONDS);

        HistogramSnapshot latencySnap = latency.takeSnapshot();
        HistogramSnapshot pressureSnap = pressureSummary.takeSnapshot();
        HistogramSnapshot throughputSnap = throughputSummary.takeSnapshot();

        return new Metrics(hashId,
                id,
                name,
                type,

                count == 0 ? 0 : total / count,
                minLatency.get() == Long.MAX_VALUE ? 0 : minLatency.get(),
                maxLatency.get(),
                extract(latencySnap, 0.90),
                extract(latencySnap, 0.95),

                pressureTps.get(),
                minPressureTps.get() == Long.MAX_VALUE ? 0 : minPressureTps.get(),
                maxPressureTps.get(),
                extract(pressureSnap, 0.90),
                extract(pressureSnap, 0.95),

                throughputTps.get(),
                minThroughputTps.get() == Long.MAX_VALUE ? 0 : minThroughputTps.get(),
                maxThroughputTps.get(),
                extract(throughputSnap, 0.90),
                extract(throughputSnap, 0.95)
        );
    }

    public void dispose() {
        if (!disposed.compareAndSet(false, true)) return;

        meters.forEach(registry::remove);
        meters.clear();
        socket = null;

        log.info("SocketTelemetry disposed id={}", id);
    }

    private String extractLocalHost(List<SocketChannel> channels) {
        if (channels == null || channels.isEmpty()) return "-";

        return channels.stream()
                .map(SocketChannel::channel)
                .filter(Objects::nonNull)
                .filter(Channel::isActive)
                .map(Channel::localAddress)
                .filter(InetSocketAddress.class::isInstance)
                .map(InetSocketAddress.class::cast)
                .map(a -> a.getHostString() + ":" + a.getPort())
                .distinct()
                .findFirst()
                .orElse("-");
    }

    private String extractRemoteHosts(List<SocketChannel> channels) {
        if (channels == null || channels.isEmpty()) return "-";

        return channels.stream()
                .map(SocketChannel::channel)
                .filter(Objects::nonNull)
                .filter(Channel::isActive)
                .map(Channel::remoteAddress)
                .filter(InetSocketAddress.class::isInstance)
                .map(InetSocketAddress.class::cast)
                .map(a -> a.getHostString() + ":" + a.getPort())
                .distinct()
                .collect(Collectors.joining(","));
    }

    private String extractServerLocalHost(NettyServerSocket socket) {
        Channel ch = socket.getServerChannel();
        if (ch == null) return "-";
        InetSocketAddress addr = (InetSocketAddress) ch.localAddress();
        return ":" + addr.getPort();
    }
}
