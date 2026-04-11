package com.socket.edge.utils;

import com.typesafe.config.Config;

import java.util.List;

/**
 * Configuration utility for accessing Typesafe Config values.
 *
 * <p>v3.0: No longer uses static {@code SystemBootstrap.getConfig()}.
 * Must be constructed with a Config instance.</p>
 *
 * @author Ari Patriana
 * @since 3.0.0
 */
public class ConfigUtil {

    private final Config config;

    public ConfigUtil(Config config) {
        this.config = config;
    }

    public boolean getBoolean(String key) {
        return config.hasPath(key);
    }

    public String getString(String key) {
        return config.getString(key);
    }

    public int getInt(String key) {
        return config.getInt(key);
    }

    public boolean getBoolean(String key, boolean def) {
        return config.hasPath(key) ? config.getBoolean(key) : def;
    }

    public String getString(String key, String def) {
        return config.hasPath(key) ? config.getString(key) : def;
    }

    public int getInt(String key, int def) {
        return config.hasPath(key) ? config.getInt(key) : def;
    }

    public List<String> getStringList(String key) {
        return config.getStringList(key);
    }
}
