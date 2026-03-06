package com.socket.edge.utils;

import java.util.List;

import static com.socket.edge.SystemBootstrap.getConfig;

public class ConfigUtil {

    public boolean getBoolean(String key) {
        return getConfig().hasPath(key);
    }

    public String getString(String key) {
        return getConfig().getString(key);
    }

    public int getInt(String key) {
        return getConfig().getInt(key);
    }

    public boolean getBoolean(String key, boolean def) {
        return getConfig().hasPath(key) ? getConfig().getBoolean(key) : def;
    }

    public String getString(String key, String def) {
        return getConfig().hasPath(key) ? getConfig().getString(key) : def;
    }

    public int getInt(String key, int def) {
        return getConfig().hasPath(key) ? getConfig().getInt(key) : def;
    }

    public List<String> getStringList(String key) {
        return getConfig().getStringList(key);
    }
}
