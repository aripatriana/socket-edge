package com.socket.edge.http.service;

import com.socket.edge.core.ChannelCfgProcessor;
import com.socket.edge.core.socket.SocketManager;
import com.socket.edge.model.Metadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;

public class AdminHttpService {

    private static final Logger log = LoggerFactory.getLogger(AdminHttpService.class);

    private SocketManager socketManager;

    public AdminHttpService(SocketManager socketManager) {
        this.socketManager = socketManager;
    }

    public void startSocketById(String id) throws InterruptedException {
        log.info("Start socket by id {}", id);
        socketManager.startById(id);
    }

    public void startSocketByName(String name) {
        log.info("Start socket by name {}", name);
        socketManager.startByName(name);
    }

    public void stopSocketById(String id) throws InterruptedException {
        log.info("Stop socket by id {}", id);
        socketManager.stopById(id);
    }

    public void stopSocketByName(String name) {
        log.info("Stop socket by name {}", name);
        socketManager.stopByName(name);
    }

    public void startAllSocket() {
        log.info("Start all socket");
        socketManager.startAll();
    }

    public void stopAllSocket() {
        log.info("Stop all socket");
        socketManager.stopAll();
    }

    public void restartSocketById(String id) throws InterruptedException {
        log.info("Restart socket by id {}", id);
        socketManager.restart(id);
    }

    public void restartSocketByName(String name) throws InterruptedException {
        log.info("Restart socket by name {}", name);
        socketManager.restartByName(name);
    }

    public void restartAll() throws InterruptedException {
        log.info("Restart all socket");
        socketManager.restartAll();
    }
}
