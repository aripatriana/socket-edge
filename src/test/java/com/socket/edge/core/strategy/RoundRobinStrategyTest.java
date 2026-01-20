package com.socket.edge.core.strategy;

import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

public class RoundRobinStrategyTest {

    @Test
    void roundRobin_shouldRespectWeight() {
        SelectionStrategy<TestCandidate> strategy =
                new RoundRobinStrategy<>();

        TestCandidate a = new TestCandidate("A", 3, 1, 0);
        TestCandidate b = new TestCandidate("B", 1, 1, 0);

        List<TestCandidate> list = List.of(a, b);

        int countA = 0;
        int countB = 0;

        for (int i = 0; i < 40; i++) {
            TestCandidate c = strategy.next(list, null);
            if (c.toString().equals("A")) countA++;
            else countB++;
        }

        assertTrue(countA > countB);
    }

    @Test
    void emptyCandidates_shouldThrow() {
        SelectionStrategy<WeightedCandidate> strategy =
                SelectionFactory.roundRobin();

        assertThrows(IllegalStateException.class,
                () -> strategy.next(List.of(), null));
    }

    @Test
    void higherPriority_shouldAlwaysWin() {
        SelectionStrategy<TestCandidate> strategy =
                new RoundRobinStrategy<>();

        TestCandidate high = new TestCandidate("HIGH", 1, 2, 0);
        TestCandidate low  = new TestCandidate("LOW", 10, 1, 0);

        List<TestCandidate> list = List.of(high, low);

        for (int i = 0; i < 20; i++) {
            TestCandidate result = strategy.next(list, null);
            assertEquals("HIGH", result.toString());
        }
    }

    @Test
    void roundRobin_shouldRespectWeightRatio() {
        SelectionStrategy<TestCandidate> strategy =
                new RoundRobinStrategy<>();

        TestCandidate a = new TestCandidate("A", 3, 1, 0);
        TestCandidate b = new TestCandidate("B", 1, 1, 0);

        List<TestCandidate> list = List.of(a, b);

        Map<String, Integer> count = new HashMap<>();

        int cycles = 40;

        for (int i = 0; i < cycles; i++) {
            String id = strategy.next(list, null).toString();
            count.merge(id, 1, Integer::sum);
        }

        assertEquals(40, count.get("A") + count.get("B"));
        assertTrue(count.get("A") >= 28);   // expected ~30
        assertTrue(count.get("B") <= 12);
    }

    @Test
    void roundRobin_orderShouldBeDeterministic() {
        SelectionStrategy<TestCandidate> strategy =
                new RoundRobinStrategy<>();

        TestCandidate a = new TestCandidate("A", 2, 1, 0);
        TestCandidate b = new TestCandidate("B", 1, 1, 0);

        List<TestCandidate> list = List.of(a, b);

        List<String> firstCycle = new ArrayList<>();
        List<String> secondCycle = new ArrayList<>();

        for (int i = 0; i < 3; i++) {
            firstCycle.add(strategy.next(list, null).toString());
        }

        for (int i = 0; i < 3; i++) {
            secondCycle.add(strategy.next(list, null).toString());
        }

        assertEquals(firstCycle, secondCycle);
    }

    @Test
    void zeroOrNegativeWeight_shouldBeTreatedAsOne() {
        SelectionStrategy<TestCandidate> strategy =
                new RoundRobinStrategy<>();

        TestCandidate a = new TestCandidate("A", 0, 1, 0);
        TestCandidate b = new TestCandidate("B", -5, 1, 0);

        List<TestCandidate> list = List.of(a, b);

        Set<String> seen = new HashSet<>();

        for (int i = 0; i < 10; i++) {
            seen.add(strategy.next(list, null).toString());
        }

        assertEquals(Set.of("A", "B"), seen);
    }

    @Test
    void singleCandidate_shouldAlwaysReturnThatCandidate() {
        SelectionStrategy<TestCandidate> strategy =
                new RoundRobinStrategy<>();

        TestCandidate a = new TestCandidate("A", 10, 99, 0);

        for (int i = 0; i < 10; i++) {
            assertSame(a, strategy.next(List.of(a), null));
        }
    }

    @Test
    void priorityChange_shouldAffectSelection() {
        SelectionStrategy<TestCandidate> strategy =
                new RoundRobinStrategy<>();

        TestCandidate a = new TestCandidate("A", 1, 1, 0);
        TestCandidate b = new TestCandidate("B", 1, 2, 0);

        List<TestCandidate> list = List.of(a, b);

        assertEquals("B",
                strategy.next(list, null).toString());
    }

}
