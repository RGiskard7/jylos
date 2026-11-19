package com.example.jylos.ui.controller;

import java.nio.file.Path;
import java.text.MessageFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.function.BiPredicate;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.example.jylos.config.LoggerConfig;
import com.example.jylos.event.events.SystemActionEvent.ActionType;
import com.example.jylos.service.NoteService;
import com.example.jylos.ui.UiDialogs;

import javafx.application.Platform;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.stage.Window;

/**
 * The "Clean up Jylos metadata" command, for folder vaults: takes out of every note the fields
 * older versions of Jylos wrote in it ({@code id}, {@code title}, {@code modified},
 * {@code favorite}, {@code pinned}, {@code deleted}), which Obsidian shows as properties. It
 * says first how many notes it would touch and asks; then it copies each one to
 * {@code .jylos/backups/cleanup-<date>/}, at the same path, and cleans it. Notes are cleaned
 * anyway when they are saved; this does the whole vault at once.
 *
 * <p>Meant to be retired in a later major version, once old vaults have been cleaned.</p>
 *
 * @author Edu Díaz (RGiskard7)
 * @since 2.6.0
 */
final class VaultCleanupSupport {

    private static final Logger logger = LoggerConfig.getLogger(VaultCleanupSupport.class);
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private Supplier<NoteService> notes = () -> null;
    private Function<String, String> i18n = key -> key;
    private Consumer<String> status = message -> {
    };
    private Supplier<Window> owner = () -> null;
    private Executor background = task -> new Thread(task, "vault-cleanup").start();
    private Consumer<Runnable> fx = Platform::runLater;
    private BiPredicate<Integer, Path> confirm = this::askToConfirm;

    /** Answers the command, reaching the notes of the vault and the window through these. */
    void register(Map<ActionType, Runnable> handlers, Supplier<NoteService> notes, Function<String, String> i18n,
            Consumer<String> status, Supplier<Window> owner) {
        this.notes = notes;
        this.i18n = i18n;
        this.status = status;
        this.owner = owner;
        handlers.put(ActionType.VAULT_CLEANUP, this::run);
    }

    /** Runs the work and the window's part where given, and answers the question with {@code confirm}. */
    void useForTests(Executor background, Consumer<Runnable> fx, BiPredicate<Integer, Path> confirm) {
        this.background = background;
        this.fx = fx;
        this.confirm = confirm;
    }

    /** Finds the notes to clean (reading only), asks, and cleans them after copying them. */
    void run() {
        NoteService vault = notes.get();
        Optional<Path> vaultRoot = vault != null ? vault.getStorageRoot() : Optional.empty();
        if (vaultRoot.isEmpty()) {
            status.accept(getString("cleanup.status.only_vault"));
            return;
        }
        status.accept(getString("cleanup.status.looking"));
        background.execute(() -> {
            List<String> found;
            try {
                found = vault.notesWithJylosFields();
            } catch (RuntimeException e) {
                failed(e);
                return;
            }
            fx.accept(() -> {
                if (found.isEmpty()) {
                    status.accept(getString("cleanup.status.none"));
                    return;
                }
                Path backups = vaultRoot.get().resolve(".jylos").resolve("backups")
                        .resolve("cleanup-" + LocalDateTime.now().format(STAMP));
                if (!confirm.test(found.size(), backups)) {
                    status.accept(getString("cleanup.status.cancelled"));
                    return;
                }
                background.execute(() -> {
                    try {
                        int cleaned = vault.cleanJylosFields(found, backups);
                        fx.accept(() -> status.accept(MessageFormat.format(getString("cleanup.status.done"),
                                cleaned, vaultRoot.get().relativize(backups))));
                    } catch (RuntimeException e) {
                        failed(e);
                    }
                });
            });
        });
    }

    private void failed(RuntimeException e) {
        logger.log(Level.WARNING, "Could not clean up the vault", e);
        fx.accept(() -> status.accept(MessageFormat.format(getString("cleanup.status.failed"), e.getMessage())));
    }

    private boolean askToConfirm(int count, Path backups) {
        Optional<Path> root = notes.get() != null ? notes.get().getStorageRoot() : Optional.empty();
        ButtonType clean = new ButtonType(getString("cleanup.confirm.ok"), ButtonBar.ButtonData.OK_DONE);
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION,
                MessageFormat.format(getString("cleanup.confirm.content"), count,
                        root.map(vaultRoot -> vaultRoot.relativize(backups)).orElse(backups)),
                clean, ButtonType.CANCEL);
        alert.setTitle(getString("cleanup.confirm.title"));
        alert.setHeaderText(MessageFormat.format(getString("cleanup.confirm.header"), count));
        Window window = owner.get();
        if (window != null) {
            alert.initOwner(window);
        }
        UiDialogs.apply(alert);
        return alert.showAndWait().filter(clean::equals).isPresent();
    }

    private String getString(String key) {
        return i18n.apply(key);
    }
}
