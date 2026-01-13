package com.socket.edge.http.service;

import com.socket.edge.core.socket.AbstractSocket;
import com.socket.edge.core.socket.NettyClientSocket;
import com.socket.edge.core.socket.NettyServerSocket;
import com.socket.edge.model.SocketInfo;

import java.util.List;

public class SocketInfoService {

    private final long startTime = System.currentTimeMillis();
    private List<AbstractSocket> sockets;

    public SocketInfoService(List<AbstractSocket> sockets) {
        this.sockets = sockets;
    }

    public long uptimeSeconds() {
        return (System.currentTimeMillis() - startTime) / 1000;
    }

    public List<SocketInfo> socketInfo() {
        return sockets.stream()
                .map(socket -> {

                    int activeClient = 0;
                    int activeServer = 0;

                    if (socket instanceof NettyClientSocket) {
                        activeClient = socket.channelPool() != null
                                ? socket.channelPool().activeChannels().size()
                                : 0;
                    }

                    if (socket instanceof NettyServerSocket) {
                        activeServer = socket.channelPool() != null
                                ? socket.channelPool().activeChannels().size()
                                : 0;
                    }

                    String status = socket.isUp() ? "UP" : "DOWN";

                    return new SocketInfo(
                            socket.getId(),
                            socket.getName(),
                            activeClient,
                            activeServer,
                            status
                    );
                })
                .toList();
    }
}
