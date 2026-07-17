package com.example.jylos;

/**
 * IMPORTANT: Do NOT run this class directly in VS Code/IDEs.
 * Due to JavaFX module system requirements, use the Launcher class 
 * or the "Jylos (Launcher)" Run configuration.
 */
import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.File;
import java.io.IOException;
import java.util.ResourceBundle;
import java.util.logging.Logger;
import java.util.prefs.Preferences;
import java.util.Locale;

import com.example.jylos.config.LoggerConfig;
import com.example.jylos.data.database.SQLiteDB;
import com.example.jylos.service.DatabaseBackupService;
import com.example.jylos.ui.AppIconLoader;
import com.example.jylos.ui.controller.MainController;

/**
 * JavaFX application entry point. Use {@link Launcher} or {@code scripts/launch-jylos.*}
 * so the JavaFX module path is configured correctly.
 */
public class Main extends Application {

    // Create directories BEFORE logger loads (logger needs logs/ to exist)
    static {
        if (!AppDataDirectory.ensureDirectoriesExist()) {
            System.err.println("Warning: Could not create data/logs directories");
        }
    }

    private static final Logger logger = LoggerConfig.getLogger(Main.class);

    @Override
    public void start(Stage primaryStage) {
        try {
            ensureDirectoriesExist();
            DatabaseBackupService.createStartupBackupIfNeeded();
            initializeDatabase();

            Preferences prefs = Preferences.userNodeForPackage(MainController.class);
            String lang = prefs.get("language", Locale.getDefault().getLanguage());
            Locale locale = Locale.forLanguageTag(lang);
            Locale.setDefault(locale);
            ResourceBundle bundle = ResourceBundle.getBundle("com.example.jylos.i18n.messages", locale);
            FXMLLoader loader = new FXMLLoader(getClass()
                    .getResource("/com/example/jylos/ui/view/MainView.fxml"), bundle);
            Scene scene = new Scene(loader.load(), 1200, 800);
            MainController mainController = loader.getController();

            var cssResource = getClass().getResource("/com/example/jylos/ui/css/modern-theme.css");
            if (cssResource != null) {
                scene.getStylesheets().add(cssResource.toExternalForm());
            }

            primaryStage.setTitle(AppConfig.getWindowTitle());

            AppIconLoader.load().ifPresent(img -> primaryStage.getIcons().add(img));

            primaryStage.setScene(scene);
            primaryStage.setMinWidth(800);
            primaryStage.setMinHeight(600);
            primaryStage.setOnCloseRequest(event -> {
                if (mainController != null && !mainController.requestApplicationClose()) {
                    event.consume();
                }
            });
            primaryStage.show();

            // macOS focus workaround
            if (System.getProperty("os.name", "").toLowerCase().contains("mac")) {
                Platform.runLater(() -> {
                    primaryStage.toFront();
                    primaryStage.requestFocus();
                });
            }

            logger.info("Jylos started. Data: " + AppDataDirectory.getDataDirectory());

        } catch (IOException e) {
            logger.severe("Failed to load main view: " + e.getMessage());
            throw new RuntimeException("Failed to start application", e);
        }
    }

    private void initializeDatabase() {
        try {
            String dbPath = new File(AppDataDirectory.getDataDirectory(), "database.db").getAbsolutePath();

            SQLiteDB.configure(dbPath);
            SQLiteDB.getInstance().initDatabase();

            logger.info("Database initialized at: " + dbPath);
        } catch (Exception e) {
            logger.severe("Failed to initialize database: " + e.getMessage());
            throw new RuntimeException("Database initialization failed", e);
        }
    }

    private void ensureDirectoriesExist() {
        if (!AppDataDirectory.ensureDirectoriesExist()) {
            System.err.println("Warning: Could not create necessary directories");
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}
