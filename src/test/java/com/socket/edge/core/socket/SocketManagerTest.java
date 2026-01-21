package com.socket.edge.core.socket;

import com.socket.edge.core.transport.TransportRegister;
import com.socket.edge.model.ChannelCfg;
import com.socket.edge.model.ClientChannel;
import com.socket.edge.model.ServerChannel;
import com.socket.edge.model.SocketEndpoint;
import com.socket.edge.utils.CommonUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SocketManagerTest {

    @Mock
    SocketFactory socketFactory;

    @Mock
    TransportRegister transportRegister;

    @Mock
    AbstractSocket serverSocket;

    @Mock
    AbstractSocket clientSocket;

    SocketManager socketManager;

    SocketEndpoint endpoint;
    ChannelCfg cfg;

    @BeforeEach
    void setUp() {
        socketManager = new SocketManager(socketFactory, transportRegister);

        endpoint = new SocketEndpoint("localhost", 9000, 1, 1);

        cfg = new ChannelCfg(
                "CH1",
                "ISO",
                new ServerChannel("0.0.0.0", 7000, List.of(endpoint), "RR"),
                new ClientChannel(List.of(endpoint), "RR"),
                "default"
        );

    }

    /* ===============================
       CREATE
       =============================== */

    @Test
    void createSocket_shouldCreateServerAndClient() {
        when(serverSocket.getId()).thenReturn(CommonUtil.serverId("CH1", 7000));
        when(clientSocket.getId()).thenReturn(CommonUtil.clientId("CH1", "localhost", 9000));

        when(socketFactory.createServer(cfg)).thenReturn(serverSocket);
        when(socketFactory.createClient(cfg, endpoint)).thenReturn(clientSocket);

        List<AbstractSocket> sockets = socketManager.createSocket(cfg);

        assertEquals(2, sockets.size());
        verify(transportRegister).registerServerTransport(cfg, serverSocket);
        verify(transportRegister).registerClientTransport(eq(cfg), anyList());
    }

    @Test
    void createServerSocket_duplicate_shouldNotRegisterAgain() {
        when(serverSocket.getId()).thenReturn(CommonUtil.serverId("CH1", 7000));
        when(clientSocket.getId()).thenReturn(CommonUtil.clientId("CH1", "localhost", 9000));

        when(socketFactory.createServer(cfg)).thenReturn(serverSocket);
        when(socketFactory.createClient(cfg, endpoint)).thenReturn(clientSocket);

        socketManager.createSocket(cfg);
        socketManager.createSocket(cfg);

        verify(transportRegister, times(1))
                .registerServerTransport(cfg, serverSocket);
    }

    @Test
    void createClientSockets_emptyClient_shouldReturnEmpty() {
        ChannelCfg noClient = new ChannelCfg(
                "CH1", "ISO",
                new ServerChannel("0.0.0.0", 7000, List.of(endpoint), "RR"),
                null,
                "default"
        );

        List<AbstractSocket> result = socketManager.createClientSockets(noClient);

        assertTrue(result.isEmpty());
        verifyNoInteractions(socketFactory);
    }

    /* ===============================
       START / STOP
       =============================== */

    @Test
    void start_shouldInvokeSocketStart() throws Exception {
        socketManager.start(clientSocket);

        verify(clientSocket).start();
    }

    @Test
    void start_whenInterrupted_shouldWrapException() throws Exception {
        when(clientSocket.getId()).thenReturn("X");
        doThrow(new InterruptedException("boom"))
                .when(clientSocket).start();

        RuntimeException ex = assertThrows(
                RuntimeException.class,
                () -> socketManager.start(clientSocket)
        );

        assertTrue(ex.getCause() instanceof InterruptedException);
    }

    @Test
    void stop_shouldInvokeSocketStop() throws Exception {
        socketManager.stop(clientSocket);

        verify(clientSocket).stop();
    }

    /* ===============================
       START / STOP BY ID
       =============================== */

    @Test
    void startById_shouldStartCorrectSocket() throws Exception {
        when(serverSocket.getId()).thenReturn(CommonUtil.serverId("CH1", 7000));
        when(clientSocket.getId()).thenReturn(CommonUtil.clientId("CH1", "localhost", 9000));

        when(socketFactory.createServer(cfg)).thenReturn(serverSocket);
        when(socketFactory.createClient(cfg, endpoint)).thenReturn(clientSocket);

        socketManager.createSocket(cfg);
        socketManager.startById(CommonUtil.serverId("CH1", 7000));

        verify(serverSocket).start();
    }

    @Test
    void startById_notFound_shouldThrow() {
        assertThrows(
                IllegalArgumentException.class,
                () -> socketManager.startById("UNKNOWN")
        );
    }

    /* ===============================
       START / STOP BY NAME
       =============================== */

    @Test
    void startByName_shouldStartAllMatchingSockets() throws Exception {
        when(serverSocket.getId()).thenReturn(CommonUtil.serverId("CH1", 7000));
        when(clientSocket.getId()).thenReturn(CommonUtil.clientId("CH1", "localhost", 9000));

        when(serverSocket.getName()).thenReturn("CH1");
        when(clientSocket.getName()).thenReturn("CH2");

        when(socketFactory.createServer(cfg)).thenReturn(serverSocket);
        when(socketFactory.createClient(cfg, endpoint)).thenReturn(clientSocket);

        socketManager.createSocket(cfg);
        socketManager.startByName("CH1");
        socketManager.startByName("CH2");

        verify(serverSocket).start();
        verify(clientSocket).start();
    }

    /* ===============================
       RESTART
       =============================== */

    @Test
    void restartById_shouldStopThenStart() throws Exception {
        when(serverSocket.getId()).thenReturn(CommonUtil.serverId("CH1", 7000));
        when(clientSocket.getId()).thenReturn(CommonUtil.clientId("CH1", "localhost", 9000));

        when(socketFactory.createServer(cfg)).thenReturn(serverSocket);
        when(socketFactory.createClient(cfg, endpoint)).thenReturn(clientSocket);

        socketManager.createSocket(cfg);
        socketManager.restart(CommonUtil.serverId("CH1", 7000));

        verify(serverSocket).stop();
        verify(serverSocket).start();
    }

    /* ===============================
       DESTROY
       =============================== */

    @Test
    void destroyServerSocket_shouldShutdownAndUnregister() throws InterruptedException {
        when(serverSocket.getId()).thenReturn(CommonUtil.serverId("CH1", 7000));
        when(clientSocket.getId()).thenReturn(CommonUtil.clientId("CH1", "localhost", 9000));

        when(socketFactory.createServer(cfg)).thenReturn(serverSocket);
        when(socketFactory.createClient(cfg, endpoint)).thenReturn(clientSocket);

        socketManager.createSocket(cfg);
        socketManager.destroyServerSocket(cfg);

        verify(serverSocket).shutdown();
        verify(transportRegister).unregisterServerTransport(cfg);
    }

    @Test
    void destroyClientSocket_shouldShutdownAndUnregister() throws InterruptedException {
        when(clientSocket.getId()).thenReturn(CommonUtil.clientId("CH1", "localhost", 9000));
        when(socketFactory.createClient(cfg, endpoint)).thenReturn(clientSocket);

        socketManager.createClientSockets(cfg);
        socketManager.destroyClientSocket(cfg, endpoint);

        verify(clientSocket).shutdown();
        verify(transportRegister).unregisterClientTransport(eq(cfg), any(AbstractSocket.class));
    }

    @Test
    void destroyAll_shouldShutdownAllSockets_evenIfOneFails() throws InterruptedException {
        when(serverSocket.getId()).thenReturn(CommonUtil.serverId("CH1", 7000));
        when(clientSocket.getId()).thenReturn(CommonUtil.clientId("CH1", "localhost", 9000));

        when(socketFactory.createServer(cfg)).thenReturn(serverSocket);
        when(socketFactory.createClient(cfg, endpoint)).thenReturn(clientSocket);

        doThrow(new RuntimeException("boom"))
                .when(serverSocket).shutdown();

        socketManager.createSocket(cfg);
        socketManager.destroyAll();

        verify(serverSocket).shutdown();
        verify(clientSocket).shutdown();

    }

    /* ===============================
       REQUIRE SOCKET
       =============================== */

    @Test
    void requireSocket_notFound_shouldThrow() {
        assertThrows(
                IllegalArgumentException.class,
                () -> socketManager.requireSocket("NOPE")
        );
    }
}
