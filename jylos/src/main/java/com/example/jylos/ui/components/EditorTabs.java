package com.example.jylos.ui.components;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.example.jylos.ui.UiNodes;
import com.example.jylos.ui.components.EditorTabsModel.OpenTarget;

import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;

/**
 * A lightweight, presentational tab strip for the editor: one tab per open note,
 * each showing a dirty dot, the note title and a close button.
 *
 * <p>This component is intentionally "dumb": which tabs exist and in which order is
 * decided by {@link EditorTabsModel} (VS Code rules: a plain open replaces the active tab,
 * an open note is never duplicated, "open in new tab" inserts after the active one). It
 * reports user gestures through a {@link Listener}; it does <b>not</b> decide which note
 * is loaded — {@link com.example.jylos.ui.controller.MainController} remains the single
 * source of truth and calls {@link #open} to keep the strip in sync with the editor.</p>
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

        /** A tab's close button (or the tab itself, with the middle button) was clicked. */
        void onClose(String noteId);
    }

    /** Every tab is rendered at this fixed width so titles never shrink away. */
    private static final double TAB_WIDTH = 170;

    private final HBox container;
    /** Node toggled for show/hide (typically the wrapping ScrollPane); may equal the container. */
    private final Region visibilityNode;
    private final Listener listener;
    /** i18n lookup for the close tooltip (nullable; falls back to English). */
    private final java.util.function.Function<String, String> i18n;
    private final EditorTabsModel model = new EditorTabsModel();
    private final Map<String, TabNode> nodes = new HashMap<>();

    public EditorTabs(HBox container, Region visibilityNode, Listener listener,
            java.util.function.Function<String, String> i18n) {
        this.container = container;
        this.visibilityNode = visibilityNode != null ? visibilityNode : container;
        this.listener = listener;
        this.i18n = i18n;
        if (container != null) {
            container.getStyleClass().add("editor-tab-bar");
        }
        updateVisibility();
    }

    /** A single rendered tab, always {@link #TAB_WIDTH} wide with an ellipsised title. */
    private final class TabNode {
        final HBox root = new HBox();
        final Label dot = new Label("●");
        final Label title = new Label();
        String noteId;

        TabNode(String noteId, String titleText) {
            this.noteId = noteId;
            root.getStyleClass().add("editor-tab");
            root.setSpacing(6);
            root.setAlignment(Pos.CENTER_LEFT);
            // Fixed width: tabs never compress; the strip scrolls horizontally instead.
            root.setMinWidth(TAB_WIDTH);
            root.setPrefWidth(TAB_WIDTH);
            root.setMaxWidth(TAB_WIDTH);

            dot.getStyleClass().add("editor-tab-dot");
            UiNodes.show(dot, false); // clean by default

            title.getStyleClass().add("editor-tab-title");
            title.setText(displayTitle(titleText));
            // Title takes the remaining space and ellipsises rather than widening the tab.
            title.setMaxWidth(Double.MAX_VALUE);
            HBox.setHgrow(title, Priority.ALWAYS);

            Label close = new Label("×");
            close.getStyleClass().add("editor-tab-close");
            String closeText = i18n != null ? i18n.apply("tooltip.close_note") : null;
            close.setTooltip(new Tooltip(closeText != null ? closeText : "Close note"));
            close.setOnMouseClicked(e -> {
                e.consume();
                if (listener != null) {
                    listener.onClose(this.noteId);
                }
            });

            root.getChildren().addAll(dot, title, close);
            root.setOnMouseClicked(e -> {
                if (listener == null) {
                    return;
                }
                if (e.getButton() == MouseButton.MIDDLE) {
                    listener.onClose(this.noteId);
                } else if (e.getButton() == MouseButton.PRIMARY && !this.noteId.equals(model.getActiveId())) {
                    listener.onSelect(this.noteId);
                }
            });
        }
    }

    // ------------------------------------------------------------------
    // Queries
    // ------------------------------------------------------------------

    public boolean isOpen(String id) {
        return model.isOpen(id);
    }

    /** Open tab note ids in display order. */
    public List<String> getOpenIds() {
        return model.getIds();
    }

    public boolean isEmpty() {
        return model.getIds().isEmpty();
    }

    public String getActiveId() {
        return model.getActiveId();
    }

    /**
     * Returns the id of the tab that should become active after {@code id} is closed
     * (the previous tab, or the next one, or {@code null} if it was the only tab).
     */
    public String neighborOf(String id) {
        return model.neighborOf(id);
    }

    // ------------------------------------------------------------------
    // Mutations (called by MainController)
    // ------------------------------------------------------------------

    /**
     * Shows {@code id} as the active tab, opening it where {@code target} says (see
     * {@link EditorTabsModel}). No callback is fired. No-op for a {@code null} id.
     */
    public void open(String id, String title, OpenTarget target) {
        if (id == null) {
            return;
        }
        model.open(id, target);
        TabNode node = nodes.get(id);
        if (node == null) {
            nodes.put(id, new TabNode(id, title));
        } else {
            node.title.setText(displayTitle(title));
        }
        render();
    }

    /** "Always open notes in a new tab" preference. */
    public void setAlwaysNewTab(boolean alwaysNewTab) {
        model.setAlwaysNewTab(alwaysNewTab);
    }

    /** Removes the tab for {@code id} (if present). */
    public void removeTab(String id) {
        model.remove(id);
        render();
    }

    /** Shows/hides the dirty dot for a tab. */
    public void setDirty(String id, boolean dirty) {
        TabNode node = nodes.get(id);
        if (node != null) {
            UiNodes.show(node.dot, dirty);
        }
    }

    /** Updates a tab's displayed title (e.g. after a rename/save). */
    public void setTitle(String id, String title) {
        TabNode node = nodes.get(id);
        if (node != null) {
            node.title.setText(displayTitle(title));
        }
    }

    /**
     * Rebinds a tab from {@code oldId} to {@code newId}, preserving its visual order and
     * active state. Used when a filesystem-backed note is renamed and its path-based id changes.
     */
    public void rebindId(String oldId, String newId, String title) {
        if (oldId == null || newId == null) {
            return;
        }
        TabNode node = nodes.get(oldId);
        if (!oldId.equals(newId) && node != null && !nodes.containsKey(newId)) {
            nodes.remove(oldId);
            node.noteId = newId;
            nodes.put(newId, node);
        }
        model.rebind(oldId, newId);
        setTitle(newId, title);
        render();
    }

    /** Closes all tabs. */
    public void clear() {
        model.clear();
        render();
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    /** Lays the tab nodes out in the model's order, marks the active one and drops closed ones. */
    private void render() {
        List<String> ids = model.getIds();
        nodes.keySet().retainAll(ids);
        if (container != null) {
            container.getChildren().setAll(ids.stream().map(id -> (Node) nodes.get(id).root).toList());
        }
        String activeId = model.getActiveId();
        nodes.forEach((id, node) -> {
            node.root.getStyleClass().remove("active");
            if (id.equals(activeId)) {
                node.root.getStyleClass().add("active");
            }
        });
        updateVisibility();
    }

    private void updateVisibility() {
        if (visibilityNode != null) {
            boolean show = !model.getIds().isEmpty();
            UiNodes.show(visibilityNode, show);
        }
    }

    private static String displayTitle(String title) {
        return (title != null && !title.isBlank()) ? title : "(untitled)";
    }

}
