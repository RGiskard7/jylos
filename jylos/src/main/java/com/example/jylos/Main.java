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

    // Before the logger loads (it needs logs/ to exist): first move what an older
    // installed build left in the wrong place, then create the folders.
    private static final LegacyDataMigration.Report LEGACY_MIGRATION;

    static {
        LegacyDataMigration.Report report;
        try {
            report = AppDataDirectory.migrateLegacyData();
        } catch (RuntimeException e) {
            report = new LegacyDataMigration.Report(
                    java.util.List.of("Legacy data migration failed: " + e), java.util.Optional.empty());
        }
        LEGACY_MIGRATION = report;
        if (!AppDataDirectory.ensureDirectoriesExist()) {
            System.err.println("Warning: Could not create data/logs directories");
        }
    }

    private static final Logger logger = LoggerConfig.getLogger(Main.class);

    @Override
    public void start(Stage primaryStage) {
        try {
            ensureDirectoriesExist();

            Preferences prefs = Preferences.userNodeForPackage(MainController.class);
            String lang = prefs.get("language", Locale.getDefault().getLanguage());
            Locale locale = Locale.forLanguageTag(lang);
            Locale.setDefault(locale);
            ResourceBundle bundle = ResourceBundle.getBundle("com.example.jylos.i18n.messages", locale);

            try {
                prepareSqliteInfrastructureIfNeeded();
            } catch (RuntimeException e) {
                showStartupDatabaseError(bundle, e);
                Platform.exit();
                return;
            }

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
            primaryStage.setMaximized(true);
            mainController.applyInitialThemeBeforeShow();
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

            LEGACY_MIGRATION.messages().forEach(message -> logger.info("Data migration: " + message));
            logger.info("Jylos started. Data: " + AppDataDirectory.getDataDirectory()
                    + " | Logs: " + AppDataDirectory.getLogsDirectory());

        } catch (IOException e) {
            logger.severe("Failed to load main view: " + e.getMessage());
            throw new RuntimeException("Failed to start application", e);
        }
    }

    /**
     * The database could not be opened or brought to the required schema. Tell the user
     * why the app is closing and where the automatic backups are, instead of exiting with
     * only a stack trace in the log.
     */
    private void showStartupDatabaseError(ResourceBundle bundle, RuntimeException error) {
        Throwable cause = error.getCause() != null ? error.getCause() : error;
        String detail = cause.getMessage() != null ? cause.getMessage() : cause.toString();
        javafx.scene.control.Alert alert = new javafx.scene.control.Alert(javafx.scene.control.Alert.AlertType.ERROR);
        alert.setTitle(bundle.getString("startup.database_error.title"));
        alert.setHeaderText(bundle.getString("startup.database_error.header"));
        alert.setContentText(java.text.MessageFormat.format(bundle.getString("startup.database_error.content"),
                detail, AppDataDirectory.getBackupsDirectory()));
        AppIconLoader.load().ifPresent(img -> ((Stage) alert.getDialogPane().getScene().getWindow()).getIcons().add(img));
        alert.showAndWait();
    }

    private void prepareSqliteInfrastructureIfNeeded() {
        Preferences prefs = Preferences.userNodeForPackage(MainController.class);
        String storageType = prefs.get("storage_type", System.getProperty("jylos.storage", "sqlite"));
        if (!"sqlite".equalsIgnoreCase(storageType)) {
            logger.info("Skipping SQLite startup preparation for storage mode: " + storageType);
            return;
        }

        try {
            DatabaseBackupService.createStartupBackupIfNeeded();
            String dbPath = new File(AppDataDirectory.getDataDirectory(), "database.db").getAbsolutePath();

            SQLiteDB.configure(dbPath);
            SQLiteDB.getInstance().initDatabase();

            logger.info("SQLite storage prepared at: " + dbPath);
        } catch (Exception e) {
            logger.severe("Failed to prepare SQLite storage: " + e.getMessage());
            throw new RuntimeException("SQLite storage preparation failed", e);
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
