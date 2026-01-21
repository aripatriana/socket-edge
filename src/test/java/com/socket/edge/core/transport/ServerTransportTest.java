package com.socket.edge.core.transport;

import com.socket.edge.core.MessageContext;
import com.socket.edge.core.socket.NettyServerSocket;
import com.socket.edge.core.socket.SocketChannel;
import com.socket.edge.core.socket.SocketChannelPool;
import com.socket.edge.core.strategy.SelectionStrategy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ServerTransportTest {

    private NettyServerSocket serverSocket;
    private SocketChannelPool channelPool;
    private SelectionStrategy<SocketChannel> strategy;
    private SocketChannel channel;
    private MessageContext ctx;

    @BeforeEach
    void setUp() {
        serverSocket = mock(NettyServerSocket.class);
        channelPool = mock(SocketChannelPool.class);
        strategy = mock(SelectionStrategy.class);
        channel = mock(SocketChannel.class);
        ctx = mock(MessageContext.class);

        when(serverSocket.channelPool()).thenReturn(channelPool);
    }

    @Test
    void send_shouldSendMessage_whenActiveChannelExists() {
        byte[] payload = new byte[]{0x10, 0x20};

        when(channelPool.activeChannels()).thenReturn(List.of(channel));
        when(strategy.next(anyList(), eq(ctx))).thenReturn(channel);
        when(ctx.getRawBytes()).thenReturn(payload);

        ServerTransport transport =
                new ServerTransport(serverSocket, strategy);

        transport.send(ctx);

        verify(strategy).next(anyList(), eq(ctx));
        verify(channel).increment();
        verify(channel).send(payload);
        verify(ctx).addProperty("back_forward_channel", channel);
    }

    @Test
    void send_shouldThrowException_whenNoActiveChannels() {
        when(channelPool.activeChannels()).thenReturn(List.of());

        ServerTransport transport =
                new ServerTransport(serverSocket, strategy);

        IllegalStateException ex = assertThrows(
                IllegalStateException.class,
                () -> transport.send(ctx)
        );

        assertEquals("No active socket channel", ex.getMessage());
        verifyNoInteractions(strategy);
    }

    @Test
    void send_shouldPassAllActiveChannelsToStrategy() {
        SocketChannel ch1 = mock(SocketChannel.class);
        SocketChannel ch2 = mock(SocketChannel.class);

        when(channelPool.activeChannels()).thenReturn(List.of(ch1, ch2));
        when(strategy.next(anyList(), eq(ctx))).thenReturn(ch1);
        when(ctx.getRawBytes()).thenReturn(new byte[]{0x01});

        ServerTransport transport =
                new ServerTransport(serverSocket, strategy);

        transport.send(ctx);

        ArgumentCaptor<List<SocketChannel>> captor =
                ArgumentCaptor.forClass(List.class);

        verify(strategy).next(captor.capture(), eq(ctx));

        List<SocketChannel> candidates = captor.getValue();
        assertEquals(2, candidates.size());
        assertTrue(candidates.contains(ch1));
        assertTrue(candidates.contains(ch2));
    }

    @Test
    void send_shouldHandleEmptyPayload() {
        when(channelPool.activeChannels()).thenReturn(List.of(channel));
        when(strategy.next(anyList(), eq(ctx))).thenReturn(channel);
        when(ctx.getRawBytes()).thenReturn(new byte[0]);

        ServerTransport transport =
                new ServerTransport(serverSocket, strategy);

        transport.send(ctx);

        verify(channel).send(new byte[0]);
    }

    @Test
    void isUp_shouldReturnTrue_whenActiveChannelExists() {
        when(channelPool.activeChannels()).thenReturn(List.of(channel));

        ServerTransport transport =
                new ServerTransport(serverSocket, strategy);

        assertTrue(transport.isUp());
    }

    @Test
    void isUp_shouldReturnFalse_whenNoActiveChannels() {
        when(channelPool.activeChannels()).thenReturn(List.of());

        ServerTransport transport =
                new ServerTransport(serverSocket, strategy);

        assertFalse(transport.isUp());
    }

    @Test
    void shutdown_shouldBeNoOp() {
        ServerTransport transport =
                new ServerTransport(serverSocket, strategy);

        assertDoesNotThrow(transport::shutdown);
    }
}
