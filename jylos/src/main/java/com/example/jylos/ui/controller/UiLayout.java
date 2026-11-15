package com.example.jylos.ui.controller;

import com.example.jylos.data.models.Note;
import com.example.jylos.ui.UiNodes;
import javafx.scene.control.SplitPane;
import javafx.scene.control.ToggleButton;
import javafx.scene.layout.VBox;

/**
 * Encapsulates editor/preview layout modes and side-panel visibility changes.
 *
 * <p>The shell controller owns the FXML nodes; this helper owns only the repeated
 * layout mechanics so panel toggles stay consistent across startup and toolbar
 * commands.</p>
 *
 * @author Edu Díaz (RGiskard7)
 * @since 2.3.0
 */
class UiLayout {

    /** Editor surface arrangement selected by the shell. */
    public enum ViewMode {
        EDITOR_ONLY,
        SPLIT,
        PREVIEW_ONLY
    }

    /**
     * Applies one of the three editor surface arrangements.
     */
    public void applyViewMode(
            ViewMode mode,
            SplitPane editorPreviewSplitPane,
            VBox editorPane,
            VBox previewPane,
            Runnable updatePreviewAction) {
        if (editorPreviewSplitPane == null || editorPane == null || previewPane == null || mode == null) {
            return;
        }

        switch (mode) {
            case EDITOR_ONLY:
                UiNodes.show(editorPane, true);
                UiNodes.show(previewPane, false);
                if (editorPreviewSplitPane.getItems().contains(previewPane)) {
                    editorPreviewSplitPane.getItems().remove(previewPane);
                }
                if (!editorPreviewSplitPane.getItems().contains(editorPane)) {
                    editorPreviewSplitPane.getItems().add(editorPane);
                }
                break;
            case PREVIEW_ONLY:
                UiNodes.show(editorPane, false);
                UiNodes.show(previewPane, true);
                if (editorPreviewSplitPane.getItems().contains(editorPane)) {
                    editorPreviewSplitPane.getItems().remove(editorPane);
                }
                if (!editorPreviewSplitPane.getItems().contains(previewPane)) {
                    editorPreviewSplitPane.getItems().add(previewPane);
                }
                if (updatePreviewAction != null) {
                    updatePreviewAction.run();
                }
                break;
            case SPLIT:
            default:
                UiNodes.show(editorPane, true);
                UiNodes.show(previewPane, true);
                editorPreviewSplitPane.getItems().clear();
                editorPreviewSplitPane.getItems().addAll(editorPane, previewPane);
                editorPreviewSplitPane.setDividerPositions(0.5);
                if (updatePreviewAction != null) {
                    updatePreviewAction.run();
                }
                break;
        }

    }

    /**
     * Opens or collapses the right panel without recreating it, preserving plugin
     * panels and backlinks state between toggles. {@code panelSplitPane} is the
     * SplitPane the panel is a direct child of — a sibling of the center content
     * (editor/graph/Kanban), not nested inside any one of them, so the panel stays
     * reachable no matter which center view is active.
     */
    public void toggleRightPanel(SplitPane panelSplitPane, VBox rightPanel, ToggleButton toggleButton, Note currentNote,
            Runnable updateNoteInfoPanelAction) {
        if (rightPanel == null) {
            return;
        }

        boolean isCollapsed = !rightPanel.isManaged() || !rightPanel.isVisible() || rightPanel.getPrefWidth() < 10;
        if (isCollapsed) {
            UiNodes.show(rightPanel, true);
            rightPanel.setMinWidth(240);
            rightPanel.setMaxWidth(Double.MAX_VALUE);
            rightPanel.setPrefWidth(300);
            if (panelSplitPane != null) {
                panelSplitPane.setDividerPositions(0.8);
            }
        } else {
            rightPanel.setMinWidth(0);
            rightPanel.setMaxWidth(0);
            rightPanel.setPrefWidth(0);
        }

        boolean isVisible = isCollapsed;
        if (toggleButton != null) {
            toggleButton.setSelected(isVisible);
        }

        if (isVisible && currentNote != null && updateNoteInfoPanelAction != null) {
            updateNoteInfoPanelAction.run();
        }
    }

}
