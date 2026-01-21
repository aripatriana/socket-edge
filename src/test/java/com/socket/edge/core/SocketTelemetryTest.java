package com.socket.edge.core;

import com.socket.edge.constant.SocketType;
import com.socket.edge.core.socket.AbstractSocket;
import com.socket.edge.core.socket.SocketChannelPool;
import com.socket.edge.model.Metrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SocketTelemetryTest {

    private SimpleMeterRegistry registry;

    @Mock
    private AbstractSocket socket;

    @Mock
    private SocketChannelPool channelPool;

    private SocketTelemetry telemetry;

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();

        when(socket.getId()).thenReturn("sock-1");
        when(socket.getName()).thenReturn("ISO-SOCKET");
        when(socket.getType()).thenReturn(SocketType.CLIENT);
        telemetry = new SocketTelemetry(registry, socket);
    }

    @Test
    void shouldIncreaseMsgInAndQueue() {
        telemetry.onMessage();

        Metrics metrics = telemetry.getMetrics();

        assertThat(metrics.msgIn()).isEqualTo(1);
        assertThat(metrics.queue()).isEqualTo(1);
    }

    @Test
    void shouldDecreaseQueueOnComplete() {
        telemetry.onMessage();
        telemetry.onComplete(1_000_000);

        Metrics metrics = telemetry.getMetrics();

        assertThat(metrics.msgOut()).isEqualTo(1);
        assertThat(metrics.queue()).isZero();
    }

    @Test
    void shouldRecordLatencyMinMaxAvg() {
        telemetry.onMessage();
        telemetry.onComplete(1_000_000);

        telemetry.onMessage();
        telemetry.onComplete(3_000_000);

        Metrics metrics = telemetry.getMetrics();

        assertThat(metrics.minLatency()).isEqualTo(1_000_000);
        assertThat(metrics.maxLatency()).isEqualTo(3_000_000);
        assertThat(metrics.avgLatency()).isEqualTo(2_000_000);
    }

    @Test
    void shouldRecordError() {
        telemetry.onError();

        Metrics metrics = telemetry.getMetrics();

        assertThat(metrics.errCnt()).isEqualTo(1);
        assertThat(metrics.lastErr()).isGreaterThan(0);
    }

    @Test
    void shouldNotProduceNegativeQueue() {
        telemetry.onComplete(1_000_000);

        Metrics metrics = telemetry.getMetrics();

        assertThat(metrics.queue()).isGreaterThanOrEqualTo(0);
    }

    @Test
    void queueShouldNeverGoBelowZero_underAnySequence() {
        telemetry.onComplete(1_000_000);
        telemetry.onComplete(1_000_000);
        telemetry.onError();

        Metrics metrics = telemetry.getMetrics();

        assertThat(metrics.queue()).isGreaterThanOrEqualTo(0);
    }

    @Test
    void shouldHandleOutOfOrderCompleteBeforeMessage() {
        telemetry.onComplete(1_000_000);
        telemetry.onMessage();

        Metrics metrics = telemetry.getMetrics();

        assertThat(metrics.queue()).isEqualTo(1);
        assertThat(metrics.msgIn()).isEqualTo(1);
        assertThat(metrics.msgOut()).isEqualTo(1);
    }

    @Test
    void duplicateCompleteShouldNotCorruptQueue() {
        telemetry.onMessage();
        telemetry.onComplete(1_000_000);
        telemetry.onComplete(1_000_000); // duplicate

        Metrics metrics = telemetry.getMetrics();

        assertThat(metrics.queue()).isZero();
        assertThat(metrics.msgOut()).isEqualTo(2);
    }

    @Test
    void errorAndCompleteRaceShouldNotBreakQueue() {
        telemetry.onMessage();
        telemetry.onError();
        telemetry.onComplete(1_000_000);

        Metrics metrics = telemetry.getMetrics();

        assertThat(metrics.queue()).isZero();
        assertThat(metrics.errCnt()).isEqualTo(1);
    }

    @Test
    void burstTrafficShouldLeaveQueueAtZero() {
        int burst = 1_000;

        for (int i = 0; i < burst; i++) {
            telemetry.onMessage();
        }

        for (int i = 0; i < burst; i++) {
            telemetry.onComplete(1_000_000);
        }

        Metrics metrics = telemetry.getMetrics();

        assertThat(metrics.queue()).isZero();
        assertThat(metrics.msgIn()).isEqualTo(burst);
        assertThat(metrics.msgOut()).isEqualTo(burst);
    }

    @Test
    void concurrentMessageAndCompleteShouldMaintainInvariant() throws Exception {
        int threads = 8;
        int operations = 10_000;

        ExecutorService executor = Executors.newFixedThreadPool(threads);

        Runnable producer = () -> {
            for (int i = 0; i < operations; i++) {
                telemetry.onMessage();
            }
        };

        Runnable consumer = () -> {
            for (int i = 0; i < operations; i++) {
                telemetry.onComplete(1_000_000);
            }
        };

        List<Callable<Void>> tasks = List.of(
                Executors.callable(producer, null),
                Executors.callable(consumer, null)
        );

        executor.invokeAll(tasks);
        executor.shutdown();
        executor.awaitTermination(10, TimeUnit.SECONDS);

        Metrics metrics = telemetry.getMetrics();

        assertThat(metrics.queue()).isGreaterThanOrEqualTo(0);
    }


    @Test
    void tpsShouldNeverBeNegative() throws InterruptedException {
        telemetry.onMessage();
        telemetry.onMessage();

        Thread.sleep(1100); // roll TPS window

        telemetry.onMessage();

        Metrics metrics = telemetry.getMetrics();

        assertThat(metrics.avgTps()).isGreaterThanOrEqualTo(0);
    }


}
