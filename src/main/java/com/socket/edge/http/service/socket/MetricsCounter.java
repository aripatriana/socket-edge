package com.socket.edge.http.service.socket;

import com.socket.edge.model.Metrics;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

public class MetricsCounter {

    private String id;
    private String name;
    private String type;

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

    /* TPS calculation */
    private final LongAdder tpsCounter = new LongAdder();
    private final AtomicLong lastTpsTime = new AtomicLong(System.currentTimeMillis());
    private final AtomicLong tps = new AtomicLong(0);

    public MetricsCounter(MeterRegistry registry,
                          String id,
                          String name,
                          String type) {

        this.id = id;
        this.name = name;
        this.type = type;

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

    public void onComplete(long latencyMs) {
        msgOut.increment();
        queue.decrementAndGet();
        lastMsg.set(System.currentTimeMillis());

        latency.record(latencyMs, TimeUnit.MILLISECONDS);
        minLatency.accumulateAndGet(latencyMs, Math::min);
        maxLatency.accumulateAndGet(latencyMs, Math::max);
    }

    public void onError() {
        errCnt.increment();
        lastErr.set(System.currentTimeMillis());
    }

    public void onConnect() {
        lastConnect.set(System.currentTimeMillis());
    }

    public void onDisconnect() {
        lastDisconnect.set(System.currentTimeMillis());
    }

    /* ========================================================= */
    /* ====================== TPS LOGIC ======================== */
    /* ========================================================= */

    private void calculateTps() {
        long now = System.currentTimeMillis();
        long last = lastTpsTime.get();

        if (now - last >= 1000) {
            if (lastTpsTime.compareAndSet(last, now)) {
                tps.set(tpsCounter.sumThenReset());
            }
        }
    }

    /* ========================================================= */
    /* ======================= SNAPSHOT ======================== */
    /* ========================================================= */

    public Metrics snapshot() {

        long count = latency.count();
        long avgLatency = count == 0 ? 0 :
                TimeUnit.NANOSECONDS.toMillis((long) latency.totalTime(TimeUnit.NANOSECONDS) / count);

        long min = minLatency.get() == Long.MAX_VALUE ? 0 : minLatency.get();

        return new Metrics(
                id,
                name,
                type,
                avgLatency,
                min,
                maxLatency.get(),
                (long) msgIn.count(),
                (long) msgOut.count(),
                queue.get(),
                (long) errCnt.count(),
                lastErr.get(),
                tps.get(),
                lastMsg.get(),
                lastConnect.get(),
                lastDisconnect.get()
        );
    }
}
