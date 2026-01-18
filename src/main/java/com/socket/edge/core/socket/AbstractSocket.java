package com.socket.edge.core.socket;

import com.socket.edge.constant.SocketState;
import com.socket.edge.constant.SocketType;

public abstract class AbstractSocket implements AutoCloseable {

    String name;
    String id;
    long startTime;

    public AbstractSocket(String id, String name) {
        this.id = id;
        this.name = name;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public abstract SocketType getType();

    public long getStartTime() {
        return startTime;
    }

    /*
     * start the socket
     */
    public abstract void start() throws InterruptedException;

    /*
     * stop the socket
     */
    public abstract void stop() throws InterruptedException;

    public abstract void shutdown() throws InterruptedException;

    public abstract SocketState getState();

    public abstract SocketChannelPool channelPool();

    @Override
    public void close() throws Exception {
        stop();
    }

}
