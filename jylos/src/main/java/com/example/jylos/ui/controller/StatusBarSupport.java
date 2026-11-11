package com.example.jylos.ui.controller;

import java.io.File;
import java.text.MessageFormat;
import java.util.EnumMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.prefs.Preferences;

import com.example.jylos.data.models.Note;

import javafx.geometry.Side;
import javafx.scene.Node;
import javafx.scene.control.CheckMenuItem;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;

/**
 * Status-bar presentation: the live word/character counts for the open note and the
 * storage indicator (vault folder vs. SQLite database). Extracted from
 * {@code MainController} as a small, cohesive presentation helper.
 *
 * @author Edu Díaz (RGiskard7)
 * @since 2.0.0
 */
final class StatusBarSupport {

    /**
     * Shows {@code message} in the status bar's message area. With no message the separator
     * in front of it is hidden too, so the bar does not end in a dangling "|". A long message
     * (the label truncates it when narrow) gets its full text as a tooltip.
     */
    static void showMessage(javafx.scene.control.Label label, javafx.scene.control.Separator separator,
            String message) {
        String text = message != null ? message : "";
        label.setText(text);
        label.setTooltip(text.length() > 40 ? new javafx.scene.control.Tooltip(text) : null);
        if (separator != null) {
            boolean shown = !text.isBlank();
            separator.setVisible(shown);
            separator.setManaged(shown);
        }
    }

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

    /** What the editor shows: the four modes a user can pick from the status bar. */
    enum EditorMode {
        LIVE_PREVIEW("status.mode.live_preview"),
        SOURCE("status.mode.source"),
        READING("status.mode.reading"),
        SPLIT("status.mode.split");

        final String labelKey;

        EditorMode(String labelKey) {
            this.labelKey = labelKey;
        }

        /** Reading and split render the note, so the editing preference only matters in the editor. */
        static EditorMode of(UiLayout.ViewMode viewMode, boolean livePreview) {
            if (viewMode == UiLayout.ViewMode.PREVIEW_ONLY) {
                return READING;
            }
            if (viewMode == UiLayout.ViewMode.SPLIT) {
                return SPLIT;
            }
            return livePreview ? LIVE_PREVIEW : SOURCE;
        }
    }

    private Label saveStateLabel;
    private Label editorModeLabel;
    private Node editorIndicatorsGroup;
    private final ContextMenu editorModeMenu = new ContextMenu();
    private final Map<EditorMode, CheckMenuItem> editorModeItems = new EnumMap<>(EditorMode.class);

    /**
     * Wires the save-state and editor-mode labels. Both stay hidden until a note is open;
     * clicking the mode label opens a menu that reports the picked mode to {@code onModeChosen}.
     */
    void wireEditorIndicators(Label saveStateLabel, Label editorModeLabel, Node group,
            Consumer<EditorMode> onModeChosen) {
        this.saveStateLabel = saveStateLabel;
        this.editorModeLabel = editorModeLabel;
        this.editorIndicatorsGroup = group;
        editorModeMenu.getItems().clear();
        editorModeItems.clear();
        for (EditorMode mode : EditorMode.values()) {
            CheckMenuItem item = new CheckMenuItem(getString(mode.labelKey));
            item.setOnAction(e -> {
                if (onModeChosen != null) {
                    onModeChosen.accept(mode);
                }
            });
            editorModeItems.put(mode, item);
            editorModeMenu.getItems().add(item);
        }
        if (editorModeLabel != null) {
            editorModeLabel.setTooltip(new Tooltip(getString("status.mode.tooltip")));
            editorModeLabel.setOnMouseClicked(e -> editorModeMenu.show(editorModeLabel, Side.TOP, 0, 0));
        }
        if (saveStateLabel != null) {
            saveStateLabel.setTooltip(new Tooltip());
        }
        setShown(saveStateLabel, false);
        setShown(editorModeLabel, false);
        setShown(group, false);
    }

    /** Names the save state ("Saved"/"Unsaved", with the autosave rule as tooltip) and the editor mode. */
    void updateEditorIndicators(boolean noteOpen, boolean modified, EditorMode mode, boolean autosaveEnabled,
            int autosaveIdleMs) {
        setShown(saveStateLabel, noteOpen);
        setShown(editorModeLabel, noteOpen);
        setShown(editorIndicatorsGroup, noteOpen);
        if (!noteOpen) {
            return;
        }
        if (saveStateLabel != null) {
            saveStateLabel.setText(getString(modified ? "status.save.unsaved" : "status.save.saved"));
            saveStateLabel.getStyleClass().removeAll("status-save-dirty", "status-save-clean");
            saveStateLabel.getStyleClass().add(modified ? "status-save-dirty" : "status-save-clean");
            if (saveStateLabel.getTooltip() != null) {
                saveStateLabel.getTooltip().setText(autosaveEnabled
                        ? MessageFormat.format(getString("status.save.autosave_on"), autosaveIdleMs / 1000.0)
                        : getString("status.save.autosave_off"));
            }
        }
        if (editorModeLabel != null && mode != null) {
            editorModeLabel.setText(getString(mode.labelKey));
        }
        editorModeItems.forEach((itemMode, item) -> item.setSelected(itemMode == mode));
    }

    /** The menu the mode label opens (package-private for tests). */
    ContextMenu editorModeMenu() {
        return editorModeMenu;
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
