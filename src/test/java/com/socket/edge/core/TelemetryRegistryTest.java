package com.socket.edge.core;

import com.socket.edge.constant.SocketState;
import com.socket.edge.constant.SocketType;
import com.socket.edge.core.socket.AbstractSocket;
import com.socket.edge.core.socket.SocketChannelPooling;
import com.socket.edge.model.Metrics;
import com.socket.edge.model.Queue;
import com.socket.edge.model.RuntimeState;
import com.socket.edge.model.SocketEndpoint;
import com.socket.edge.utils.CommonUtil;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.*;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class TelemetryRegistryTest {

    private MeterRegistry meterRegistry;
    private TelemetryRegistry telemetryRegistry;

    @BeforeEach
    void setup() {
        meterRegistry = new SimpleMeterRegistry();
        telemetryRegistry = new TelemetryRegistry(meterRegistry);
    }

    private AbstractSocket socket(String id, String name, SocketType type) {
        AbstractSocket socket = mock(AbstractSocket.class);
        when(socket.getId()).thenReturn(id);
        when(socket.getName()).thenReturn(name);
        when(socket.getType()).thenReturn(type);
        return socket;
    }

    /* ---------------------------------------------------
     * REGISTER
     * --------------------------------------------------- */

    @Test
    @DisplayName("register() should create telemetry on first call")
    void register_firstTime() {
        AbstractSocket socket = socket("A", "fello", SocketType.SERVER);
        SocketEndpoint se = new SocketEndpoint("127.0.0.1", 7000, 100, 1);

        SocketTelemetry telemetry = telemetryRegistry.register(socket, se);

        assertThat(telemetry).isNotNull();
        assertThat(meterRegistry.getMeters()).isNotEmpty();
    }

    @Test
    @DisplayName("register() should be idempotent for same socket id")
    void register_idempotent() {
        AbstractSocket socket1 = socket("A", "fello", SocketType.SERVER);
        AbstractSocket socket2 = socket("A", "fello", SocketType.SERVER);
        SocketEndpoint se = new SocketEndpoint("127.0.0.1", 7000, 100, 1);

        SocketTelemetry t1 = telemetryRegistry.register(socket1, se);
        SocketTelemetry t2 = telemetryRegistry.register(socket2, se);

        assertThat(t1).isSameAs(t2);
    }

    /* ---------------------------------------------------
     * UNREGISTER
     * --------------------------------------------------- */

    @Test
    @DisplayName("unregister() should remove telemetry and return it")
    void unregister_existing() {
        AbstractSocket socket = socket("A", "fello", SocketType.SERVER);
        SocketEndpoint se = new SocketEndpoint("127.0.0.1", 7000, 100, 1);

        SocketTelemetry registered = telemetryRegistry.register(socket, se);
        SocketTelemetry removed = telemetryRegistry.unregister(socket, se);

        assertThat(removed).isSameAs(registered);
        assertThat(telemetryRegistry.getMetricsById("A")).isNull();
        assertThat(telemetryRegistry.getQueueById("A")).isNull();
        assertThat(telemetryRegistry.getRuntimeStateById("A")).isNull();
    }

    @Test
    @DisplayName("unregister() should return null if socket not registered")
    void unregister_missing() {
        AbstractSocket socket = socket("X", "fello", SocketType.SERVER);
        SocketEndpoint se = new SocketEndpoint("127.0.0.1", 7000, 100, 1);

        SocketTelemetry removed = telemetryRegistry.unregister(socket, se);

        assertThat(removed).isNull();
    }

    /* ---------------------------------------------------
     * GET METRIC
     * --------------------------------------------------- */

    @Test
    @DisplayName("getMetric() should return metric for registered socket")
    void getMetric_existing() {
        AbstractSocket socket = socket("A", "fello", SocketType.SERVER);
        SocketEndpoint se = new SocketEndpoint("127.0.0.1", 7000, 100, 1);

        telemetryRegistry.register(socket, se);

        String hashId = CommonUtil.hashId(socket.getId(), se.id().id());
        Metrics result = telemetryRegistry.getMetricsById(hashId);

        assertThat(result).isNotNull();
        assertThat(result.id()).isEqualTo("A");
        assertThat(result.name()).isEqualTo("fello");
        assertThat(result.type()).isEqualTo("SERVER");
    }

    @Test
    @DisplayName("getQueue() should return metric for registered socket")
    void getQueue_existing() {
        AbstractSocket socket = socket("A", "fello", SocketType.SERVER);
        SocketEndpoint se = new SocketEndpoint("127.0.0.1", 7000, 100, 1);

        telemetryRegistry.register(socket, se);

        String hashId = CommonUtil.hashId(socket.getId(), se.id().id());
        Queue result = telemetryRegistry.getQueueById(hashId);

        assertThat(result).isNotNull();
        assertThat(result.id()).isEqualTo("A");
        assertThat(result.name()).isEqualTo("fello");
        assertThat(result.type()).isEqualTo("SERVER");
    }

    @Test
    @DisplayName("getMetric() should return null for missing id")
    void getMetric_missing() {
        assertThat(telemetryRegistry.getMetricsById("UNKNOWN")).isNull();
    }

    @Test
    @DisplayName("getQueue() should return null for missing id")
    void getQueue_missing() {
        assertThat(telemetryRegistry.getQueueById("UNKNOWN")).isNull();
    }


    /* ---------------------------------------------------
     * GET ALL METRICS
     * --------------------------------------------------- */

    @Test
    @DisplayName("getAllMetrics() should return sorted metrics by id")
    void getAllMetrics_sorted() {
        AbstractSocket s1 = socket("B", "fello", SocketType.SERVER);
        AbstractSocket s2 = socket("A", "fello", SocketType.SERVER);
        SocketEndpoint se = new SocketEndpoint("127.0.0.1", 7000, 100, 1);

        SocketTelemetry t1 = spy(telemetryRegistry.register(s1, se));
        SocketTelemetry t2 = spy(telemetryRegistry.register(s2, se));

        Metrics m1 = mock(Metrics.class);
        when(m1.id()).thenReturn("B");
        Metrics m2 = mock(Metrics.class);
        when(m2.id()).thenReturn("A");

        doReturn(m1).when(t1).getMetrics();
        doReturn(m2).when(t2).getMetrics();

        List<Metrics> result = telemetryRegistry.getAllMetrics();

        assertThat(result)
                .extracting(Metrics::id)
                .containsExactly("A", "B");
    }

    @Test
    @DisplayName("getAllQueue() should return sorted queue by id")
    void getAllQueue_sorted() {
        AbstractSocket s1 = socket("B", "fello", SocketType.SERVER);
        AbstractSocket s2 = socket("A", "fello", SocketType.SERVER);
        SocketEndpoint se = new SocketEndpoint("127.0.0.1", 7000, 100, 1);

        SocketTelemetry t1 = spy(telemetryRegistry.register(s1, se));
        SocketTelemetry t2 = spy(telemetryRegistry.register(s2, se));

        Queue m1 = mock(Queue.class);
        when(m1.id()).thenReturn("B");
        Queue m2 = mock(Queue.class);
        when(m2.id()).thenReturn("A");

        doReturn(m1).when(t1).getQueue();
        doReturn(m2).when(t2).getQueue();

        List<Queue> result = telemetryRegistry.getAllQueue();

        assertThat(result)
                .extracting(Queue::id)
                .containsExactly("A", "B");
    }

    @Test
    @DisplayName("getAllMetrics() should return empty list if no telemetry")
    void getAllMetrics_empty() {
        assertThat(telemetryRegistry.getAllMetrics()).isEmpty();
    }

    @Test
    @DisplayName("getAllQueue() should return empty list if no telemetry")
    void getAllQueue_empty() {
        assertThat(telemetryRegistry.getAllQueue()).isEmpty();
    }

    /* ---------------------------------------------------
     * GET RUNTIME STATE
     * --------------------------------------------------- */

    @Test
    @DisplayName("getRuntimeState() should return runtime state for registered socket")
    void getRuntimeState_existing() {
        AbstractSocket socket = socket("A", "fello", SocketType.SERVER);
        SocketEndpoint se = new SocketEndpoint("127.0.0.1", 7000, 100, 1);

        when(socket.getState()).thenReturn(SocketState.UP);

        SocketTelemetry telemetry = spy(telemetryRegistry.register(socket, se));
        SocketChannelPooling pool = mock(SocketChannelPooling.class);
        when(socket.channelPool()).thenReturn(pool);

        String hashId = CommonUtil.hashId(socket.getId(), se.id().id());
        RuntimeState result = telemetryRegistry.getRuntimeStateById(hashId);

        assertThat(result.id()).isEqualTo("A");
    }

    @Test
    @DisplayName("getAllRuntimeState() should return sorted runtime states")
    void getAllRuntimeState_sorted() {
        AbstractSocket s1 = socket("C", "fello", SocketType.SERVER);
        AbstractSocket s2 = socket("A", "fello", SocketType.SERVER);
        AbstractSocket s3 = socket("B", "fello", SocketType.SERVER);
        SocketEndpoint se = new SocketEndpoint("127.0.0.1", 7000, 100, 1);

        when(s1.getState()).thenReturn(SocketState.UP);
        when(s2.getState()).thenReturn(SocketState.UP);
        when(s3.getState()).thenReturn(SocketState.UP);

        SocketChannelPooling pool = mock(SocketChannelPooling.class);
        when(s1.channelPool()).thenReturn(pool);
        when(s2.channelPool()).thenReturn(pool);
        when(s3.channelPool()).thenReturn(pool);

        SocketTelemetry t1 = spy(telemetryRegistry.register(s1, se));
        SocketTelemetry t2 = spy(telemetryRegistry.register(s2, se));
        SocketTelemetry t3 = spy(telemetryRegistry.register(s3, se));

        RuntimeState r1 = mock(RuntimeState.class);
        when(r1.id()).thenReturn("C");
        RuntimeState r2 = mock(RuntimeState.class);
        when(r2.id()).thenReturn("A");
        RuntimeState r3 = mock(RuntimeState.class);
        when(r3.id()).thenReturn("B");

        doReturn(r1).when(t1).getRuntimeState();
        doReturn(r2).when(t2).getRuntimeState();
        doReturn(r3).when(t3).getRuntimeState();

        List<RuntimeState> result = telemetryRegistry.getAllRuntimeState();

        assertThat(result)
                .extracting(RuntimeState::id)
                .containsExactly("A", "B", "C");
    }

    /* ---------------------------------------------------
     * CONCURRENCY
     * --------------------------------------------------- */

    @Test
    @DisplayName("register() should be thread-safe")
    void register_concurrent() throws Exception {
        int threads = 20;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        SocketEndpoint se = new SocketEndpoint("127.0.0.1", 7000, 100, 1);

        AbstractSocket socket = socket("CONCURRENT", "fello", SocketType.SERVER);

        List<Callable<SocketTelemetry>> tasks =
                IntStream.range(0, threads)
                        .mapToObj(i -> (Callable<SocketTelemetry>) () ->
                                telemetryRegistry.register(socket, se))
                        .toList();

        List<Future<SocketTelemetry>> futures = executor.invokeAll(tasks);

        SocketTelemetry first = futures.get(0).get();

        for (Future<SocketTelemetry> f : futures) {
            assertThat(f.get()).isSameAs(first);
        }

        executor.shutdown();
    }

    @Test
    @DisplayName("register() should return same telemetry for same socket id")
    void register_shouldBeIdempotent() {
        AbstractSocket socket = socket("A", "fello", SocketType.SERVER);
        SocketEndpoint se = new SocketEndpoint("127.0.0.1", 7000, 100, 1);

        SocketTelemetry t1 = telemetryRegistry.register(socket, se);
        SocketTelemetry t2 = telemetryRegistry.register(socket, se);

        assertThat(t1).isSameAs(t2);
    }

    @Test
    @DisplayName("register() should register counters and timers")
    void register_shouldRegisterCoreMeters() {
        AbstractSocket socket = socket("A", "fello", SocketType.SERVER);
        SocketEndpoint se = new SocketEndpoint("127.0.0.1", 7000, 100, 1);

        telemetryRegistry.register(socket, se);

        assertThat(meterRegistry.find("socket.msg.in").counter()).isNotNull();
        assertThat(meterRegistry.find("socket.msg.out").counter()).isNotNull();
        assertThat(meterRegistry.find("socket.error.count").counter()).isNotNull();
        assertThat(meterRegistry.find("socket.latency").timer()).isNotNull();
    }

    @Test
    @DisplayName("register() should register all gauges")
    void register_shouldRegisterGauges() {
        AbstractSocket socket = socket("A", "fello", SocketType.SERVER);
        SocketEndpoint se = new SocketEndpoint("127.0.0.1", 7000, 100, 1);

        telemetryRegistry.register(socket, se);

        assertThat(meterRegistry.find("socket.queue.depth").gauge()).isNotNull();
        assertThat(meterRegistry.find("socket.throughput.tps.current").gauge()).isNotNull();
        assertThat(meterRegistry.find("socket.throughput.tps.min").gauge()).isNotNull();
        assertThat(meterRegistry.find("socket.throughput.tps.max").gauge()).isNotNull();
        assertThat(meterRegistry.find("socket.pressure.tps.current").gauge()).isNotNull();
        assertThat(meterRegistry.find("socket.pressure.tps.min").gauge()).isNotNull();
        assertThat(meterRegistry.find("socket.pressure.tps.max").gauge()).isNotNull();
        assertThat(meterRegistry.find("socket.latency.min").gauge()).isNotNull();
        assertThat(meterRegistry.find("socket.latency.max").gauge()).isNotNull();
    }

    @Test
    @DisplayName("register() should attach correct tags")
    void register_shouldAttachCorrectTags() {
        AbstractSocket socket = socket("A", "fello", SocketType.SERVER);
        SocketEndpoint se = new SocketEndpoint("127.0.0.1", 7000, 100, 1);

        telemetryRegistry.register(socket, se);

        Meter meter = meterRegistry.find("socket.msg.in").meter();
        assertThat(meter).isNotNull();

        assertThat(meter.getId().getTag("socketId")).isEqualTo("A");
//        assertThat(meter.getId().getTag("socketName")).isEqualTo("fello");
//        assertThat(meter.getId().getTag("type")).isEqualTo("SERVER");
    }

    @Test
    @DisplayName("register() should not duplicate meters")
    void register_shouldNotDuplicateMeters() {
        AbstractSocket socket = socket("A", "fello", SocketType.SERVER);
        SocketEndpoint se = new SocketEndpoint("127.0.0.1", 7000, 100, 1);

        telemetryRegistry.register(socket, se);
        int meterCountAfterFirst = meterRegistry.getMeters().size();

        telemetryRegistry.register(socket, se);
        int meterCountAfterSecond = meterRegistry.getMeters().size();

        assertThat(meterCountAfterSecond).isEqualTo(meterCountAfterFirst);
    }

}
