package com.example.jylos.ui.controller;

import java.io.File;
import java.text.MessageFormat;
import java.util.function.Function;
import java.util.prefs.Preferences;

import com.example.jylos.data.models.Note;

import javafx.scene.Node;
import javafx.scene.control.Label;

/**
 * Status-bar presentation: the live word/character counts for the open note and the
 * storage indicator (vault folder vs. SQLite database). Extracted from
 * {@code MainController} as a small, cohesive presentation helper.
 *
 * @author Edu Díaz (RGiskard7)
 * @since 2.0.0
 */
final class StatusBarSupport {

    private Label storageLabel;
    private Label wordCountLabel;
    private Label charCountLabel;
    private Node statsSeparator;
    private Node wordCharSeparator;
    private Preferences prefs;
    private Function<String, String> i18n;

    void wire(Label storageLabel, Label wordCountLabel, Label charCountLabel, Node statsSeparator,
            Node wordCharSeparator, Preferences prefs, Function<String, String> i18n) {
        this.storageLabel = storageLabel;
        this.wordCountLabel = wordCountLabel;
        this.charCountLabel = charCountLabel;
        this.statsSeparator = statsSeparator;
        this.wordCharSeparator = wordCharSeparator;
        this.prefs = prefs;
        this.i18n = i18n;
    }

    /** Updates the status-bar word/character counts for the current note (or hides them). */
    void updateNoteStats(Note note) {
        boolean show = note != null;
        if (show) {
            String content = note.getContent() != null ? note.getContent() : "";
            int words = content.isBlank() ? 0 : content.trim().split("\\s+").length;
            if (wordCountLabel != null) {
                wordCountLabel.setText(MessageFormat.format(getString("status.words"), words));
            }
            if (charCountLabel != null) {
                charCountLabel.setText(MessageFormat.format(getString("status.chars"), content.length()));
            }
        }
        setShown(statsSeparator, show);
        setShown(wordCountLabel, show);
        setShown(wordCharSeparator, show);
        setShown(charCountLabel, show);
    }

    /** Reflects the active storage backend (vault folder vs. SQLite database). */
    void updateStorageLabel() {
        if (storageLabel == null) {
            return;
        }
        String type = prefs.get("storage_type", System.getProperty("jylos.storage", "sqlite"));
        if ("filesystem".equalsIgnoreCase(type)) {
            String path = prefs.get("filesystem_path", "");
            String name = path.isBlank() ? getString("storage.vault") : new File(path).getName();
            storageLabel.setText(MessageFormat.format(getString("storage.vault_named"), name));
        } else {
            storageLabel.setText(getString("storage.database"));
        }
    }

    private static void setShown(Node node, boolean visible) {
        if (node != null) {
            node.setVisible(visible);
            node.setManaged(visible);
        }
    }

    private String getString(String key) {
        return i18n != null ? i18n.apply(key) : key;
    }
}
