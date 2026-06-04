package com.example.jylos;

/**
 * Launcher class for Jylos.
 * 
 * This class is required for jpackage to work correctly with JavaFX applications.
 * When using jpackage with a JavaFX application, the main class cannot directly
 * extend javafx.application.Application because JavaFX modules need to be loaded
 * before the Application class is instantiated.
 * 
 * By using this launcher class (which does NOT extend Application), jpackage can
 * create a proper executable that loads JavaFX classes from the classpath (uber-jar)
 * at runtime, before calling the actual Main class.
 * 
 * @see Main
 */
public class Launcher {
    
    /**
     * Main entry point for the application.
     * This method simply delegates to the Main class.
     * 
     * @param args Command line arguments
     */
    public static void main(String[] args) {
        Main.main(args);
    }
}

