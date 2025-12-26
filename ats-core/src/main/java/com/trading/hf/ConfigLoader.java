package com.trading.hf;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.Properties;

public class ConfigLoader {

    private static final Properties properties = new Properties();
    private static final String CONFIG_FILE = "config.properties";

    static {
        InputStream input = null;
        try {
            // 1. Try loading from the classpath first
            input = ConfigLoader.class.getClassLoader().getResourceAsStream(CONFIG_FILE);
            if (input != null) {
                properties.load(input);
                input.close();
            }

            // 2. Try loading from the filesystem (current directory) - this will override classpath settings
            File file = new File(CONFIG_FILE);
            if (file.exists()) {
                // Keep this as a high-level info print as it's critical for startup awareness
                try (InputStream fileInput = new FileInputStream(file)) {
                    properties.load(fileInput);
                }
            } else if (input == null) {
                 // No logger available yet in static block reliably for some setups, but SLF4J is fine here
            }

        } catch (Exception ex) {
            ex.printStackTrace();
        } finally {
            if (input != null) {
                try {
                    input.close();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }

    public static String getProperty(String key) {
        return properties.getProperty(key);
    }

    public static String getProperty(String key, String defaultValue) {
        return properties.getProperty(key, defaultValue);
    }

    public static boolean getBooleanProperty(String key, boolean defaultValue) {
        String value = getProperty(key);
        if (value == null) {
            return defaultValue;
        }
        return Boolean.parseBoolean(value);
    }
    public static long getLongProperty(String key, long defaultValue) {
        String value = getProperty(key);
        if (value == null) {
            return defaultValue;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
}
