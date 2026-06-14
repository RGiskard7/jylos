package com.example.jylos.config;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.logging.ConsoleHandler;
import java.util.logging.FileHandler;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

import com.example.jylos.AppDataDirectory;

/**
 * Configures the logging system for the application.
 * 
 * Reads base configuration from logging.properties, then configures
 * the FileHandler to use the correct logs directory from AppDataDirectory.
 * 
 * Note: AppDataDirectory.ensureDirectoriesExist() must be called before
 * this class is loaded (done in Main's static block).
 */
public class LoggerConfig {

    static {
        try (InputStream configFile = LoggerConfig.class.getClassLoader()
                .getResourceAsStream("logging.properties")) {
            
            if (configFile == null) {
                throw new IOException("Could not find logging.properties file");
            }
            
            // Load base configuration
            LogManager.getLogManager().readConfiguration(configFile);
            
            // Configure FileHandler with absolute path from AppDataDirectory
            configureFileHandler();
            
        } catch (IOException e) {
            Logger.getLogger(LoggerConfig.class.getName())
                .severe("Could not load logging configuration: " + e.getMessage());
        }
    }
    
    /**
     * Configures the FileHandler with the correct absolute path.
     */
    private static void configureFileHandler() {
        try {
            String logsDir = AppDataDirectory.getLogsDirectory();
            String logFile = new File(logsDir, "app.log").getAbsolutePath();
            
            // Reset and reconfigure with absolute path
            LogManager.getLogManager().reset();
            
            // Console handler
            ConsoleHandler consoleHandler = new ConsoleHandler();
            consoleHandler.setLevel(Level.INFO);
            consoleHandler.setFormatter(new SimpleFormatter());
            Logger.getLogger("").addHandler(consoleHandler);
            
            // Rotating file handler: cap each file at ~5 MB and keep 3 generations.
            // Previously this used an unbounded, append-only handler at Level.ALL, so
            // app.log grew without limit and could fill the disk (No space left on device).
            FileHandler fileHandler = new FileHandler(logFile, 5_000_000, 3, true);
            fileHandler.setLevel(Level.INFO);
            fileHandler.setFormatter(new SimpleFormatter());
            Logger.getLogger("").addHandler(fileHandler);

            // INFO root level: avoids persisting third-party FINEST/FINER spam — notably
            // JavaFX's per-frame render logging — which previously bloated app.log.
            Logger.getLogger("").setLevel(Level.INFO);
            
        } catch (Exception e) {
            System.err.println("Warning: Could not configure file logging: " + e.getMessage());
        }
    }

    /**
     * Returns the logger for the given class.
     */
    public static Logger getLogger(Class<?> clazz) {
        return Logger.getLogger(clazz.getName());
    }
}
