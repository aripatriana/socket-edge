package com.socket.edge.http.service;

import com.socket.edge.core.engine.SEEngine;
import com.socket.edge.core.socket.AbstractSocket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.Objects;

public class AdminHttpService {

    private static final Logger log = LoggerFactory.getLogger(AdminHttpService.class);
    private Map<String, AbstractSocket> sockets;

    public AdminHttpService(Map<String, AbstractSocket> sockets) {
        this.sockets = sockets;
    }

    public boolean validate() {
        return false;
    }

    public void reload() {

    }

    public void startSocketById(String id) {
        log.info("Start socket by id {}", id);
        Objects.requireNonNull(id, "Required id");
        AbstractSocket socket = sockets.get(id);
        Objects.requireNonNull(socket, "Object socket null");
        try {
            socket.start();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    public void startSocketByName(String name) {
        log.info("Start socket by name {}", name);
        Objects.requireNonNull(name, "Required name");
        List<AbstractSocket> socket = sockets.values()
                .stream()
                .filter(s -> name.equals(s.getName()))
                .toList();
        Objects.requireNonNull(socket, "Object socket null");
        socket.forEach(s -> {
            try {
                s.start();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        });
    }

    public void stopSocketById(String id) {
        Objects.requireNonNull(id, "Required name");
        AbstractSocket socket = sockets.get(id);
        Objects.requireNonNull(socket, "Object socket null");
        try {
            socket.stop();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    public void stopSocketByName(String name) {
        Objects.requireNonNull(name, "Required name");
        List<AbstractSocket> socket = sockets.values()
                .stream()
                .filter(s -> name.equals(s.getName()))
                .toList();
        Objects.requireNonNull(socket, "Object socket null");
        socket.forEach(s -> {
            try {
                s.stop();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        });
    }

    public void startAllSocket() {
        sockets.values().forEach(s -> {
            try {
                s.start();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        });
    }

    public void stopAllSocket() {
        sockets.values().forEach(s -> {
            try {
                s.stop();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        });
    }

    public void restartSocketById(String id) {
        Objects.requireNonNull(id, "Required id");
        stopSocketById(id);
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        startSocketById(id);
    }

    public void restartSocketByName(String name) {
        Objects.requireNonNull(name, "Required name");
        stopSocketByName(name);
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        startSocketByName(name);
    }

    public void restartAll() {
        stopAllSocket();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        startAllSocket();
    }
}
