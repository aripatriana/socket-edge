package com.socket.edge.core.transport;

import com.socket.edge.core.MessageContext;
import com.socket.edge.core.socket.NettyClientSocket;
import com.socket.edge.core.socket.SocketChannel;
import com.socket.edge.core.socket.SocketChannelPool;
import com.socket.edge.core.strategy.SelectionStrategy;
import com.socket.edge.constant.SocketState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ClientTransportTest {

    private SelectionStrategy<SocketChannel> strategy;
    private NettyClientSocket clientSocket;
    private SocketChannelPool channelPool;
    private SocketChannel channel;
    private MessageContext ctx;

    @BeforeEach
    void setUp() {
        strategy = mock(SelectionStrategy.class);
        clientSocket = mock(NettyClientSocket.class);
        channelPool = mock(SocketChannelPool.class);
        channel = mock(SocketChannel.class);
        ctx = mock(MessageContext.class);
    }

    @Test
    void send_shouldSendMessage_whenActiveChannelExists() {
        byte[] payload = new byte[]{0x01, 0x02};

        when(clientSocket.channelPool()).thenReturn(channelPool);
        when(channelPool.activeChannels()).thenReturn(List.of(channel));
        when(channel.isActive()).thenReturn(true);
        when(strategy.next(anyList(), eq(ctx))).thenReturn(channel);
        when(ctx.getRawBytes()).thenReturn(payload);

        ClientTransport transport =
                new ClientTransport(List.of(clientSocket), strategy);

        transport.send(ctx);

        verify(strategy).next(anyList(), eq(ctx));
        verify(channel).increment();
        verify(channel).send(payload);
        verify(ctx).addProperty("back_forward_channel", channel);
    }

    @Test
    void send_shouldThrowException_whenNoActiveChannels() {
        when(clientSocket.channelPool()).thenReturn(channelPool);
        when(channelPool.activeChannels()).thenReturn(List.of());

        ClientTransport transport =
                new ClientTransport(List.of(clientSocket), strategy);

        IllegalStateException ex = assertThrows(
                IllegalStateException.class,
                () -> transport.send(ctx)
        );

        assertEquals("No active client socket", ex.getMessage());
        verifyNoInteractions(strategy);
    }

    @Test
    void send_shouldIgnoreNullChannelPool() {
        when(clientSocket.channelPool()).thenReturn(null);

        ClientTransport transport =
                new ClientTransport(List.of(clientSocket), strategy);

        assertThrows(
                IllegalStateException.class,
                () -> transport.send(ctx)
        );
    }

    @Test
    void send_shouldIgnoreInactiveChannels() {
        when(clientSocket.channelPool()).thenReturn(channelPool);
        when(channelPool.activeChannels()).thenReturn(List.of(channel));
        when(channel.isActive()).thenReturn(false);

        ClientTransport transport =
                new ClientTransport(List.of(clientSocket), strategy);

        assertThrows(
                IllegalStateException.class,
                () -> transport.send(ctx)
        );

        verifyNoInteractions(strategy);
    }

    @Test
    void send_shouldPassOnlyActiveChannelsToStrategy() {
        SocketChannel active = mock(SocketChannel.class);
        SocketChannel inactive = mock(SocketChannel.class);

        when(active.isActive()).thenReturn(true);
        when(inactive.isActive()).thenReturn(false);

        when(clientSocket.channelPool()).thenReturn(channelPool);
        when(channelPool.activeChannels()).thenReturn(List.of(active, inactive));
        when(strategy.next(anyList(), eq(ctx))).thenReturn(active);
        when(ctx.getRawBytes()).thenReturn(new byte[]{0x01});

        ClientTransport transport =
                new ClientTransport(List.of(clientSocket), strategy);

        transport.send(ctx);

        ArgumentCaptor<List<SocketChannel>> captor =
                ArgumentCaptor.forClass(List.class);

        verify(strategy).next(captor.capture(), eq(ctx));

        List<SocketChannel> candidates = captor.getValue();
        assertEquals(1, candidates.size());
        assertTrue(candidates.contains(active));
    }

    @Test
    void isUp_shouldReturnTrue_whenAnySocketIsUp() {
        when(clientSocket.getState()).thenReturn(SocketState.UP);

        ClientTransport transport =
                new ClientTransport(List.of(clientSocket), strategy);

        assertTrue(transport.isUp());
    }

    @Test
    void isUp_shouldReturnFalse_whenAllSocketsAreDown() {
        when(clientSocket.getState()).thenReturn(SocketState.DOWN);

        ClientTransport transport =
                new ClientTransport(List.of(clientSocket), strategy);

        assertFalse(transport.isUp());
    }
}
