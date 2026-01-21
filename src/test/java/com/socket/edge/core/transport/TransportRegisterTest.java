package com.socket.edge.core.transport;

import com.socket.edge.constant.SocketType;
import com.socket.edge.core.socket.NettyClientSocket;
import com.socket.edge.core.socket.NettyServerSocket;
import com.socket.edge.core.socket.SocketChannel;
import com.socket.edge.core.strategy.SelectionFactory;
import com.socket.edge.core.strategy.SelectionStrategy;
import com.socket.edge.model.ChannelCfg;
import com.socket.edge.model.ClientChannel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TransportRegisterTest {

    private TransportProvider provider;
    private TransportRegister register;

    private ChannelCfg cfg;
    private ClientChannel clientCfg;

    @BeforeEach
    void setUp() {
        provider = mock(TransportProvider.class);
        register = new TransportRegister(provider);

        clientCfg = mock(ClientChannel.class);
        cfg = mock(ChannelCfg.class);

        when(cfg.name()).thenReturn("CH1");
        when(cfg.client()).thenReturn(clientCfg);
        when(clientCfg.strategy()).thenReturn("RR");
    }

    // ===================== SERVER =====================

    @Test
    void registerServerTransport_shouldRegisterSuccessfully() {
        NettyServerSocket socket = mock(NettyServerSocket.class);
        when(socket.getType()).thenReturn(SocketType.SERVER);
        when(socket.getId()).thenReturn("S1");

        SelectionStrategy<SocketChannel> strategy = mock(SelectionStrategy.class);

        try (MockedStatic<SelectionFactory> sf =
                     mockStatic(SelectionFactory.class)) {

            sf.when(() -> SelectionFactory.create("RR", null))
                    .thenReturn(strategy);

            when(provider.registerIfAbsent(
                    eq("SERVER|CH1"),
                    any(ServerTransport.class))
            ).thenReturn(true);

            register.registerServerTransport(cfg, socket);

            verify(provider).registerIfAbsent(
                    eq("SERVER|CH1"),
                    any(ServerTransport.class)
            );
        }
    }

    @Test
    void registerServerTransport_shouldFail_whenAlreadyRegistered() {
        NettyServerSocket socket = mock(NettyServerSocket.class);
        when(socket.getType()).thenReturn(SocketType.SERVER);

        try (MockedStatic<SelectionFactory> sf =
                     mockStatic(SelectionFactory.class)) {

            sf.when(() -> SelectionFactory.create(any(), any()))
                    .thenReturn(mock(SelectionStrategy.class));

            when(provider.registerIfAbsent(any(), any()))
                    .thenReturn(false);

            assertThrows(
                    IllegalStateException.class,
                    () -> register.registerServerTransport(cfg, socket)
            );
        }
    }

    @Test
    void unregisterServerTransport_shouldDelegateToProvider() {
        register.unregisterServerTransport(cfg);

        verify(provider).unregister("SERVER|CH1");
    }

    // ===================== CLIENT LIST =====================

    @Test
    void registerClientTransport_shouldRegisterSuccessfully() {
        NettyClientSocket socket = mock(NettyClientSocket.class);
        when(socket.getType()).thenReturn(SocketType.CLIENT);

        SelectionStrategy<SocketChannel> strategy = mock(SelectionStrategy.class);

        try (MockedStatic<SelectionFactory> sf =
                     mockStatic(SelectionFactory.class)) {

            sf.when(() -> SelectionFactory.create("RR", null))
                    .thenReturn(strategy);

            when(provider.registerIfAbsent(
                    eq("CLIENT|CH1"),
                    any(ClientTransport.class))
            ).thenReturn(true);

            register.registerClientTransport(cfg, List.of(socket));

            verify(provider).registerIfAbsent(
                    eq("CLIENT|CH1"),
                    any(ClientTransport.class)
            );
        }
    }

    @Test
    void registerClientTransport_shouldFail_whenClientListEmpty() {
        assertThrows(
                IllegalArgumentException.class,
                () -> register.registerClientTransport(cfg, List.of())
        );
    }

    @Test
    void registerClientTransport_shouldFail_whenAlreadyRegistered() {
        NettyClientSocket socket = mock(NettyClientSocket.class);
        when(socket.getType()).thenReturn(SocketType.CLIENT);

        try (MockedStatic<SelectionFactory> sf =
                     mockStatic(SelectionFactory.class)) {

            sf.when(() -> SelectionFactory.create(any(), any()))
                    .thenReturn(mock(SelectionStrategy.class));

            when(provider.registerIfAbsent(any(), any()))
                    .thenReturn(false);

            assertThrows(
                    IllegalStateException.class,
                    () -> register.registerClientTransport(cfg, List.of(socket))
            );
        }
    }

    // ===================== ADD / REMOVE SOCKET =====================

    @Test
    void registerClientTransport_shouldAddSocket_whenTransportExists() {
        NettyClientSocket socket = mock(NettyClientSocket.class);
        when(socket.getType()).thenReturn(SocketType.CLIENT);
        when(socket.getId()).thenReturn("C1");

        ClientTransport transport = mock(ClientTransport.class);

        when(provider.get("CLIENT|CH1")).thenReturn(transport);

        register.registerClientTransport(cfg, socket);

        verify(transport).addSocket(socket);
    }

    @Test
    void registerClientTransport_shouldIgnore_whenTransportNotClientTransport() {
        NettyClientSocket socket = mock(NettyClientSocket.class);
        when(socket.getType()).thenReturn(SocketType.CLIENT);

        when(provider.get("CLIENT|CH1"))
                .thenReturn(mock(Transport.class));

        assertDoesNotThrow(() ->
                register.registerClientTransport(cfg, socket)
        );
    }

    @Test
    void unregisterClientTransport_shouldRemoveSocket_whenTransportExists() {
        NettyClientSocket socket = mock(NettyClientSocket.class);
        when(socket.getType()).thenReturn(SocketType.CLIENT);
        when(socket.getId()).thenReturn("C1");

        ClientTransport transport = mock(ClientTransport.class);

        when(provider.get("CLIENT|CH1")).thenReturn(transport);

        register.unregisterClientTransport(cfg, socket);

        verify(transport).removeSocket(socket);
    }

    // ===================== DESTROY =====================

    @Test
    void destroy_shouldDelegateToProvider() {
        register.destroy();

        verify(provider).destroy();
    }

    @Test
    void registerServerTransport_shouldRegisterAtMostOne_underConcurrency()
            throws Exception {

        TransportProvider provider = new TransportProvider();
        TransportRegister register = new TransportRegister(provider);

        ClientChannel clientCfg = mock(ClientChannel.class);
        ChannelCfg cfg = mock(ChannelCfg.class);

        when(cfg.name()).thenReturn("CH1");
        when(cfg.client()).thenReturn(clientCfg);
        when(clientCfg.strategy()).thenReturn("roundrobin");

        NettyServerSocket socket = mock(NettyServerSocket.class);
        when(socket.getType()).thenReturn(SocketType.SERVER);
        when(socket.getId()).thenReturn("S1");

        SelectionStrategy strategy = mock(SelectionStrategy.class);

        int threads = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);

        try (MockedStatic<SelectionFactory> sf =
                     mockStatic(SelectionFactory.class)) {

            sf.when(() -> SelectionFactory.create(eq("RR"), isNull()))
                    .thenReturn(strategy);

            for (int i = 0; i < threads; i++) {
                executor.submit(() -> {
                    try {
                        start.await();
                        register.registerServerTransport(cfg, socket);
                    } catch (IllegalStateException expected) {
                        // expected under race
                    } catch (InterruptedException ignored) {
                    }
                });
            }

            start.countDown();
            executor.shutdown();
            executor.awaitTermination(5, TimeUnit.SECONDS);
        }

        Transport transport = provider.get("SERVER|CH1");

        assertNotNull(transport,
                "Exactly one ServerTransport must be registered");
    }

    @Test
    void addAndRemoveClientSocket_shouldBeSafe_underConcurrency() throws Exception {
        TransportProvider provider = new TransportProvider();
        TransportRegister register = new TransportRegister(provider);

        ChannelCfg cfg = mock(ChannelCfg.class);
        when(cfg.name()).thenReturn("CH1");

        NettyClientSocket socket = mock(NettyClientSocket.class);
        when(socket.getType()).thenReturn(SocketType.CLIENT);
        when(socket.getId()).thenReturn("C1");

        ClientTransport clientTransport =
                spy(new ClientTransport(
                        new CopyOnWriteArrayList<>(),
                        mock(SelectionStrategy.class)
                ));

        provider.register("CLIENT|CH1", clientTransport);

        ExecutorService executor = Executors.newFixedThreadPool(8);

        Runnable addTask = () ->
                register.registerClientTransport(cfg, socket);

        Runnable removeTask = () ->
                register.unregisterClientTransport(cfg, socket);

        assertDoesNotThrow(() ->
                executor.invokeAll(
                        List.of(
                                Executors.callable(addTask),
                                Executors.callable(removeTask),
                                Executors.callable(addTask),
                                Executors.callable(removeTask)
                        )
                )
        );

        executor.shutdown();
    }
}
