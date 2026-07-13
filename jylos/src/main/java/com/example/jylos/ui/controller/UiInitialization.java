package com.example.jylos.ui.controller;

import java.util.function.Consumer;
import java.util.function.Function;

import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

/**
 * Encapsulates lightweight UI initialization blocks for MainController.
 */
class UiInitialization {

    /** Runnables for the toolbar overflow menu entries. */
    record OverflowActions(
            Runnable newNote,
            Runnable newCanvas,
            Runnable newFolder,
            Runnable newTag,
            Runnable save,
            Runnable delete,
            Runnable toggleSidebar,
            Runnable toggleNotesPanel,
            Runnable toggleRightPanel,
            Runnable viewLayoutSwitch) {
    }

    private Function<String, String> i18nFn;
    private OverflowActions overflowActions;

    void wire(Function<String, String> i18n, OverflowActions overflowActions) {
        this.i18nFn = i18n;
        this.overflowActions = overflowActions;
    }

    private String i18n(String key) {
        return i18nFn.apply(key);
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
        wireCollapsibleSection(header, content, collapseIcon, null);
    }

    /** Wires a header/content/icon trio as a click-to-collapse section. */
    public void wireCollapsibleSection(HBox header, VBox content, Label collapseIcon, Consumer<Boolean> onToggle) {
        if (header == null || content == null || collapseIcon == null) {
            return;
        }
        header.setOnMouseClicked(e -> {
            boolean expand = !content.isVisible();
            content.setVisible(expand);
            content.setManaged(expand);
            collapseIcon.setText(expand ? "▼" : "▶");
            if (onToggle != null) {
                onToggle.accept(expand);
            }
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
                newNoteItem.setOnAction(e -> overflowActions.newNote().run());
                MenuItem newCanvasItem = new MenuItem(i18n("action.new_canvas"));
                newCanvasItem.setOnAction(e -> overflowActions.newCanvas().run());
                MenuItem newFolderItem = new MenuItem(i18n("action.new_folder"));
                newFolderItem.setOnAction(e -> overflowActions.newFolder().run());
                MenuItem newTagItem = new MenuItem(i18n("action.new_tag"));
                newTagItem.setOnAction(e -> overflowActions.newTag().run());
                MenuItem saveItem = new MenuItem(i18n("action.save"));
                saveItem.setOnAction(e -> overflowActions.save().run());
                MenuItem deleteItem = new MenuItem(i18n("action.delete"));
                deleteItem.setOnAction(e -> overflowActions.delete().run());
                toolbarController.getToolbarOverflowBtn().getItems().addAll(
                        newNoteItem, newCanvasItem, newFolderItem, newTagItem, saveItem, new SeparatorMenuItem(), deleteItem);
            }
            if (!showLayoutToggles) {
                if (!toolbarController.getToolbarOverflowBtn().getItems().isEmpty()) {
                    toolbarController.getToolbarOverflowBtn().getItems().add(new SeparatorMenuItem());
                }
                MenuItem toggleSidebar = new MenuItem(i18n("action.toggle_sidebar"));
                toggleSidebar.setOnAction(e -> overflowActions.toggleSidebar().run());
                MenuItem toggleNotes = new MenuItem(i18n("action.toggle_notes_list"));
                toggleNotes.setOnAction(e -> overflowActions.toggleNotesPanel().run());
                MenuItem toggleRightPanel = new MenuItem(i18n("action.toggle_right_panel"));
                toggleRightPanel.setOnAction(e -> overflowActions.toggleRightPanel().run());
                MenuItem switchLayout = new MenuItem(i18n("action.switch_layout"));
                switchLayout.setOnAction(e -> overflowActions.viewLayoutSwitch().run());
                toolbarController.getToolbarOverflowBtn().getItems().addAll(
                        toggleSidebar, toggleNotes, toggleRightPanel, switchLayout);
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
