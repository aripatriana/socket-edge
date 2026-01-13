package com.socket.edge.utils;

import com.socket.edge.SystemBootstrap;

import static com.socket.edge.SystemBootstrap.sc;

public class ConfigUtil {

    public boolean getBoolean(String key) {
        return sc.hasPath(key);
    }

    public String getString(String key) {
        return sc.getString(key);
    }

    public int getInt(String key) {
        return sc.getInt(key);
    }

    public boolean getBoolean(String key, boolean def) {
        return sc.hasPath(key) ? sc.getBoolean(key) : def;
    }

    public String getString(String key, String def) {
        return sc.hasPath(key) ? sc.getString(key) : def;
    }

    public int getInt(String key, int def) {
        return sc.hasPath(key) ? sc.getInt(key) : def;
    }
}
