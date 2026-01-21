package com.socket.edge.core.transport;

import com.socket.edge.constant.SocketType;
import com.socket.edge.model.ChannelCfg;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TransportProviderTest {

    private TransportProvider provider;
    private Transport transport;

    @BeforeEach
    void setUp() {
        provider = new TransportProvider();
        transport = mock(Transport.class);
    }

    @Test
    void registerIfAbsent_shouldRegister_whenKeyIsNew() {
        boolean result = provider.registerIfAbsent("CLIENT|A", transport);

        assertTrue(result);
        assertSame(transport, provider.get("CLIENT|A"));
    }

    @Test
    void registerIfAbsent_shouldNotOverwrite_whenKeyExists() {
        Transport another = mock(Transport.class);

        provider.registerIfAbsent("CLIENT|A", transport);
        boolean result = provider.registerIfAbsent("CLIENT|A", another);

        assertFalse(result);
        assertSame(transport, provider.get("CLIENT|A"));
    }

    @Test
    void register_shouldOverwriteExistingTransport() {
        Transport another = mock(Transport.class);

        provider.register("CLIENT|A", transport);
        provider.register("CLIENT|A", another);

        assertSame(another, provider.get("CLIENT|A"));
    }

    @Test
    void registerIfAbsent_shouldThrowNpe_whenKeyIsNull() {
        assertThrows(
                NullPointerException.class,
                () -> provider.registerIfAbsent(null, transport)
        );
    }

    @Test
    void registerIfAbsent_shouldThrowNpe_whenTransportIsNull() {
        assertThrows(
                NullPointerException.class,
                () -> provider.registerIfAbsent("CLIENT|A", null)
        );
    }

    @Test
    void get_shouldReturnNull_whenKeyDoesNotExist() {
        assertNull(provider.get("UNKNOWN"));
    }

    @Test
    void contains_shouldReturnTrue_whenKeyExists() {
        provider.register("CLIENT|A", transport);

        assertTrue(provider.contains("CLIENT|A"));
        assertFalse(provider.contains("CLIENT|B"));
    }

    @Test
    void unregister_shouldRemoveTransport_andCallShutdown() {
        provider.register("CLIENT|A", transport);

        Transport removed = provider.unregister("CLIENT|A");

        assertSame(transport, removed);
        assertFalse(provider.contains("CLIENT|A"));
        verify(transport).shutdown();
    }

    @Test
    void unregister_shouldReturnNull_whenKeyNotExists() {
        Transport removed = provider.unregister("UNKNOWN");

        assertNull(removed);
        verifyNoInteractions(transport);
    }

    @Test
    void destroy_shouldShutdownAllTransports_andClearMap() {
        Transport t1 = mock(Transport.class);
        Transport t2 = mock(Transport.class);

        provider.register("CLIENT|A", t1);
        provider.register("SERVER|B", t2);

        provider.destroy();

        verify(t1).shutdown();
        verify(t2).shutdown();
        assertFalse(provider.contains("CLIENT|A"));
        assertFalse(provider.contains("SERVER|B"));
    }

    @Test
    void resolve_shouldReturnTransport_whenKeyExists() {
        ChannelCfg cfg = mock(ChannelCfg.class);
        when(cfg.name()).thenReturn("CHANNEL1");

        provider.register("CLIENT|CHANNEL1", transport);

        Transport resolved =
                provider.resolve(cfg, SocketType.CLIENT);

        assertSame(transport, resolved);
    }

    @Test
    void resolve_shouldThrowNpe_whenTransportNotFound() {
        ChannelCfg cfg = mock(ChannelCfg.class);
        when(cfg.name()).thenReturn("CHANNEL1");

        assertThrows(
                NullPointerException.class,
                () -> provider.resolve(cfg, SocketType.CLIENT)
        );
    }

    @Test
    void resolve_shouldThrowNpe_whenOutboundTypeIsNull() {
        ChannelCfg cfg = mock(ChannelCfg.class);

        assertThrows(
                NullPointerException.class,
                () -> provider.resolve(cfg, null)
        );
    }

    @Test
    void registerIfAbsent_shouldBeAtomic_underConcurrency() throws Exception {
        TransportProvider provider = new TransportProvider();
        Transport transport = mock(Transport.class);

        int threads = 20;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);

        AtomicInteger success = new AtomicInteger();

        for (int i = 0; i < threads; i++) {
            executor.submit(() -> {
                try {
                    start.await();
                    if (provider.registerIfAbsent("CLIENT|CH1", transport)) {
                        success.incrementAndGet();
                    }
                } catch (InterruptedException ignored) {
                } finally {
                    done.countDown();
                }
            });
        }

        start.countDown();
        done.await();
        executor.shutdown();

        assertEquals(1, success.get(),
                "Only one thread should succeed registering");

        assertSame(transport, provider.get("CLIENT|CH1"));
    }

    @Test
    void destroy_shouldBeSafe_whenCalledConcurrently() throws Exception {
        TransportProvider provider = new TransportProvider();

        Transport t1 = mock(Transport.class);
        Transport t2 = mock(Transport.class);

        provider.register("CLIENT|A", t1);
        provider.register("SERVER|B", t2);

        ExecutorService executor = Executors.newFixedThreadPool(4);

        Callable<Void> destroyTask = () -> {
            provider.destroy();
            return null;
        };

        assertDoesNotThrow(() ->
                executor.invokeAll(
                        List.of(destroyTask, destroyTask, destroyTask)
                )
        );

        executor.shutdown();

        verify(t1, atLeastOnce()).shutdown();
        verify(t2, atLeastOnce()).shutdown();
    }
}
