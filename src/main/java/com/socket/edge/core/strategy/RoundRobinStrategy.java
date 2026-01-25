package com.socket.edge.core.strategy;

import com.socket.edge.core.MessageContext;
import com.socket.edge.model.VersionedCandidates;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

public final class RoundRobinStrategy<T extends WeightedCandidate>
        implements SelectionStrategy<T> {

    private volatile long loadedVersion = -1;
    private volatile Object[] cycle;
    private AtomicInteger idx = new AtomicInteger(0);

    @Override
    public T next(VersionedCandidates<T> vc, MessageContext messageContext) {
        validate(vc.candidates());

        long v = vc.version();

        // VERY CHEAP check
        if (v != loadedVersion) {
            rebuild(vc);
        }

        Object[] c = cycle;
        int i = Math.floorMod(idx.getAndIncrement(), c.length);
        return (T) c[i];
    }

    private synchronized void rebuild(VersionedCandidates vc) {
        if (vc.version() != loadedVersion) {
            cycle = buildPriorityCycle(vc.candidates());
            loadedVersion = vc.version();
            idx.set(0);
        }
    }

    private Object[] buildPriorityCycle(List<T> candidates) {
        // cari priority tertinggi
        int highestPriority = Integer.MIN_VALUE;
        for (T c : candidates) {
            highestPriority = Math.max(highestPriority, c.getPriority());
        }

        // ambil kandidat dengan priority tertinggi saja
        List<T> active = new ArrayList<>();
        for (T c : candidates) {
            if (c.getPriority() == highestPriority) {
                active.add(c);
            }
        }

        if (active.isEmpty()) {
            throw new IllegalStateException("No active candidates");
        }

        // build EXACT cycle
        return buildRoutingTable(active);
    }

    private Object[] buildRoutingTable(List<T> candidates) {
        class Node {
            final T c;
            int remain;
            boolean seen = false;

            Node(T c) {
                this.c = c;
                this.remain = Math.max(1, c.getWeight());
            }
        }

        List<Node> nodes = new ArrayList<>();
        int total = 0;
        for (T c : candidates) {
            Node n = new Node(c);
            nodes.add(n);
            total += n.remain;
        }

        Object[] cycle = new Object[total];
        Node last = null;

        for (int i = 0; i < total; i++) {

            Node pick = null;

            // PRIORITAS: yang belum pernah muncul
            for (Node n : nodes) {
                if (n.remain > 0 && !n.seen) {
                    pick = n;
                    break;
                }
            }

            //  Kalau semua sudah muncul → ambil remain terbesar,
            //     tapi beda dari last kalau bisa
            if (pick == null) {
                for (Node n : nodes) {
                    if (n.remain <= 0) continue;
                    if (last != null && n == last) continue;

                    if (pick == null || n.remain > pick.remain) {
                        pick = n;
                    }
                }
            }

            // Kalau cuma satu tersisa
            if (pick == null) {
                for (Node n : nodes) {
                    if (n.remain > 0) {
                        pick = n;
                        break;
                    }
                }
            }

            cycle[i] = pick.c;
            pick.remain--;
            pick.seen = true;
            last = pick;
        }

        return cycle;
    }


    public static void main(String[] args) {
        RoundRobinStrategy roundRobinStrategy = new RoundRobinStrategy();
        List<WeightedCandidate> candidates = new ArrayList<>();
        candidates.add(new WeightedCandidate() {

            @Override
            public int getWeight() {
                return 5;
            }

            @Override
            public int getPriority() {
                return 1;
            }

            @Override
            public String toString() {
                return "candidate-1";
            }
        });
        candidates.add(new WeightedCandidate() {

            @Override
            public int getWeight() {
                return 3;
            }

            @Override
            public int getPriority() {
                return 1;
            }
            @Override
            public String toString() {
                return "candidate-2";
            }
        });
        candidates.add(new WeightedCandidate() {

            @Override
            public int getWeight() {
                return 1;
            }

            @Override
            public int getPriority() {
                return 1;
            }
            @Override
            public String toString() {
                return "candidate-3";
            }
        });

        VersionedCandidates vc = new VersionedCandidates(1, candidates);
        for (int i=0; i<100; i++) {
            System.out.println(i + " - " +roundRobinStrategy.next(vc, null));
        }
    }
}
