package com.example.jylos.ui.controller;

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
import com.example.jylos.data.models.Note;
import com.example.jylos.event.events.SystemActionEvent.ActionType;
import com.example.jylos.service.NoteService;
import com.example.jylos.service.ObsidianBookmarks;
import com.example.jylos.ui.UiDialogs;

import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.stage.Window;

/**
 * Tools > "Import Obsidian Bookmarks", for a folder vault Obsidian also opens: the notes
 * bookmarked in Obsidian ({@link ObsidianBookmarks}) that exist in the vault become favorites.
 * Obsidian's file is only read. Nothing is asked: it only adds favorites, which are undone one
 * by one. Then it says, in a dialog, how many it marked, how many already were, how many it did
 * not find and how many bookmarks are of kinds Jylos has no favorite for.
 *
 * @author Edu Díaz (RGiskard7)
 * @since 2.6.0
 */
final class ObsidianBookmarksSupport {

    private static final Logger logger = LoggerConfig.getLogger(ObsidianBookmarksSupport.class);

    private Supplier<NoteService> notes = () -> null;
    private Function<String, String> i18n = key -> key;
    private Consumer<String> status = message -> {
    };
    private Runnable refresh = () -> {
    };
    private Supplier<Window> owner = () -> null;
    private Consumer<String> report = this::showResult;

    /** Answers the command; {@code refresh} shows the new favorites. */
    void register(Map<ActionType, Runnable> handlers, Supplier<NoteService> notes, Function<String, String> i18n,
            Consumer<String> status, Runnable refresh, Supplier<Window> owner) {
        this.notes = notes;
        this.i18n = i18n;
        this.status = status;
        this.refresh = refresh;
        this.owner = owner;
        handlers.put(ActionType.IMPORT_OBSIDIAN_BOOKMARKS, this::run);
    }

    /** Shows the result with {@code report} instead of a dialog. */
    void reportWith(Consumer<String> report) {
        this.report = report;
    }

    void run() {
        NoteService vault = notes.get();
        Optional<Path> root = vault != null ? vault.getStorageRoot() : Optional.empty();
        if (root.isEmpty()) {
            status.accept(i18n.apply("bookmarks.status.only_vault"));
            return;
        }
        Optional<ObsidianBookmarks.Found> found;
        try {
            found = ObsidianBookmarks.read(root.get());
        } catch (IllegalArgumentException e) {
            logger.log(Level.WARNING, "Obsidian's bookmarks were not understood", e);
            status.accept(i18n.apply("bookmarks.status.not_understood"));
            return;
        }
        if (found.isEmpty() || found.get().files().isEmpty() && found.get().ignored() == 0) {
            status.accept(i18n.apply("bookmarks.status.none"));
            return;
        }
        int marked = 0;
        int already = 0;
        int missing = 0;
        for (String path : found.get().files()) {
            Optional<Note> note = vault.getNoteById(path);
            if (note.isEmpty()) {
                missing++;
            } else if (note.get().isFavorite()) {
                already++;
            } else {
                vault.toggleFavorite(note.get());
                marked++;
            }
        }
        // Refreshed first, and the result in a dialog: refreshing the notes list writes its own
        // message in the status bar, which would hide this one.
        refresh.run();
        report.accept(MessageFormat.format(i18n.apply("bookmarks.status.done"), marked, already, missing,
                found.get().ignored()));
    }

    private void showResult(String result) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION, result, ButtonType.OK);
        alert.setTitle(i18n.apply("action.import_obsidian_bookmarks"));
        alert.setHeaderText(null);
        Window window = owner.get();
        if (window != null) {
            alert.initOwner(window);
        }
        UiDialogs.apply(alert);
        alert.showAndWait();
    }
}
