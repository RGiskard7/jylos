package com.example.jylos.ui.controller;

/**
 * UI initialization blocks and editor/preview/right-panel layout logic.
 *
 * <p>Package-private shell-support types collaborating with {@link MainController}.
 * Extracted from the former single-file shell-services unit into cohesive files.</p>
 */
import com.example.jylos.data.models.Note;
import java.util.function.Consumer;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.SplitPane;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

// ===== UiInitialization =====
/**
 * Encapsulates lightweight UI initialization blocks for MainController.
 */
class UiInitialization {

    private final MainController controller;

    UiInitialization(MainController controller) {
        this.controller = controller;
    }

    private String i18n(String key) {
        return controller.getString(key);
    }

    public void initializeSortOptions(ComboBox<String> sortComboBox, Consumer<String> sorter) {
        if (sortComboBox == null) {
            return;
        }

        sortComboBox.getItems().addAll(
                i18n("sort.title_az"),
                i18n("sort.title_za"),
                i18n("sort.created_newest"),
                i18n("sort.created_oldest"),
                i18n("sort.modified_newest"),
                i18n("sort.modified_oldest"));
        sortComboBox.getSelectionModel().selectFirst();

        sortComboBox.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue) -> {
            if (sorter != null) {
                sorter.accept(newValue);
            }
        });
    }

    public void initializeViewModeButtons(
            ToggleButton editorOnlyButton,
            ToggleButton splitViewButton,
            ToggleButton previewOnlyButton,
            ToolbarController toolbarController,
            Runnable initializeGridView,
            Runnable applyViewMode) {
        ToggleGroup viewModeGroup = new ToggleGroup();
        if (editorOnlyButton != null) {
            editorOnlyButton.setToggleGroup(viewModeGroup);
        }
        if (splitViewButton != null) {
            splitViewButton.setToggleGroup(viewModeGroup);
            splitViewButton.setSelected(true);
        }
        if (previewOnlyButton != null) {
            previewOnlyButton.setToggleGroup(viewModeGroup);
        }

        ToggleGroup notesViewGroup = new ToggleGroup();
        if (toolbarController != null && toolbarController.getListViewButton() != null) {
            toolbarController.getListViewButton().setToggleGroup(notesViewGroup);
            toolbarController.getListViewButton().setSelected(true);
        }
        if (toolbarController != null && toolbarController.getGridViewButton() != null) {
            toolbarController.getGridViewButton().setToggleGroup(notesViewGroup);
        }

        if (initializeGridView != null) {
            initializeGridView.run();
        }
        if (applyViewMode != null) {
            applyViewMode.run();
        }
    }

    public void initializeRightPanelSections(HBox noteInfoHeader, VBox noteInfoContent, Label noteInfoCollapseIcon,
            VBox pluginPanelsContainer) {
        wireCollapsibleSection(noteInfoHeader, noteInfoContent, noteInfoCollapseIcon);

        if (pluginPanelsContainer != null) {
            pluginPanelsContainer.setVisible(true);
            pluginPanelsContainer.setManaged(true);
        }
    }

    /** Wires a header/content/icon trio as a click-to-collapse section. */
    public void wireCollapsibleSection(HBox header, VBox content, Label collapseIcon) {
        if (header == null || content == null || collapseIcon == null) {
            return;
        }
        header.setOnMouseClicked(e -> {
            boolean expand = !content.isVisible();
            content.setVisible(expand);
            content.setManaged(expand);
            collapseIcon.setText(expand ? "▼" : "▶");
        });
        header.setStyle("-fx-cursor: hand;");
    }

    public void setupToolbarResponsiveness(ToolbarController toolbarController, Consumer<Double> widthConsumer) {
        if (toolbarController == null || toolbarController.getToolbarHBox() == null
                || toolbarController.getToolbarOverflowBtn() == null) {
            return;
        }

        PauseTransition resizeDebounce = new PauseTransition(Duration.millis(90));
        resizeDebounce.setOnFinished(e -> {
            if (widthConsumer != null) {
                widthConsumer.accept(toolbarController.getToolbarHBox().getWidth());
            }
        });

        toolbarController.getToolbarHBox().widthProperty().addListener((obs, oldVal, newVal) -> {
            resizeDebounce.playFromStart();
        });

        Platform.runLater(() -> {
            if (widthConsumer != null) {
                widthConsumer.accept(toolbarController.getToolbarHBox().getWidth());
            }
        });
    }

    public void updateToolbarOverflow(ToolbarController toolbarController, double width) {
        if (toolbarController == null || toolbarController.getToolbarHBox() == null
                || toolbarController.getToolbarOverflowBtn() == null) {
            return;
        }

        int responsiveBucket = 0;
        if (width > 400) {
            responsiveBucket |= 1;
        }
        if (width > 550) {
            responsiveBucket |= 2;
        }
        if (width > 750) {
            responsiveBucket |= 4;
        }

        Object previousBucket = toolbarController.getToolbarOverflowBtn().getProperties().get("fn.responsive.bucket");
        if (previousBucket instanceof Integer previous && previous == responsiveBucket) {
            return;
        }
        toolbarController.getToolbarOverflowBtn().getProperties().put("fn.responsive.bucket", responsiveBucket);

        boolean showSearch = width > 750;
        boolean showFileActions = width > 550;
        boolean showLayoutToggles = width > 400;

        toolbarController.setResponsiveState(showSearch, showLayoutToggles, showFileActions);

        toolbarController.getSidebarToggleBtn().setVisible(showLayoutToggles);
        toolbarController.getSidebarToggleBtn().setManaged(showLayoutToggles);
        toolbarController.getNotesPanelToggleBtn().setVisible(showLayoutToggles);
        toolbarController.getNotesPanelToggleBtn().setManaged(showLayoutToggles);
        toolbarController.getSearchField().setVisible(showSearch);
        toolbarController.getSearchField().setManaged(showSearch);
        toolbarController.getLayoutSwitchBtn().setVisible(showLayoutToggles);
        toolbarController.getLayoutSwitchBtn().setManaged(showLayoutToggles);
        toolbarController.getToolbarSeparator1().setVisible(showLayoutToggles);
        toolbarController.getToolbarSeparator1().setManaged(showLayoutToggles);

        toolbarController.getToolbarOverflowBtn().getItems().clear();
        boolean needsOverflow = !showFileActions || !showSearch || !showLayoutToggles;

        if (needsOverflow) {
            if (!showSearch) {
                MenuItem searchItem = new MenuItem(i18n("app.search.placeholder"));
                searchItem.setOnAction(e -> toolbarController.getSearchField().requestFocus());
                toolbarController.getToolbarOverflowBtn().getItems().add(searchItem);
                toolbarController.getToolbarOverflowBtn().getItems().add(new SeparatorMenuItem());
            }
            if (!showFileActions) {
                MenuItem newNoteItem = new MenuItem(i18n("action.new_note"));
                newNoteItem.setOnAction(e -> controller.handleNewNote(null));
                MenuItem newCanvasItem = new MenuItem(i18n("action.new_canvas"));
                newCanvasItem.setOnAction(e -> controller.handleNewCanvas(null));
                MenuItem newFolderItem = new MenuItem(i18n("action.new_folder"));
                newFolderItem.setOnAction(e -> controller.handleNewFolder(null));
                MenuItem newTagItem = new MenuItem(i18n("action.new_tag"));
                newTagItem.setOnAction(e -> controller.handleNewTag(null));
                MenuItem saveItem = new MenuItem(i18n("action.save"));
                saveItem.setOnAction(e -> controller.handleSave(null));
                MenuItem deleteItem = new MenuItem(i18n("action.delete"));
                deleteItem.setOnAction(e -> controller.handleDelete(null));
                toolbarController.getToolbarOverflowBtn().getItems().addAll(
                        newNoteItem, newCanvasItem, newFolderItem, newTagItem, saveItem, new SeparatorMenuItem(), deleteItem);
            }
            if (!showLayoutToggles) {
                if (!toolbarController.getToolbarOverflowBtn().getItems().isEmpty()) {
                    toolbarController.getToolbarOverflowBtn().getItems().add(new SeparatorMenuItem());
                }
                MenuItem toggleSidebar = new MenuItem(i18n("action.toggle_sidebar"));
                toggleSidebar.setOnAction(e -> controller.handleToggleSidebar(null));
                MenuItem toggleNotes = new MenuItem(i18n("action.toggle_notes_list"));
                toggleNotes.setOnAction(e -> controller.handleToggleNotesPanel(null));
                MenuItem switchLayout = new MenuItem(i18n("action.switch_layout"));
                switchLayout.setOnAction(e -> controller.handleViewLayoutSwitch(null));
                toolbarController.getToolbarOverflowBtn().getItems().addAll(toggleSidebar, toggleNotes, switchLayout);
            }

            toolbarController.getToolbarOverflowBtn().setVisible(true);
            toolbarController.getToolbarOverflowBtn().setManaged(true);
        } else {
            toolbarController.getToolbarOverflowBtn().getItems().clear();
            toolbarController.getToolbarOverflowBtn().setVisible(false);
            toolbarController.getToolbarOverflowBtn().setManaged(false);
        }
    }
}


// ===== UiLayout =====
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


