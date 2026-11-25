package com.example.jylos.ui.controller;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.text.MessageFormat;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.example.jylos.config.LoggerConfig;
import com.example.jylos.event.events.SystemActionEvent.ActionType;
import com.example.jylos.service.ImportService;
import com.example.jylos.ui.UiDialogs;

import javafx.concurrent.Task;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.Window;

/**
 * UI flow for importing external notes (Obsidian vault folders and Evernote
 * {@code .enex} files): file pickers, the background import task, the result
 * summary and the post-import refresh. The actual import logic lives in
 * {@link ImportService}; this class follows the standard feature-support pattern.
 *
 * <p>Importing an Obsidian vault copies its notes; before doing it the user is told so and
 * offered to open that folder as the vault instead, which is what working on an Obsidian
 * vault takes. A folder that is the open vault, lies inside it or holds it is not imported.</p>
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
    private Consumer<Path> openAsVault = folder -> { };

    /** @param openAsVault opens a folder (absolute) as the vault: from the database, after a restart */
    void wire(ImportService importService, Function<String, String> i18n, Consumer<String> status,
            Supplier<Window> windowSupplier, Runnable refreshAll, Consumer<Path> openAsVault) {
        this.importService = importService;
        this.i18n = i18n;
        this.status = status;
        this.windowSupplier = windowSupplier;
        this.refreshAll = refreshAll;
        this.openAsVault = openAsVault != null ? openAsVault : folder -> { };
    }

    /** Answers the commands that import an Obsidian vault or an Evernote export. */
    void register(Map<ActionType, Runnable> handlers) {
        handlers.put(ActionType.IMPORT_OBSIDIAN, this::importObsidianVault);
        handlers.put(ActionType.IMPORT_ENEX, this::importEnex);
    }

    /** Picks an Obsidian vault folder and goes on with {@link #importObsidianVault(Path)}. */
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
        importObsidianVault(dir.toPath());
    }

    /**
     * Imports the Obsidian vault {@code folder} once the user, told that it copies the notes,
     * chooses so; or opens it as the vault, if that is what they choose. A folder overlapping
     * the open vault is refused, with the reason.
     */
    void importObsidianVault(Path folder) {
        if (importService == null || folder == null) {
            return;
        }
        Optional<Path> vault = importService.storageRoot();
        if (vault.isPresent() && overlaps(vault.get(), folder)) {
            Alert refused = new Alert(Alert.AlertType.INFORMATION);
            refused.setTitle(getString("dialog.import_obsidian.confirm.title"));
            refused.setHeaderText(getString("dialog.import_obsidian.overlap.header"));
            refused.setContentText(getString("dialog.import_obsidian.overlap.content"));
            UiDialogs.show(refused);
            return;
        }
        ButtonType importCopy = new ButtonType(getString("dialog.import_obsidian.import_copy"), ButtonBar.ButtonData.OK_DONE);
        ButtonType openIt = new ButtonType(getString("dialog.import_obsidian.open_as_vault"), ButtonBar.ButtonData.OTHER);
        ButtonType cancel = new ButtonType(getString("action.cancel"), ButtonBar.ButtonData.CANCEL_CLOSE);
        Alert ask = new Alert(Alert.AlertType.CONFIRMATION, "", importCopy, openIt, cancel);
        ask.setTitle(getString("dialog.import_obsidian.confirm.title"));
        String name = folder.getFileName() != null ? folder.getFileName().toString() : folder.toString();
        ask.setHeaderText(MessageFormat.format(getString(vault.isPresent()
                ? "dialog.import_obsidian.confirm.header_vault"
                : "dialog.import_obsidian.confirm.header_database"), name));
        ask.setContentText(getString("dialog.import_obsidian.confirm.content"));
        ask.getDialogPane().setMinWidth(480);
        Optional<ButtonType> choice = UiDialogs.show(ask);
        if (choice.isEmpty() || choice.get() == cancel) {
            return;
        }
        if (choice.get() == openIt) {
            openAsVault.accept(folder.toAbsolutePath());
            return;
        }
        runImport(() -> importService.importObsidianVault(folder));
    }

    /**
     * Whether {@code chosen} is the vault {@code vault}, lies inside it or holds it, by where
     * they really are (a link to the vault is the vault).
     */
    static boolean overlaps(Path vault, Path chosen) {
        Path a = real(vault);
        Path b = real(chosen);
        return a.startsWith(b) || b.startsWith(a);
    }

    /** Where {@code path} really is: links followed, {@code ..} resolved by name first. */
    private static Path real(Path path) {
        Path plain = path.toAbsolutePath().normalize();
        try {
            return plain.toRealPath();
        } catch (IOException | RuntimeException notThere) {
            return plain;
        }
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
            String summary = result.notesSkipped() > 0
                    ? MessageFormat.format(getString("status.import_done_skipped"),
                            result.notesImported(), result.notesSkipped(), result.errors().size())
                    : MessageFormat.format(getString("status.import_done"),
                            result.notesImported(), result.errors().size());
            updateStatus(summary);
            if (refreshAll != null) {
                refreshAll.run();
            }
            // The lists load again in the background and write the status bar after this, so
            // the outcome is told in a dialog too: an import that brought nothing new says so.
            showSummary(result, summary);
        });
        task.setOnFailed(e -> {
            logger.log(Level.WARNING, "Import failed", task.getException());
            updateStatus(getString("status.import_failed"));
        });
        Thread thread = new Thread(task, "import");
        thread.setDaemon(true);
        thread.start();
    }

    /** What the import did: how many notes came, how many were there already, and what failed. */
    private void showSummary(ImportService.ImportResult result, String summary) {
        boolean failed = !result.errors().isEmpty();
        Alert alert = new Alert(failed ? Alert.AlertType.WARNING : Alert.AlertType.INFORMATION);
        alert.setTitle(getString(failed ? "dialog.import_errors.title" : "status.import_complete"));
        alert.setHeaderText(summary);
        if (failed) {
            StringBuilder sb = new StringBuilder(MessageFormat.format(getString("dialog.import_errors.header"),
                    result.errors().size())).append('\n');
            // At most the first 20 errors, to keep the dialog readable.
            result.errors().stream().limit(20).forEach(err -> sb.append("• ").append(err).append('\n'));
            if (result.errors().size() > 20) {
                sb.append('…');
            }
            alert.setContentText(sb.toString());
            alert.setResizable(true);
        }
        UiDialogs.show(alert);
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
