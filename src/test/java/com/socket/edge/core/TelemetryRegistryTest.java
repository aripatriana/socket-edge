package com.socket.edge.core;

import com.socket.edge.core.socket.AbstractSocket;
import com.socket.edge.model.Metrics;
import com.socket.edge.model.RuntimeState;
import io.micrometer.core.instrument.MeterRegistry;
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
        meterRegistry = mock(MeterRegistry.class);
        telemetryRegistry = new TelemetryRegistry(meterRegistry);
    }

    private AbstractSocket socket(String id) {
        AbstractSocket socket = mock(AbstractSocket.class);
        when(socket.getId()).thenReturn(id);
        return socket;
    }

    /* ---------------------------------------------------
     * REGISTER
     * --------------------------------------------------- */

    @Test
    @DisplayName("register() should create telemetry on first call")
    void register_firstTime() {
        AbstractSocket socket = socket("A");

        SocketTelemetry telemetry = telemetryRegistry.register(socket);

        assertThat(telemetry).isNotNull();
    }

    @Test
    @DisplayName("register() should be idempotent for same socket id")
    void register_idempotent() {
        AbstractSocket socket1 = socket("A");
        AbstractSocket socket2 = socket("A");

        SocketTelemetry t1 = telemetryRegistry.register(socket1);
        SocketTelemetry t2 = telemetryRegistry.register(socket2);

        assertThat(t1).isSameAs(t2);
    }

    /* ---------------------------------------------------
     * UNREGISTER
     * --------------------------------------------------- */

    @Test
    @DisplayName("unregister() should remove telemetry and return it")
    void unregister_existing() {
        AbstractSocket socket = socket("A");

        SocketTelemetry registered = telemetryRegistry.register(socket);
        SocketTelemetry removed = telemetryRegistry.unregister(socket);

        assertThat(removed).isSameAs(registered);
        assertThat(telemetryRegistry.getMetric("A")).isNull();
    }

    @Test
    @DisplayName("unregister() should return null if socket not registered")
    void unregister_missing() {
        AbstractSocket socket = socket("X");

        SocketTelemetry removed = telemetryRegistry.unregister(socket);

        assertThat(removed).isNull();
    }

    /* ---------------------------------------------------
     * GET METRIC
     * --------------------------------------------------- */

    @Test
    @DisplayName("getMetric() should return metric for registered socket")
    void getMetric_existing() {
        AbstractSocket socket = socket("A");
        SocketTelemetry telemetry = spy(telemetryRegistry.register(socket));

        Metrics metrics = mock(Metrics.class);
        when(metrics.id()).thenReturn("A");
        doReturn(metrics).when(telemetry).getMetrics();

        Metrics result = telemetryRegistry.getMetric("A");

        assertThat(result).isSameAs(metrics);
    }

    @Test
    @DisplayName("getMetric() should return null for missing id")
    void getMetric_missing() {
        assertThat(telemetryRegistry.getMetric("UNKNOWN")).isNull();
    }

    /* ---------------------------------------------------
     * GET ALL METRICS
     * --------------------------------------------------- */

    @Test
    @DisplayName("getAllMetrics() should return sorted metrics by id")
    void getAllMetrics_sorted() {
        AbstractSocket s1 = socket("B");
        AbstractSocket s2 = socket("A");

        SocketTelemetry t1 = spy(telemetryRegistry.register(s1));
        SocketTelemetry t2 = spy(telemetryRegistry.register(s2));

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
    @DisplayName("getAllMetrics() should return empty list if no telemetry")
    void getAllMetrics_empty() {
        assertThat(telemetryRegistry.getAllMetrics()).isEmpty();
    }

    /* ---------------------------------------------------
     * GET RUNTIME STATE
     * --------------------------------------------------- */

    @Test
    @DisplayName("getRuntimeState() should return runtime state for registered socket")
    void getRuntimeState_existing() {
        AbstractSocket socket = socket("A");
        SocketTelemetry telemetry = spy(telemetryRegistry.register(socket));

        RuntimeState state = mock(RuntimeState.class);
        when(state.id()).thenReturn("A");
        doReturn(state).when(telemetry).getRuntimeState();

        RuntimeState result = telemetryRegistry.getRuntimeState("A");

        assertThat(result).isSameAs(state);
    }

    @Test
    @DisplayName("getAllRuntimeState() should return sorted runtime states")
    void getAllRuntimeState_sorted() {
        AbstractSocket s1 = socket("C");
        AbstractSocket s2 = socket("A");
        AbstractSocket s3 = socket("B");

        SocketTelemetry t1 = spy(telemetryRegistry.register(s1));
        SocketTelemetry t2 = spy(telemetryRegistry.register(s2));
        SocketTelemetry t3 = spy(telemetryRegistry.register(s3));

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

        AbstractSocket socket = socket("CONCURRENT");

        List<Callable<SocketTelemetry>> tasks =
                IntStream.range(0, threads)
                        .mapToObj(i -> (Callable<SocketTelemetry>) () ->
                                telemetryRegistry.register(socket))
                        .toList();

        List<Future<SocketTelemetry>> futures = executor.invokeAll(tasks);

        SocketTelemetry first = futures.get(0).get();

        for (Future<SocketTelemetry> f : futures) {
            assertThat(f.get()).isSameAs(first);
        }

        executor.shutdown();
    }
}
