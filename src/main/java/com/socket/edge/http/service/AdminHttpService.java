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
    private ChannelCfgProcessor channelCfgProcessor;
    private ReloadCfgService reloadCfgService;

    public AdminHttpService(SocketManager socketManager, ChannelCfgProcessor channelCfgProcessor, ReloadCfgService reloadCfgService) {
        this.socketManager = socketManager;
        this.channelCfgProcessor = channelCfgProcessor;
        this.reloadCfgService = reloadCfgService;
    }

    public void validate() throws IOException {
        Path path = Path.of(System.getProperty("base.dir"),"conf", "channel.conf");
        channelCfgProcessor.process(path);
    }

    public void reload() throws IOException {
        Path path = Path.of(System.getProperty("base.dir"),"conf", "channel.conf");
        Metadata newMetadata = channelCfgProcessor.process(path);
        reloadCfgService.reloadConfig(newMetadata);
    }

    public void startSocketById(String id) throws InterruptedException {
        log.info("Start socket by id {}", id);
        socketManager.start(id);
    }

    public void startSocketByName(String name) {
        log.info("Start socket by name {}", name);
        socketManager.startByName(name);
    }

    public void stopSocketById(String id) throws InterruptedException {
        log.info("Stop socket by id {}", id);
        socketManager.stop(id);
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
