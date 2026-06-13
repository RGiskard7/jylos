package com.example.jylos.ui.controller;

import java.io.File;
import java.text.MessageFormat;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.example.jylos.config.LoggerConfig;
import com.example.jylos.service.ImportService;

import javafx.concurrent.Task;
import javafx.scene.control.Alert;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.Window;

/**
 * UI flow for importing external notes (Obsidian vault folders and Evernote
 * {@code .enex} files): file pickers, the background import task, the result
 * summary and the post-import refresh. The actual import logic lives in
 * {@link ImportService}; this class follows the standard feature-support pattern.
 *
 * @author Edu Díaz (RGiskard7)
 * @since 2.0.0
 */
final class ImportSupport {

    private static final Logger logger = LoggerConfig.getLogger(ImportSupport.class);

    private ImportService importService;
    private Function<String, String> i18n;
    private Consumer<String> status;
    private Supplier<Window> windowSupplier;
    private Runnable refreshAll;

    void wire(ImportService importService, Function<String, String> i18n, Consumer<String> status,
            Supplier<Window> windowSupplier, Runnable refreshAll) {
        this.importService = importService;
        this.i18n = i18n;
        this.status = status;
        this.windowSupplier = windowSupplier;
        this.refreshAll = refreshAll;
    }

    /** Picks an Obsidian vault folder and imports its notes in the background. */
    void importObsidianVault() {
        if (importService == null) {
            return;
        }
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle(getString("dialog.import_obsidian.title"));
        File dir = chooser.showDialog(window());
        if (dir == null) {
            return;
        }
        runImport(() -> importService.importObsidianVault(dir.toPath()));
    }

    /** Picks an Evernote .enex export and imports its notes in the background. */
    void importEnex() {
        if (importService == null) {
            return;
        }
        FileChooser chooser = new FileChooser();
        chooser.setTitle(getString("dialog.import_enex.title"));
        chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Evernote export (*.enex)", "*.enex"));
        File file = chooser.showOpenDialog(window());
        if (file == null) {
            return;
        }
        runImport(() -> importService.importEnex(file.toPath()));
    }

    /** Runs an import off the FX thread, then reports the summary and refreshes views. */
    private void runImport(Supplier<ImportService.ImportResult> work) {
        updateStatus(getString("status.import_running"));
        Task<ImportService.ImportResult> task = new Task<>() {
            @Override
            protected ImportService.ImportResult call() {
                return work.get();
            }
        };
        task.setOnSucceeded(e -> {
            ImportService.ImportResult result = task.getValue();
            updateStatus(MessageFormat.format(getString("status.import_done"),
                    result.notesImported(), result.errors().size()));
            if (!result.errors().isEmpty()) {
                showErrors(result);
            }
            if (refreshAll != null) {
                refreshAll.run();
            }
        });
        task.setOnFailed(e -> {
            logger.log(Level.WARNING, "Import failed", task.getException());
            updateStatus(getString("status.import_failed"));
        });
        Thread thread = new Thread(task, "import");
        thread.setDaemon(true);
        thread.start();
    }

    private void showErrors(ImportService.ImportResult result) {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle(getString("dialog.import_errors.title"));
        alert.setHeaderText(MessageFormat.format(getString("dialog.import_errors.header"),
                result.errors().size()));
        // Show at most the first 20 errors to keep the dialog readable.
        StringBuilder sb = new StringBuilder();
        result.errors().stream().limit(20).forEach(err -> sb.append("• ").append(err).append('\n'));
        if (result.errors().size() > 20) {
            sb.append('…');
        }
        alert.setContentText(sb.toString());
        alert.setResizable(true);
        com.example.jylos.ui.UiDialogs.show(alert);
    }

    private Window window() {
        return windowSupplier != null ? windowSupplier.get() : null;
    }

    private String getString(String key) {
        return i18n != null ? i18n.apply(key) : key;
    }

    private void updateStatus(String message) {
        if (status != null) {
            status.accept(message);
        }
    }
}
