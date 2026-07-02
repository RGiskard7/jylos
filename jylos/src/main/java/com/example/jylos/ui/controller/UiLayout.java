package com.example.jylos.ui.controller;

import com.example.jylos.data.models.Note;
import javafx.scene.control.SplitPane;
import javafx.scene.control.ToggleButton;
import javafx.scene.layout.VBox;

/**
 * Encapsulates editor/preview layout modes and right-panel visibility logic.
 */
class UiLayout {

    public enum ViewMode {
        EDITOR_ONLY,
        SPLIT,
        PREVIEW_ONLY
    }

    public void applyViewMode(
            ViewMode mode,
            SplitPane editorPreviewSplitPane,
            VBox editorPane,
            VBox previewPane,
            ToggleButton editorOnlyButton,
            ToggleButton splitViewButton,
            ToggleButton previewOnlyButton,
            Runnable updatePreviewAction) {
        if (editorPreviewSplitPane == null || editorPane == null || previewPane == null || mode == null) {
            return;
        }

        switch (mode) {
            case EDITOR_ONLY:
                editorPane.setVisible(true);
                editorPane.setManaged(true);
                previewPane.setVisible(false);
                previewPane.setManaged(false);
                if (editorPreviewSplitPane.getItems().contains(previewPane)) {
                    editorPreviewSplitPane.getItems().remove(previewPane);
                }
                if (!editorPreviewSplitPane.getItems().contains(editorPane)) {
                    editorPreviewSplitPane.getItems().add(editorPane);
                }
                break;
            case PREVIEW_ONLY:
                editorPane.setVisible(false);
                editorPane.setManaged(false);
                previewPane.setVisible(true);
                previewPane.setManaged(true);
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
                editorPane.setVisible(true);
                editorPane.setManaged(true);
                previewPane.setVisible(true);
                previewPane.setManaged(true);
                editorPreviewSplitPane.getItems().clear();
                editorPreviewSplitPane.getItems().addAll(editorPane, previewPane);
                editorPreviewSplitPane.setDividerPositions(0.5);
                if (updatePreviewAction != null) {
                    updatePreviewAction.run();
                }
                break;
        }

        if (editorOnlyButton != null) {
            editorOnlyButton.setSelected(mode == ViewMode.EDITOR_ONLY);
        }
        if (splitViewButton != null) {
            splitViewButton.setSelected(mode == ViewMode.SPLIT);
        }
        if (previewOnlyButton != null) {
            previewOnlyButton.setSelected(mode == ViewMode.PREVIEW_ONLY);
        }
    }

    public void toggleRightPanel(VBox rightPanel, ToggleButton infoButton, Note currentNote,
            Runnable updateNoteInfoPanelAction) {
        if (rightPanel == null) {
            return;
        }

        boolean nextVisible = !rightPanel.isVisible();
        rightPanel.setVisible(nextVisible);
        rightPanel.setManaged(nextVisible);

        if (infoButton != null) {
            infoButton.setSelected(nextVisible);
        }

        if (nextVisible) {
            rightPanel.setMinWidth(260);
            rightPanel.setMaxWidth(340);
            rightPanel.setPrefWidth(300);
        } else {
            rightPanel.setMinWidth(0);
            rightPanel.setMaxWidth(0);
            rightPanel.setPrefWidth(0);
        }

        if (nextVisible && currentNote != null && updateNoteInfoPanelAction != null) {
            updateNoteInfoPanelAction.run();
        }
    }

    public void closeRightPanel(VBox rightPanel, ToggleButton infoButton) {
        if (rightPanel == null) {
            return;
        }
        rightPanel.setVisible(false);
        rightPanel.setManaged(false);
        if (infoButton != null) {
            infoButton.setSelected(false);
        }
    }
}
