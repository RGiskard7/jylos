package com.example.jylos;

import java.io.InputStream;
import java.util.Properties;
import java.util.logging.Logger;

import com.example.jylos.config.VersionConfig;

/**
 * Centralized application metadata loaded from {@code app.properties}.
 *
 * <p>Release builds may override the version through filtered resources; when the
 * properties file is absent or unresolved, values fall back to safe defaults.</p>
 */
public final class AppConfig {
    
    private static final Logger logger = Logger.getLogger(AppConfig.class.getName());
    private static Properties properties = null;
    
    static {
        loadProperties();
    }

    private AppConfig() {
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
                properties.setProperty("app.version", VersionConfig.getVersion());
                properties.setProperty("app.vendor", "Jylos");
                properties.setProperty("app.description", "A free and open-source note-taking application");
                properties.setProperty("app.copyright", "Copyright © 2026 Eduardo Díaz Sánchez");
                properties.setProperty("app.window.title", "Jylos - Knowledge Management");
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
            properties.setProperty("app.version", VersionConfig.getVersion());
            properties.setProperty("app.vendor", "Jylos");
            properties.setProperty("app.description", "A free and open-source note-taking application");
            properties.setProperty("app.copyright", "Copyright © 2026 Eduardo Díaz Sánchez");
            properties.setProperty("app.window.title", "Jylos - Local-first Knowledge Management");
        }
    }
    
    /**
     * Gets a property value, with optional default.
     */
    private static String getProperty(String key, String defaultValue) {
        return properties.getProperty(key, defaultValue);
    }
    
    /** @return display application name */
    public static String getAppName() {
        return getProperty("app.name", "Jylos");
    }
    
    /** @return release version displayed in UI and packaging metadata */
    public static String getAppVersion() {
        String version = getProperty("app.version", "");
        return VersionConfig.isUnresolved(version) ? VersionConfig.getVersion() : version;
    }
    
    /** @return application vendor */
    public static String getAppVendor() {
        return getProperty("app.vendor", "Jylos");
    }
    
    /** @return short product description */
    public static String getAppDescription() {
        return getProperty("app.description", "A free and open-source note-taking application");
    }
    
    /** @return copyright notice */
    public static String getAppCopyright() {
        return getProperty("app.copyright", "Copyright © 2026 Eduardo Díaz Sánchez");
    }
    
    /** @return main window title */
    public static String getWindowTitle() {
        return getProperty("app.window.title", "Jylos - Local-first Knowledge Management");
    }
    
    /** @return JavaFX window icon path, relative to the resources root */
    public static String getWindowIconPath() {
        return getProperty("app.icon.window", "icons/app-icon.png");
    }
    
    /** @return Windows packaging icon path, relative to the Maven module root */
    public static String getIconPathWindows() {
        return getProperty("app.icon.windows", "src/main/resources/icons/app-icon.ico");
    }
    
    /** @return macOS packaging icon path, relative to the Maven module root */
    public static String getIconPathMacOS() {
        return getProperty("app.icon.macos", "src/main/resources/icons/app-icon.icns");
    }
    
    /** @return Linux packaging icon path, relative to the Maven module root */
    public static String getIconPathLinux() {
        return getProperty("app.icon.linux", "src/main/resources/icons/app-icon.png");
    }
    
    /** @return package/application id used by native installers */
    public static String getPackageName() {
        return getProperty("app.package.name", "jylos");
    }
    
    /** @return Windows installer category */
    public static String getPackageCategoryWindows() {
        return getProperty("app.package.category.windows", "Productivity");
    }
    
    /** @return macOS package category UTI */
    public static String getPackageCategoryMacOS() {
        return getProperty("app.package.category.macos", "public.app-category.productivity");
    }
    
    /** @return Linux desktop/menu package category */
    public static String getPackageCategoryLinux() {
        return getProperty("app.package.category.linux", "Office");
    }
}
