package com.example.jylos.ui.controller;

import java.util.Optional;
import java.util.function.Function;

import com.example.jylos.service.FileFingerprint;
import com.example.jylos.ui.UiDialogs;

import javafx.scene.control.Alert;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;

/**
 * Guards a save against overwriting a note file that changed on disk after it was opened
 * (edited in another app, synced from another device, updated by {@code git pull}).
 * A manual save asks what to do; an autosave never overwrites and pauses instead.
 */
final class SaveConflictSupport {

    enum Decision {
        /** Nothing changed on disk (or there is nothing to compare): save normally. */
        PROCEED,
        /** The file changed on disk and the user asked to save: ask what to do. */
        CONFLICT_PROMPT,
        /** The file changed on disk during an autosave: do not save, tell the user. */
        CONFLICT_PAUSE
    }

    enum Resolution {
        /** Write the editor's version over the file. */
        OVERWRITE,
        /** Discard the editor's changes and load what is on disk. */
        RELOAD,
        /** Keep the file as is, save the editor's version as a separate conflict copy. */
        SAVE_COPY,
        /** Do nothing for now. */
        CANCEL
    }

    private Function<String, String> i18n = key -> key;

    void wire(Function<String, String> i18n) {
        this.i18n = i18n != null ? i18n : key -> key;
    }

    /**
     * Pure decision. {@code baseline} is the file as it was when the editor loaded or last
     * saved the note; {@code current} is the file now. A missing baseline (SQLite, no file)
     * or a file deleted on disk has nothing to lose, so the save proceeds.
     */
    static Decision decide(FileFingerprint baseline, Optional<FileFingerprint> current, boolean autosave) {
        if (baseline == null || current == null || current.isEmpty()) {
            return Decision.PROCEED;
        }
        // Content decides: a file merely touched (e.g. by a sync client) is not a conflict.
        if (baseline.sameContentAs(current.get())) {
            return Decision.PROCEED;
        }
        return autosave ? Decision.CONFLICT_PAUSE : Decision.CONFLICT_PROMPT;
    }

    /** Asks the user how to resolve a conflict on the note titled {@code noteTitle}. */
    Resolution askResolution(String noteTitle) {
        ButtonType overwrite = new ButtonType(i18n.apply("dialog.save_conflict.overwrite"), ButtonBar.ButtonData.OTHER);
        ButtonType reload = new ButtonType(i18n.apply("dialog.save_conflict.reload"), ButtonBar.ButtonData.OTHER);
        ButtonType saveCopy = new ButtonType(i18n.apply("dialog.save_conflict.save_copy"), ButtonBar.ButtonData.OTHER);
        ButtonType cancel = new ButtonType(i18n.apply("action.cancel"), ButtonBar.ButtonData.CANCEL_CLOSE);

        Alert alert = new Alert(Alert.AlertType.WARNING, "", overwrite, reload, saveCopy, cancel);
        alert.setTitle(i18n.apply("dialog.save_conflict.title"));
        alert.setHeaderText(java.text.MessageFormat.format(i18n.apply("dialog.save_conflict.header"), noteTitle));
        alert.setContentText(i18n.apply("dialog.save_conflict.content"));
        Optional<ButtonType> answer = UiDialogs.show(alert);
        if (answer.isEmpty() || answer.get() == cancel) {
            return Resolution.CANCEL;
        }
        if (answer.get() == overwrite) {
            return Resolution.OVERWRITE;
        }
        if (answer.get() == reload) {
            return Resolution.RELOAD;
        }
        return Resolution.SAVE_COPY;
    }
}
