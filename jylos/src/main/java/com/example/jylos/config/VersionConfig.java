package com.example.jylos.config;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Reads the application version embedded at build time.
 */
public final class VersionConfig {

    private static final Logger logger = LoggerConfig.getLogger(VersionConfig.class);
    private static final String VERSION_RESOURCE = "version.properties";
    private static final String FALLBACK_VERSION = "v0.0.0";

    private VersionConfig() {
    }

    public static String getVersion() {
        Properties properties = new Properties();
        try (InputStream input = VersionConfig.class.getClassLoader().getResourceAsStream(VERSION_RESOURCE)) {
            if (input == null) {
                logger.warning("version.properties not found, using fallback version");
                return FALLBACK_VERSION;
            }
            try (var reader = new java.io.InputStreamReader(input, StandardCharsets.UTF_8)) {
                properties.load(reader);
            }
            String version = properties.getProperty("app.version", "").trim();
            return isUnresolved(version) ? FALLBACK_VERSION : version;
        } catch (Exception e) {
            logger.log(Level.WARNING, "Failed to load version.properties", e);
            return FALLBACK_VERSION;
        }
    }

    public static boolean isUnresolved(String version) {
        return version == null || version.isBlank() || version.contains("${");
    }
}
