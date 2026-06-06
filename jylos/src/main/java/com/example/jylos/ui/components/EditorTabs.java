package com.example.jylos.ui.components;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;

/**
 * A lightweight, presentational tab strip for the editor: one tab per open note,
 * each showing a dirty dot, the note title and a close button.
 *
 * <p>This component is intentionally "dumb": it renders tabs and reports user
 * gestures through a {@link Listener}. It does <b>not</b> decide which note is
 * loaded — {@link com.example.jylos.ui.controller.MainController} remains the single
 * source of truth and calls {@link #setActive(String)} / {@link #ensureTab} to keep
 * the strip in sync with the editor. This avoids the open/select feedback loops the
 * event-bus contract warns about.</p>
 *
 * <p>Because the editor hosts a single note at a time, only the active tab can hold
 * unsaved edits; closing a non-active tab is therefore always safe.</p>
 *
 * @author Edu Díaz (RGiskard7)
 * @since 2.0.0
 */
public final class EditorTabs {

    /** User gestures on the strip. The implementation drives editor state in response. */
    public interface Listener {
        /** A tab body was clicked (only fired when it is not already the active tab). */
        void onSelect(String noteId);

        /** A tab's close button was clicked. */
        void onClose(String noteId);
    }

    private final HBox container;
    private final Listener listener;
    /** Insertion-ordered so tab order is stable and neighbour lookup is predictable. */
    private final Map<String, TabNode> tabs = new LinkedHashMap<>();
    private String activeId;

    public EditorTabs(HBox container, Listener listener) {
        this.container = container;
        this.listener = listener;
        if (container != null) {
            container.getStyleClass().add("editor-tab-bar");
        }
        updateVisibility();
    }

    /** A single rendered tab. */
    private final class TabNode {
        final HBox root = new HBox();
        final Label dot = new Label("●");
        final Label title = new Label();
        String noteId;

        TabNode(String noteId, String titleText) {
            this.noteId = noteId;
            root.getStyleClass().add("editor-tab");
            root.setSpacing(6);

            dot.getStyleClass().add("editor-tab-dot");
            setNodeVisible(dot, false); // clean by default

            title.getStyleClass().add("editor-tab-title");
            title.setText(displayTitle(titleText));

            Label close = new Label("×");
            close.getStyleClass().add("editor-tab-close");
            close.setTooltip(new Tooltip("Close"));
            close.setOnMouseClicked(e -> {
                e.consume();
                if (listener != null) {
                    listener.onClose(this.noteId);
                }
            });

            root.getChildren().addAll(dot, title, new Spacer(), close);
            root.setOnMouseClicked(e -> {
                if (listener != null && !noteId.equals(activeId)) {
                    listener.onSelect(this.noteId);
                }
            });
        }
    }

    /** Tiny flexible gap so the close button sits at the tab's trailing edge. */
    private static final class Spacer extends Region {
        Spacer() {
            setMinWidth(4);
        }
    }

    // ------------------------------------------------------------------
    // Queries
    // ------------------------------------------------------------------

    public boolean isOpen(String id) {
        return id != null && tabs.containsKey(id);
    }

    public boolean isEmpty() {
        return tabs.isEmpty();
    }

    public String getActiveId() {
        return activeId;
    }

    /**
     * Returns the id of the tab that should become active after {@code id} is closed
     * (the previous tab, or the next one, or {@code null} if it was the only tab).
     */
    public String neighborOf(String id) {
        List<String> ids = new ArrayList<>(tabs.keySet());
        int idx = ids.indexOf(id);
        if (idx < 0) {
            return null;
        }
        if (idx > 0) {
            return ids.get(idx - 1);
        }
        return ids.size() > 1 ? ids.get(idx + 1) : null;
    }

    // ------------------------------------------------------------------
    // Mutations (called by MainController)
    // ------------------------------------------------------------------

    /** Creates a tab for {@code id} if none exists yet. No-op for a {@code null} id. */
    public void ensureTab(String id, String title) {
        if (id == null || tabs.containsKey(id)) {
            return;
        }
        TabNode node = new TabNode(id, title);
        tabs.put(id, node);
        if (container != null) {
            container.getChildren().add(node.root);
        }
        updateVisibility();
    }

    /** Marks {@code id} as the visually active tab (no callback fired). */
    public void setActive(String id) {
        this.activeId = id;
        for (Map.Entry<String, TabNode> e : tabs.entrySet()) {
            boolean active = e.getKey().equals(id);
            HBox root = e.getValue().root;
            root.getStyleClass().remove("active");
            if (active) {
                root.getStyleClass().add("active");
            }
        }
    }

    /** Removes the tab for {@code id} (if present). */
    public void removeTab(String id) {
        TabNode node = tabs.remove(id);
        if (node != null && container != null) {
            container.getChildren().remove(node.root);
        }
        if (id != null && id.equals(activeId)) {
            activeId = null;
        }
        updateVisibility();
    }

    /** Shows/hides the dirty dot for a tab. */
    public void setDirty(String id, boolean dirty) {
        TabNode node = tabs.get(id);
        if (node != null) {
            setNodeVisible(node.dot, dirty);
        }
    }

    /** Updates a tab's displayed title (e.g. after a rename/save). */
    public void setTitle(String id, String title) {
        TabNode node = tabs.get(id);
        if (node != null) {
            node.title.setText(displayTitle(title));
        }
    }

    /** Closes all tabs. */
    public void clear() {
        tabs.clear();
        activeId = null;
        if (container != null) {
            container.getChildren().clear();
        }
        updateVisibility();
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private void updateVisibility() {
        if (container != null) {
            boolean show = !tabs.isEmpty();
            container.setVisible(show);
            container.setManaged(show);
        }
    }

    private static String displayTitle(String title) {
        return (title != null && !title.isBlank()) ? title : "(untitled)";
    }

    private static void setNodeVisible(javafx.scene.Node n, boolean visible) {
        if (n != null) {
            n.setVisible(visible);
            n.setManaged(visible);
        }
    }
}
