package com.example.jylos;

import java.io.InputStream;
import java.util.Properties;
import java.util.logging.Logger;

/**
 * Centralized application configuration.
 * Reads metadata from app.properties file.
 * 
 * This allows easy rebranding by modifying a single properties file.
 */
public class AppConfig {
    
    private static final Logger logger = Logger.getLogger(AppConfig.class.getName());
    private static Properties properties = null;
    
    static {
        loadProperties();
    }
    
    /**
     * Loads application properties from app.properties file.
     */
    private static void loadProperties() {
        properties = new Properties();
        try (InputStream input = AppConfig.class.getClassLoader()
                .getResourceAsStream("app.properties")) {
            
            if (input == null) {
                logger.warning("app.properties not found, using defaults");
                // Set defaults
                properties.setProperty("app.name", "Jylos");
                properties.setProperty("app.version", "2.2.0");
                properties.setProperty("app.vendor", "Jylos");
                properties.setProperty("app.description", "A free and open-source note-taking application");
                properties.setProperty("app.copyright", "Copyright © 2026 Eduardo Díaz Sánchez");
                properties.setProperty("app.window.title", "Jylos - Free Note Taking");
                return;
            }
            
            // app.properties is UTF-8 (it contains © and accented names). Properties.load(InputStream)
            // would decode it as ISO-8859-1 and mangle those characters, so read via a UTF-8 Reader.
            try (java.io.Reader reader = new java.io.InputStreamReader(
                    input, java.nio.charset.StandardCharsets.UTF_8)) {
                properties.load(reader);
            }
            logger.info("Application properties loaded successfully");
        } catch (Exception e) {
            logger.warning("Failed to load app.properties: " + e.getMessage() + ", using defaults");
            // Set defaults on error
            properties.setProperty("app.name", "Jylos");
            properties.setProperty("app.version", "2.2.0");
            properties.setProperty("app.vendor", "Jylos");
            properties.setProperty("app.description", "A free and open-source note-taking application");
            properties.setProperty("app.copyright", "Copyright © 2026 Eduardo Díaz Sánchez");
            properties.setProperty("app.window.title", "Jylos - Free Note Taking");
        }
    }
    
    /**
     * Gets a property value, with optional default.
     */
    private static String getProperty(String key, String defaultValue) {
        return properties.getProperty(key, defaultValue);
    }
    
    // Application metadata
    public static String getAppName() {
        return getProperty("app.name", "Jylos");
    }
    
    public static String getAppVersion() {
        return getProperty("app.version", "2.2.0");
    }
    
    public static String getAppVendor() {
        return getProperty("app.vendor", "Jylos");
    }
    
    public static String getAppDescription() {
        return getProperty("app.description", "A free and open-source note-taking application");
    }
    
    public static String getAppCopyright() {
        return getProperty("app.copyright", "Copyright © 2026 Eduardo Díaz Sánchez");
    }
    
    public static String getWindowTitle() {
        return getProperty("app.window.title", "Jylos - Free Note Taking");
    }
    
    // Window icon path (for JavaFX, relative to resources root, e.g., "com/example/jylos/ui/images/app-icon.png")
    public static String getWindowIconPath() {
        return getProperty("app.icon.window", "icons/app-icon.png");
    }
    
    // Packaging icon paths (relative to project root)
    public static String getIconPathWindows() {
        return getProperty("app.icon.windows", "src/main/resources/icons/app-icon.ico");
    }
    
    public static String getIconPathMacOS() {
        return getProperty("app.icon.macos", "src/main/resources/icons/app-icon.icns");
    }
    
    public static String getIconPathLinux() {
        return getProperty("app.icon.linux", "src/main/resources/icons/app-icon.png");
    }
    
    // Package information
    public static String getPackageName() {
        return getProperty("app.package.name", "jylos");
    }
    
    public static String getPackageCategoryWindows() {
        return getProperty("app.package.category.windows", "Productivity");
    }
    
    public static String getPackageCategoryMacOS() {
        return getProperty("app.package.category.macos", "public.app-category.productivity");
    }
    
    public static String getPackageCategoryLinux() {
        return getProperty("app.package.category.linux", "Office");
    }
}

