package com.socket.edge.core.socket;

import com.socket.edge.model.SocketEndpoint;
import io.netty.channel.Channel;
import io.netty.channel.ChannelId;
import org.junit.jupiter.api.*;

import java.net.InetSocketAddress;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SocketChannelPoolTest {

    private SocketEndpoint endpoint;
    private SocketChannelPooling pool;

    @BeforeEach
    void setup() {
        endpoint = new SocketEndpoint("127.0.0.1", 9000, 1, 1);
        AbstractSocket socket = mock(AbstractSocket.class);
        when(socket.resolveEndpoint(endpoint.id())).thenReturn(endpoint);
        when(socket.resolveEndpoint(endpoint.id().host(), endpoint.id().port())).thenReturn(endpoint);

        pool = new SocketChannelPooling(
                socket
        );
    }

    private Channel mockChannel(String ip, int port, ChannelId id) {
        Channel ch = mock(Channel.class);

        when(id.asLongText()).thenReturn(UUID.randomUUID().toString());
        when(ch.id()).thenReturn(id);
        when(ch.remoteAddress()).thenReturn(new InetSocketAddress(ip, port));
        when(ch.isActive()).thenReturn(true);

        return ch;
    }

    private Channel mockChannel(String ip, int port) {
        Channel ch = mock(Channel.class);
        ChannelId id = mock(ChannelId.class);

        when(id.asLongText()).thenReturn(UUID.randomUUID().toString());
        when(ch.id()).thenReturn(id);
        when(ch.remoteAddress()).thenReturn(new InetSocketAddress(ip, port));
        when(ch.isActive()).thenReturn(true);

        return ch;
    }

    /* ===================== addChannel ===================== */

    @Test
    void addChannel_success_whenAllowed() {
        Channel ch = mockChannel("127.0.0.1", 9000);

        boolean result = pool.addChannel(ch);

        assertTrue(result);
        assertNotNull(pool.getChannel(ch));
        assertEquals(1, pool.getAllChannel().size());
    }

    @Test
    void addChannel_reject_whenIpNotAllowed() {
        Channel ch = mockChannel("10.10.10.10", 9000);

        boolean result = pool.addChannel(ch);

        assertFalse(result);
        verify(ch).close();
        assertTrue(pool.getAllChannel().isEmpty());
    }

    @Test
    void addChannel_reject_whenPortMismatch_client() {
        Channel ch = mockChannel("127.0.0.1", 9999);

        boolean result = pool.addChannel(ch);

        assertFalse(result);
        verify(ch).close();
    }

    @Test
    void addChannel_reject_duplicateChannelId() {
        ChannelId sharedId = mock(ChannelId.class);

        Channel ch1 = mockChannel("127.0.0.1", 9000, sharedId);
        Channel ch2 = mockChannel("127.0.0.1", 9000, sharedId);

        assertTrue(pool.addChannel(ch1));
        assertFalse(pool.addChannel(ch2));
    }

    /* ===================== get / getAll ===================== */

    @Test
    void get_returnsNull_whenChannelNotRegistered() {
        Channel ch = mockChannel("127.0.0.1", 9000);
        assertNull(pool.getChannel(ch));
    }

    @Test
    void getAllByEndpoint_returnsChannels() {
        Channel ch = mockChannel("127.0.0.1", 9000);
        pool.addChannel(ch);

        Set<SocketChannel> set = pool.getAllByEndpoint(endpoint);

        assertEquals(1, set.size());
    }

    /* ===================== remove ===================== */

    @Test
    void removeChannel_success() {
        Channel ch = mockChannel("127.0.0.1", 9000);
        pool.addChannel(ch);

        pool.removeChannel(ch);

        assertTrue(pool.getAllChannel().isEmpty());
    }

    @Test
    void removeByEndpoint_closesAllChannels() {
        Channel ch1 = mockChannel("127.0.0.1", 9000);
        Channel ch2 = mockChannel("127.0.0.1", 9000);

        pool.addChannel(ch1);
        pool.addChannel(ch2);

        int removed = pool.removeByEndpoint(endpoint.id());

        assertEquals(2, removed);
        assertTrue(pool.getAllChannel().isEmpty());
    }

    /* ===================== activeChannels ===================== */

    @Test
    void activeChannels_onlyReturnsActive() {
        Channel ch = mockChannel("127.0.0.1", 9000);
        when(ch.isActive()).thenReturn(false);

        pool.addChannel(ch);

        assertTrue(pool.activeChannels().isEmpty());
    }

    /* ===================== closeAll ===================== */

    @Test
    void closeAll_closesAndClears() {
        Channel ch = mockChannel("127.0.0.1", 9000);
        pool.addChannel(ch);

        pool.closeAll();

        verify(ch).close();
        assertTrue(pool.getAllChannel().isEmpty());
    }

    /* ===================== concurrency ===================== */

    @Test
    void concurrentAddChannel_isThreadSafe() throws Exception {
        ExecutorService exec = Executors.newFixedThreadPool(10);

        List<Callable<Boolean>> tasks = IntStream.range(0, 50)
                .mapToObj(i -> (Callable<Boolean>) () -> {
                    Channel ch = mockChannel("127.0.0.1", 9000);
                    return pool.addChannel(ch);
                })
                .toList();

        exec.invokeAll(tasks);
        exec.shutdown();

        assertTrue(pool.getAllChannel().size() <= 50);
    }
}
