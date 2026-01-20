package com.socket.edge.core.cache;

import com.socket.edge.model.ReplyInbound;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class CacheCorrelationStoreTest {

    private CacheCorrelationStore store;

    @AfterEach
    void tearDown() {
        if (store != null) {
            store.shutdown();
        }
    }

    @Test
    void putAndGet_shouldReturnValueBeforeTtl() {
        store = new CacheCorrelationStore(500);
        ReplyInbound inbound = mock(ReplyInbound.class);

        store.put("key1", inbound);

        ReplyInbound result = store.get("key1");
        assertNotNull(result);
        assertSame(inbound, result);
    }

    @Test
    void get_shouldReturnNullAfterTtlExpired() throws Exception {
        store = new CacheCorrelationStore(100);
        ReplyInbound inbound = mock(ReplyInbound.class);

        store.put("key1", inbound);

        Thread.sleep(150);

        ReplyInbound result = store.get("key1");
        assertNull(result);
    }

    @Test
    void expiredEntry_shouldBeRemovedOnGet() throws Exception {
        store = new CacheCorrelationStore(100);
        ReplyInbound inbound = mock(ReplyInbound.class);

        store.put("key1", inbound);

        Thread.sleep(150);

        assertNull(store.get("key1"));
        assertNull(store.get("key1")); // ensure already removed
    }

    @Test
    void remove_shouldDeleteEntryImmediately() {
        store = new CacheCorrelationStore(1_000);
        ReplyInbound inbound = mock(ReplyInbound.class);

        store.put("key1", inbound);
        store.remove("key1");

        assertNull(store.get("key1"));
    }

    @Test
    void cleanupTask_shouldRemoveExpiredEntries() throws Exception {
        store = new CacheCorrelationStore(100);
        ReplyInbound inbound = mock(ReplyInbound.class);

        store.put("key1", inbound);
        store.put("key2", inbound);

        Thread.sleep(300); // wait cleanup interval

        assertNull(store.get("key1"));
        assertNull(store.get("key2"));
    }

    @Test
    void get_shouldNotRemoveNewEntry_whenRaceConditionHappens() throws Exception {
        store = new CacheCorrelationStore(100);
        ReplyInbound oldInbound = mock(ReplyInbound.class);
        ReplyInbound newInbound = mock(ReplyInbound.class);

        store.put("key1", oldInbound);

        // Let old entry expire
        Thread.sleep(120);

        // Simulate concurrent put before get removes
        store.put("key1", newInbound);

        ReplyInbound result = store.get("key1");

        assertNotNull(result);
        assertSame(newInbound, result);
    }

    @Test
    void concurrentAccess_shouldNotThrowOrCorrupt() throws Exception {
        store = new CacheCorrelationStore(200);
        ExecutorService executor = Executors.newFixedThreadPool(8);
        AtomicInteger success = new AtomicInteger();

        for (int i = 0; i < 100; i++) {
            int idx = i;
            executor.submit(() -> {
                ReplyInbound inbound = mock(ReplyInbound.class);
                store.put("key-" + idx, inbound);
                ReplyInbound r = store.get("key-" + idx);
                if (r != null) {
                    success.incrementAndGet();
                }
            });
        }

        executor.shutdown();
        assertTrue(executor.awaitTermination(2, TimeUnit.SECONDS));

        assertTrue(success.get() > 0);
    }
}