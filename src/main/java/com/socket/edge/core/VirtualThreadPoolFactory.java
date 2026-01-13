package com.socket.edge.core;

import org.apache.camel.spi.ExecutorServiceManager;
import org.apache.camel.spi.ThreadPoolFactory;
import org.apache.camel.spi.ThreadPoolProfile;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;

public class VirtualThreadPoolFactory implements ThreadPoolFactory {

    @Override
    public ExecutorService newCachedThreadPool(ThreadFactory threadFactory) {
        // Virtual thread executor (Camel akan manage lifecycle)
        return Executors.newThreadPerTaskExecutor(threadFactory);
    }

    @Override
    public ExecutorService newThreadPool(
            ThreadPoolProfile profile,
            ThreadFactory threadFactory) {
        // Ignore pool sizing, virtual threads scale naturally
        return Executors.newThreadPerTaskExecutor(threadFactory);
    }

    @Override
    public ScheduledExecutorService newScheduledThreadPool(
            ThreadPoolProfile profile,
            ThreadFactory threadFactory) {
        // Scheduled MUST stay platform threads
        return Executors.newSingleThreadScheduledExecutor(threadFactory);
    }
}
