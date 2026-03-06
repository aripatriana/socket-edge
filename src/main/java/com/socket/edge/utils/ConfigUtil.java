package com.socket.edge.utils;

import com.socket.edge.SystemBootstrap;
import com.typesafe.config.Config;

import java.util.List;

public class ConfigUtil {

    /**
     * Returns the global system configuration.
     */
    private static Config sc() {
        return SystemBootstrap.getConfig();
    }

    public boolean getBoolean(String key) {
        return sc().hasPath(key);
    }

    public String getString(String key) {
        return sc().getString(key);
    }

    public int getInt(String key) {
        return sc().getInt(key);
    }

    public boolean getBoolean(String key, boolean def) {
        return sc().hasPath(key) ? sc().getBoolean(key) : def;
    }

    public String getString(String key, String def) {
        return sc().hasPath(key) ? sc().getString(key) : def;
    }

    public int getInt(String key, int def) {
        return sc().hasPath(key) ? sc().getInt(key) : def;
    }

    public List<String> getStringList(String key) {
        return sc().getStringList(key);
    }
}
