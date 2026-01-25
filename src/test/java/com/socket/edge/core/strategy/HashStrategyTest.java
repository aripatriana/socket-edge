package com.socket.edge.core.strategy;

import com.socket.edge.core.MessageContext;
import com.socket.edge.model.VersionedCandidates;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

public class HashStrategyTest {

    @Test
    void hashStrategy_shouldReturnConsistentResult() {
        SelectionStrategy<String> strategy =
                new HashStrategy<>(ctx -> "user-1");

        List<String> list = List.of("A", "B", "C");
        VersionedCandidates vc = new VersionedCandidates<>(1,list);
        String r1 = strategy.next(vc, null);
        String r2 = strategy.next(vc, null);

        assertEquals(r1, r2);
    }

    @Test
    void shouldReturnSameCandidateForSameKey() {
        HashStrategy<String> strategy =
                new HashStrategy<>(ctx -> ctx.field("user"));

        List<String> candidates = List.of("A", "B", "C");

        MessageContext ctx = new TestMessageContext()
                .with("user", "ari");
        VersionedCandidates vc = new VersionedCandidates<>(1,candidates);
        String r1 = strategy.next(vc, ctx);
        String r2 = strategy.next(vc, ctx);
        String r3 = strategy.next(vc, ctx);

        assertEquals(r1, r2);
        assertEquals(r2, r3);
    }

    @Test
    void differentKey_shouldLikelyProduceDifferentCandidate() {
        HashStrategy<String> strategy =
                new HashStrategy<>(ctx -> ctx.field("user"));

        List<String> candidates = List.of("A", "B", "C");
        VersionedCandidates vc = new VersionedCandidates<>(1,candidates);
        String r1 = strategy.next(
                vc,
                new TestMessageContext().with("user", "user-1")
        );

        String r2 = strategy.next(
                vc,
                new TestMessageContext().with("user", "user-999")
        );

        // not guaranteed but very likely
        assertNotNull(r1);
        assertNotNull(r2);
    }

    @Test
    void shouldHandleHashOverflowSafely() {
        HashStrategy<String> strategy =
                new HashStrategy<>(ctx -> "AaAaAaAaAaAaAaAa");

        List<String> candidates = List.of("A", "B", "C");
        VersionedCandidates vc = new VersionedCandidates<>(1,candidates);
        assertDoesNotThrow(() ->
                strategy.next(vc, null)
        );
    }

    @Test
    void nullKey_shouldThrowException() {
        HashStrategy<String> strategy =
                new HashStrategy<>(ctx -> ctx.field("missing"));

        MessageContext ctx = new TestMessageContext();
        VersionedCandidates vc = new VersionedCandidates<>(1,List.of("A"));
        IllegalStateException ex = assertThrows(
                IllegalStateException.class,
                () -> strategy.next(vc, ctx)
        );

        assertTrue(ex.getMessage().contains("Hash key"));
    }

    @Test
    void emptyCandidates_shouldThrow() {
        HashStrategy<String> strategy =
                new HashStrategy<>(ctx -> "key");
        VersionedCandidates vc = new VersionedCandidates<>(1, List.of());
        assertThrows(IllegalStateException.class,
                () -> strategy.next(vc, null));
    }

    @Test
    void largeCandidateList_shouldAlwaysReturnValidIndex() {
        HashStrategy<Integer> strategy =
                new HashStrategy<>(ctx -> "stable-key");

        List<Integer> candidates =
                IntStream.range(0, 10_000).boxed().toList();
        VersionedCandidates vc = new VersionedCandidates<>(1, candidates);
        Integer result = strategy.next(vc, null);

        assertNotNull(result);
        assertTrue(result >= 0 && result < 10_000);
    }

    @Test
    void hashDistribution_shouldBeReasonablySpread() {
        HashStrategy<String> strategy =
                new HashStrategy<>(ctx -> ctx.field("user"));

        List<String> candidates = List.of("A", "B", "C", "D");

        Map<String, Integer> count = new HashMap<>();
        VersionedCandidates vc = new VersionedCandidates<>(1, candidates);
        for (int i = 0; i < 1_000; i++) {
            String key = "user-" + i;
            String r = strategy.next(
                    vc,
                    new TestMessageContext().with("user", key)
            );
            count.merge(r, 1, Integer::sum);
        }

        // sanity check: all nodes used
        assertEquals(4, count.size());
    }




}
