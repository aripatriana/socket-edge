package com.socket.edge.core.strategy;

import com.socket.edge.model.VersionedCandidates;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.*;

public class LeastConnectionStrategyTest {

    @Test
    void shouldPickCandidateWithLeastInflight() {
        SelectionStrategy<TestCandidate> strategy =
                new LeastConnectionStrategy<>();

        TestCandidate a = new TestCandidate("A", 1, 1, 10);
        TestCandidate b = new TestCandidate("B", 1, 1, 2);
        TestCandidate c = new TestCandidate("C", 1, 1, 7);
        VersionedCandidates vc = new VersionedCandidates<>(1, List.of(a, b, c));
        TestCandidate result =
                strategy.next(vc, null);

        assertEquals("B", result.toString());
    }

    @Test
    void inflightChange_shouldAffectSelection() {
        SelectionStrategy<TestCandidate> strategy =
                new LeastConnectionStrategy<>();

        TestCandidate a = new TestCandidate("A", 1, 1, 1);
        TestCandidate b = new TestCandidate("B", 1, 1, 2);
        VersionedCandidates vc = new VersionedCandidates<>(1, List.of(a, b));
        // awal
        assertEquals("A",
                strategy.next(vc, null).toString());

        // simulate load
        a.increment(); // A = 2
        a.increment(); // A = 3

        assertEquals("B",
                strategy.next(vc, null).toString());
    }

    @Test
    void sameInflight_shouldBeDeterministic() {
        SelectionStrategy<TestCandidate> strategy =
                new LeastConnectionStrategy<>();

        TestCandidate a = new TestCandidate("A", 1, 1, 5);
        TestCandidate b = new TestCandidate("B", 1, 1, 5);
        VersionedCandidates vc = new VersionedCandidates<>(1, List.of(a, b));
        TestCandidate r1 =
                strategy.next(vc, null);

        TestCandidate r2 =
                strategy.next(vc, null);

        assertEquals(r1, r2);
    }

    @Test
    void singleCandidate_shouldAlwaysBeReturned() {
        SelectionStrategy<TestCandidate> strategy =
                new LeastConnectionStrategy<>();

        TestCandidate a = new TestCandidate("A", 1, 1, 0);
        VersionedCandidates vc = new VersionedCandidates<>(1, List.of(a));
        for (int i = 0; i < 10; i++) {
            a.increment();
            assertSame(a, strategy.next(vc, null));
        }
    }

    @Test
    void negativeInflight_shouldStillWork() {
        SelectionStrategy<TestCandidate> strategy =
                new LeastConnectionStrategy<>();

        TestCandidate a = new TestCandidate("A", 1, 1, -5);
        TestCandidate b = new TestCandidate("B", 1, 1, 0);
        VersionedCandidates vc = new VersionedCandidates<>(1, List.of(a, b));
        TestCandidate result =
                strategy.next(vc, null);

        assertEquals("A", result.toString());
    }

    @Test
    void emptyCandidates_shouldThrowException() {
        SelectionStrategy<TestCandidate> strategy =
                new LeastConnectionStrategy<>();

        assertThrows(
                IllegalStateException.class,
                () -> strategy.next(new VersionedCandidates<>(1, List.of()), null)
        );
    }

    @Test
    void concurrentInflightUpdate_shouldNotThrow() throws Exception {
        SelectionStrategy<TestCandidate> strategy =
                new LeastConnectionStrategy<>();

        TestCandidate a = new TestCandidate("A", 1, 1, 5);
        TestCandidate b = new TestCandidate("B", 1, 1, 5);

        List<TestCandidate> list = List.of(a, b);

        ExecutorService es = Executors.newFixedThreadPool(8);

        VersionedCandidates vc = new VersionedCandidates<>(1, list);
        Runnable incA = () -> {
            for (int i = 0; i < 100; i++) a.increment();
        };

        Runnable incB = () -> {
            for (int i = 0; i < 50; i++) b.increment();
        };
        Callable<TestCandidate> select =
                () -> strategy.next(vc, null);

        es.submit(incA);
        es.submit(incB);

        List<Future<TestCandidate>> results =
                es.invokeAll(Collections.nCopies(50, select));

        for (Future<TestCandidate> f : results) {
            assertNotNull(f.get());
        }

        es.shutdown();
    }

}
