package com.socket.edge.core.socket;

import java.util.concurrent.atomic.AtomicInteger;

public abstract class AbstractSocket implements AutoCloseable {

    String name;
    String id;

    public AbstractSocket(String id, String name) {
        this.name = name;
        this.id = id;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
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
