package com.socket.edge.core;

import com.socket.edge.constant.SocketType;
import com.socket.edge.core.socket.AbstractSocket;
import com.socket.edge.core.socket.SocketChannelPooling;
import com.socket.edge.model.EndpointKey;
import com.socket.edge.model.Metrics;
import com.socket.edge.model.Queue;
import com.socket.edge.utils.CommonUtil;
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
    private SocketChannelPooling channelPool;

    private SocketTelemetry telemetry;

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();

        when(socket.getId()).thenReturn("sock-1");
        when(socket.getName()).thenReturn("ISO-SOCKET");
        when(socket.getType()).thenReturn(SocketType.CLIENT);
        telemetry = new SocketTelemetry(registry, socket, CommonUtil.hashId(socket.getId(), EndpointKey.from("127.0.0.1", 7000).id()));
    }

    @Test
    void shouldIncreaseMsgInAndQueue() {
        telemetry.onMessage();

        Queue queue = telemetry.getQueue();

        assertThat(queue.msgIn()).isEqualTo(1);
        assertThat(queue.queue()).isEqualTo(1);
    }

    @Test
    void shouldDecreaseQueueOnComplete() {
        telemetry.onMessage();
        telemetry.onComplete(1_000_000);

        Queue queue = telemetry.getQueue();

        assertThat(queue.msgOut()).isEqualTo(1);
        assertThat(queue.queue()).isZero();
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

        Queue queue = telemetry.getQueue();

        assertThat(queue.errCnt()).isEqualTo(1);
        assertThat(queue.lastErr()).isGreaterThan(0);
    }

    @Test
    void shouldNotProduceNegativeQueue() {
        telemetry.onComplete(1_000_000);

        Queue queue = telemetry.getQueue();

        assertThat(queue.queue()).isGreaterThanOrEqualTo(0);
    }

    @Test
    void queueShouldNeverGoBelowZero_underAnySequence() {
        telemetry.onComplete(1_000_000);
        telemetry.onComplete(1_000_000);
        telemetry.onError();

        Queue queue = telemetry.getQueue();

        assertThat(queue.queue()).isGreaterThanOrEqualTo(0);
    }

    @Test
    void shouldHandleOutOfOrderCompleteBeforeMessage() {
        telemetry.onComplete(1_000_000);
        telemetry.onMessage();

        Queue queue = telemetry.getQueue();

        assertThat(queue.queue()).isEqualTo(1);
        assertThat(queue.msgIn()).isEqualTo(1);
        assertThat(queue.msgOut()).isEqualTo(1);
    }

    @Test
    void duplicateCompleteShouldNotCorruptQueue() {
        telemetry.onMessage();
        telemetry.onComplete(1_000_000);
        telemetry.onComplete(1_000_000); // duplicate

        Queue queue = telemetry.getQueue();

        assertThat(queue.queue()).isZero();
        assertThat(queue.msgOut()).isEqualTo(2);
    }

    @Test
    void errorAndCompleteRaceShouldNotBreakQueue() {
        telemetry.onMessage();
        telemetry.onError();
        telemetry.onComplete(1_000_000);

        Queue queue = telemetry.getQueue();

        assertThat(queue.queue()).isZero();
        assertThat(queue.errCnt()).isEqualTo(1);
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

        Queue queue = telemetry.getQueue();

        assertThat(queue.queue()).isZero();
        assertThat(queue.msgIn()).isEqualTo(burst);
        assertThat(queue.msgOut()).isEqualTo(burst);
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

        Queue queue = telemetry.getQueue();

        assertThat(queue.queue()).isGreaterThanOrEqualTo(0);
    }


    @Test
    void pressureAndThroughputTpsShouldNeverBeNegative() throws InterruptedException {
        telemetry.onMessage();
        telemetry.onMessage();
        telemetry.onComplete(1_000_000);

        Thread.sleep(1100); // move to next TPS window

        telemetry.onMessage();
        telemetry.onComplete(1_000_000);

        Metrics metrics = telemetry.getMetrics();

        assertThat(metrics.pressureTps()).isGreaterThanOrEqualTo(0);
        assertThat(metrics.throughputTps()).isGreaterThanOrEqualTo(0);
    }

    @Test
    void resetWindowMetricsShouldClearTpsAndLatencyWindow() {
        telemetry.onMessage();
        telemetry.onComplete(1_000_000);
        telemetry.onMessage();
        telemetry.onComplete(3_000_000);

        telemetry.resetWindowMetrics();

        Metrics metrics = telemetry.getMetrics();

        assertThat(metrics.pressureTps()).isZero();
        assertThat(metrics.throughputTps()).isZero();

        assertThat(metrics.minPressureTps()).isZero();
        assertThat(metrics.maxPressureTps()).isZero();

        assertThat(metrics.minThroughputTps()).isZero();
        assertThat(metrics.maxThroughputTps()).isZero();

        assertThat(metrics.minLatency()).isZero();
        assertThat(metrics.maxLatency()).isZero();
    }

    @Test
    void tpsWindowShouldResetAutomaticallyAfterOneSecond() throws InterruptedException {
        telemetry.onMessage();
        telemetry.onMessage();

        Metrics first = telemetry.getMetrics();
        long firstPressure = first.pressureTps();

        Thread.sleep(1100);

        telemetry.onMessage();

        Metrics second = telemetry.getMetrics();

        assertThat(second.pressureTps())
                .isLessThanOrEqualTo(firstPressure + 1);
    }


    @Test
    void disposeShouldRemoveAllMeters() {
        telemetry.onMessage();
        telemetry.dispose();

        assertThat(registry.getMeters()).isEmpty();
    }


}
