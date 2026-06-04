package com.example.jylos;

import java.io.File;

/**
 * Utility class to determine the application data directory.
 * 
 * Strategy:
 * 1. First, try to use relative paths (data/, logs/) - works for development
 * and portable apps
 * 2. If that fails (read-only directory), use platform-specific user data
 * directory
 * 
 * Standard directories by platform (fallback):
 * - Windows: %APPDATA%\Jylos
 * - macOS: ~/Library/Application Support/Jylos
 * - Linux: ~/.config/Jylos
 */
public class AppDataDirectory {

    private static String baseDir = null;

    /**
     * Gets the base directory for application data.
     * First tries relative path, falls back to platform-specific directory.
     */
    public static String getBaseDirectory() {
        if (baseDir != null) {
            return baseDir;
        }

        // Strategy 1: Try relative path (works for development and portable apps)
        String userDir = System.getProperty("user.dir", ".");
        File testDir = new File(userDir, "data");

        if (canWriteToDirectory(testDir)) {
            baseDir = userDir;
            return baseDir;
        }

        // Strategy 2: Use platform-specific directory (for packaged apps in read-only
        // locations)
        baseDir = getPlatformAppDataDirectory();
        return baseDir;
    }

    /**
     * Checks if we can write to a directory (exists and writable, or can be
     * created).
     */
    private static boolean canWriteToDirectory(File dir) {
        try {
            if (dir.exists()) {
                return dir.isDirectory() && dir.canWrite();
            }
            // Try to create and delete to test write permission
            if (dir.mkdirs()) {
                dir.delete();
                return true;
            }
            return false;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Gets the platform-specific application data directory.
     */
    private static String getPlatformAppDataDirectory() {
        String osName = System.getProperty("os.name", "").toLowerCase();
        String home = System.getProperty("user.home");
        File appDataDir;

        String appName = AppConfig.getAppName();

        if (osName.contains("win")) {
            // Windows: %APPDATA%\AppName
            String appData = System.getenv("APPDATA");
            if (appData != null && !appData.isEmpty()) {
                appDataDir = new File(appData, appName);
            } else {
                appDataDir = new File(home, "AppData\\Roaming\\" + appName);
            }
        } else if (osName.contains("mac")) {
            // macOS: ~/Library/Application Support/AppName
            appDataDir = new File(home, "Library/Application Support/" + appName);
        } else {
            // Linux: ~/.config/AppName (XDG Base Directory)
            String xdgConfig = System.getenv("XDG_CONFIG_HOME");
            if (xdgConfig != null && !xdgConfig.isEmpty()) {
                appDataDir = new File(xdgConfig, appName);
            } else {
                appDataDir = new File(home, ".config/" + appName);
            }
        }

        return appDataDir.getAbsolutePath();
    }

    /**
     * Gets the data directory path (baseDir/data).
     */
    public static String getDataDirectory() {
        return new File(getBaseDirectory(), "data").getAbsolutePath();
    }

    /**
     * Gets the logs directory path (baseDir/logs).
     */
    public static String getLogsDirectory() {
        return new File(getBaseDirectory(), "logs").getAbsolutePath();
    }

    /**
     * Gets the backups directory path (baseDir/backups).
     */
    public static String getBackupsDirectory() {
        return new File(getBaseDirectory(), "backups").getAbsolutePath();
    }

    /**
     * Ensures runtime directories exist: data, logs, backups, plugins, themes.
     * Invoked from {@link Main} static initializer and at startup.
     */
    public static boolean ensureDirectoriesExist() {
        try {
            File dataDir = new File(getDataDirectory());
            File logsDir = new File(getLogsDirectory());
            File pluginsDir = new File(getBaseDirectory(), "plugins");
            File backupsDir = new File(getBackupsDirectory());
            File themesDir = new File(getBaseDirectory(), "themes");

            if (!dataDir.exists()) {
                dataDir.mkdirs();
            }
            if (!logsDir.exists()) {
                logsDir.mkdirs();
            }
            if (!pluginsDir.exists()) {
                pluginsDir.mkdirs();
            }
            if (!backupsDir.exists()) {
                backupsDir.mkdirs();
            }
            if (!themesDir.exists()) {
                themesDir.mkdirs();
            }

            return dataDir.exists() && dataDir.canWrite() &&
                    logsDir.exists() && logsDir.canWrite() &&
                    backupsDir.exists() && backupsDir.canWrite() &&
                    themesDir.exists() && themesDir.canWrite();
        } catch (Exception e) {
            return false;
        }
    }
}
