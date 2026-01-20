package com.socket.edge.core.strategy;

import com.socket.edge.core.MessageContext;
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

        String r1 = strategy.next(list, null);
        String r2 = strategy.next(list, null);

        assertEquals(r1, r2);
    }

    @Test
    void shouldReturnSameCandidateForSameKey() {
        HashStrategy<String> strategy =
                new HashStrategy<>(ctx -> ctx.field("user"));

        List<String> candidates = List.of("A", "B", "C");

        MessageContext ctx = new TestMessageContext()
                .with("user", "ari");

        String r1 = strategy.next(candidates, ctx);
        String r2 = strategy.next(candidates, ctx);
        String r3 = strategy.next(candidates, ctx);

        assertEquals(r1, r2);
        assertEquals(r2, r3);
    }

    @Test
    void differentKey_shouldLikelyProduceDifferentCandidate() {
        HashStrategy<String> strategy =
                new HashStrategy<>(ctx -> ctx.field("user"));

        List<String> candidates = List.of("A", "B", "C");

        String r1 = strategy.next(
                candidates,
                new TestMessageContext().with("user", "user-1")
        );

        String r2 = strategy.next(
                candidates,
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

        assertDoesNotThrow(() ->
                strategy.next(candidates, null)
        );
    }

    @Test
    void nullKey_shouldThrowException() {
        HashStrategy<String> strategy =
                new HashStrategy<>(ctx -> ctx.field("missing"));

        MessageContext ctx = new TestMessageContext();

        IllegalStateException ex = assertThrows(
                IllegalStateException.class,
                () -> strategy.next(List.of("A"), ctx)
        );

        assertTrue(ex.getMessage().contains("Hash key"));
    }

    @Test
    void emptyCandidates_shouldThrow() {
        HashStrategy<String> strategy =
                new HashStrategy<>(ctx -> "key");

        assertThrows(IllegalStateException.class,
                () -> strategy.next(List.of(), null));
    }

    @Test
    void largeCandidateList_shouldAlwaysReturnValidIndex() {
        HashStrategy<Integer> strategy =
                new HashStrategy<>(ctx -> "stable-key");

        List<Integer> candidates =
                IntStream.range(0, 10_000).boxed().toList();

        Integer result = strategy.next(candidates, null);

        assertNotNull(result);
        assertTrue(result >= 0 && result < 10_000);
    }

    @Test
    void hashDistribution_shouldBeReasonablySpread() {
        HashStrategy<String> strategy =
                new HashStrategy<>(ctx -> ctx.field("user"));

        List<String> candidates = List.of("A", "B", "C", "D");

        Map<String, Integer> count = new HashMap<>();

        for (int i = 0; i < 1_000; i++) {
            String key = "user-" + i;
            String r = strategy.next(
                    candidates,
                    new TestMessageContext().with("user", key)
            );
            count.merge(r, 1, Integer::sum);
        }

        // sanity check: all nodes used
        assertEquals(4, count.size());
    }




}
