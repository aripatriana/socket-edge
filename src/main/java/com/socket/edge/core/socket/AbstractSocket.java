package com.socket.edge.core.socket;

import com.socket.edge.model.SocketType;

import java.util.concurrent.atomic.AtomicInteger;

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

    public long getUptime() {
        return startTime > 0 ? (System.currentTimeMillis() - startTime): 0;
    }

    /*
     * start the socket
     */
    public abstract void start() throws InterruptedException;

    /*
     * stop the socket
     */
    public abstract void stop();

    public abstract boolean isUp();

    public abstract SocketChannelPool channelPool();

    @Override
    public void close() throws Exception {
        stop();
    }

}
