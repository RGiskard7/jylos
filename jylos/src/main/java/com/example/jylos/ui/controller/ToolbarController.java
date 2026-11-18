package com.example.jylos.ui.controller;

import com.example.jylos.event.EventBus;
import com.example.jylos.event.events.SystemActionEvent;
import com.example.jylos.ui.UiNodes;
import com.example.jylos.ui.components.NewTabGesture;
import com.example.jylos.ui.preferences.LayoutMode;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.geometry.Side;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;

import java.util.ResourceBundle;
import java.util.function.Consumer;

/**
 * Controller for the Top Menu Bar and Toolbar (ToolbarView.fxml).
 *
 * @author Edu Díaz (RGiskard7)
 * @since 1.0.0
 */
public class ToolbarController {

    private EventBus eventBus;
    private Runnable showCommandPaletteAction = () -> {
    };
    private Runnable showQuickSwitcherAction = () -> {
    };
    private Runnable showKeyboardShortcutsAction = () -> {
    };
    private Runnable applyLightThemeAction = () -> {
    };
    private Runnable applyDarkThemeAction = () -> {
    };
    private Runnable applySystemThemeAction = () -> {
    };
    private Runnable toggleRightPanelAction = () -> {
    };

    public void wire(EventBus eventBus, Runnable showCommandPaletteAction,
            Runnable showQuickSwitcherAction, Runnable showKeyboardShortcutsAction,
            Runnable applyLightThemeAction, Runnable applyDarkThemeAction, Runnable applySystemThemeAction,
            Runnable toggleRightPanelAction) {
        setEventBus(eventBus);
        this.showCommandPaletteAction = showCommandPaletteAction != null ? showCommandPaletteAction : () -> {
        };
        this.showQuickSwitcherAction = showQuickSwitcherAction != null ? showQuickSwitcherAction : () -> {
        };
        this.showKeyboardShortcutsAction = showKeyboardShortcutsAction != null ? showKeyboardShortcutsAction : () -> {
        };
        this.applyLightThemeAction = applyLightThemeAction != null ? applyLightThemeAction : () -> {
        };
        this.applyDarkThemeAction = applyDarkThemeAction != null ? applyDarkThemeAction : () -> {
        };
        this.applySystemThemeAction = applySystemThemeAction != null ? applySystemThemeAction : () -> {
        };
        this.toggleRightPanelAction = toggleRightPanelAction != null ? toggleRightPanelAction : () -> {
        };
    }

    // FXML injected fields
    @FXML
    private MenuBar menuBar;
    @FXML
    private ToggleButton sidebarToggleBtn;
    @FXML
    private ToggleButton notesPanelToggleBtn;
    @FXML
    private Button layoutSwitchBtn;
    @FXML
    private ResourceBundle resources;
    /** Where the open-note tabs live in the top bar (see TopBarTabsSupport). */
    @FXML
    private HBox tabsHost;
    /** The layout ticked in the layout menu. */
    private LayoutMode currentLayout =
            LayoutMode.CLASSIC_COLUMNS;
    private Consumer<LayoutMode> layoutModeAction = mode -> {
    };
    /** In the Explorer layout the notes panel only shows results, so its toggle is hidden. */
    private boolean notesToggleAvailable = true;
    private boolean layoutTogglesShown = true;
    @FXML
    private Button newNoteBtn;
    @FXML
    private Button newFolderBtn;
    @FXML
    private Button newTagBtn;
    @FXML
    private TextField searchField;
    @FXML
    private Button searchToggleBtn;
    /** Opens the graph; with Cmd/Ctrl or the middle button, in a new tab. */
    @FXML
    private Button graphViewBtn;
    @FXML
    private ToggleButton rightPanelToggleBtn;
    @FXML
    private MenuButton toolbarOverflowBtn;
    @FXML
    private HBox toolbarHBox;
    @FXML
    private HBox pluginToolbarContainer;

    public HBox getToolbarHBox() {
        return toolbarHBox;
    }

    /** Container that hosts plugin-registered toolbar buttons (hidden while empty). */
    public HBox getPluginToolbarContainer() {
        return pluginToolbarContainer;
    }

    @FXML
    private Menu languageMenu;
    @FXML
    private RadioMenuItem lightThemeMenuItem;
    @FXML
    private RadioMenuItem darkThemeMenuItem;
    @FXML
    private RadioMenuItem systemThemeMenuItem;
    @FXML
    private Menu pluginsMenu;

    @FXML
    private MenuItem toggleSidebarMenuItem;
    @FXML
    private MenuItem toggleNotesListMenuItem;

    @FXML
    private void initialize() {
        if (menuBar != null) {
            menuBar.setUseSystemMenuBar(true);
        }
        if (searchField != null) {
            searchField.focusedProperty().addListener((obs, wasFocused, isFocused) -> {
                if (!isFocused) {
                    collapseSearchIfEmpty();
                }
            });
        }
        if (graphViewBtn != null) {
            NewTabGesture.onButton(graphViewBtn, () -> publishEvent(SystemActionEvent.ActionType.GRAPH_VIEW_NEW_TAB));
        }
    }

    private void setEventBus(EventBus eventBus) {
        this.eventBus = eventBus;
    }

    public Menu getPluginsMenu() {
        return pluginsMenu;
    }

    public TextField getSearchField() {
        return searchField;
    }

    public ToggleButton getSidebarToggleBtn() {
        return sidebarToggleBtn;
    }

    public ToggleButton getNotesPanelToggleBtn() {
        return notesPanelToggleBtn;
    }

    public MenuItem getToggleSidebarMenuItem() {
        return toggleSidebarMenuItem;
    }

    public MenuItem getToggleNotesListMenuItem() {
        return toggleNotesListMenuItem;
    }

    /** View > Language, filled by {@code AppSettings} with the app's languages. */
    public Menu getLanguageMenu() {
        return languageMenu;
    }

    public RadioMenuItem getLightThemeMenuItem() {
        return lightThemeMenuItem;
    }

    public RadioMenuItem getDarkThemeMenuItem() {
        return darkThemeMenuItem;
    }

    public RadioMenuItem getSystemThemeMenuItem() {
        return systemThemeMenuItem;
    }

    public ToggleButton getRightPanelToggleBtn() {
        return rightPanelToggleBtn;
    }

    public HBox getTabsHost() {
        return tabsHost;
    }

    public Button getLayoutSwitchBtn() {
        return layoutSwitchBtn;
    }

    public MenuButton getToolbarOverflowBtn() {
        return toolbarOverflowBtn;
    }

    @FXML
    private void handleNewNote(ActionEvent event) {
        publishEvent(SystemActionEvent.ActionType.NEW_NOTE);
    }

    @FXML
    private void handleNewKanbanBoard(ActionEvent event) {
        publishEvent(SystemActionEvent.ActionType.NEW_KANBAN_BOARD);
    }

    @FXML
    private void handleFocusMode(ActionEvent event) {
        publishEvent(SystemActionEvent.ActionType.FOCUS_MODE);
    }

    @FXML
    private void handleNewTab(ActionEvent event) {
        publishEvent(SystemActionEvent.ActionType.NEW_TAB);
    }

    @FXML
    private void handleNewCanvas(ActionEvent event) {
        publishEvent(SystemActionEvent.ActionType.NEW_CANVAS);
    }

    @FXML
    private void handleNewFolder(ActionEvent event) {
        publishEvent(SystemActionEvent.ActionType.NEW_FOLDER);
    }

    @FXML
    private void handleNewTag(ActionEvent event) {
        publishEvent(SystemActionEvent.ActionType.NEW_TAG);
    }

    @FXML
    private void handleSave(ActionEvent event) {
        publishEvent(SystemActionEvent.ActionType.SAVE);
    }

    @FXML
    private void handleSaveAll(ActionEvent event) {
        publishEvent(SystemActionEvent.ActionType.SAVE_ALL);
    }

    @FXML
    private void handleUndo(ActionEvent event) {
        publishEvent(SystemActionEvent.ActionType.UNDO);
    }

    @FXML
    private void handleRedo(ActionEvent event) {
        publishEvent(SystemActionEvent.ActionType.REDO);
    }

    @FXML
    private void handleCut(ActionEvent event) {
        publishEvent(SystemActionEvent.ActionType.CUT);
    }

    @FXML
    private void handleCopy(ActionEvent event) {
        publishEvent(SystemActionEvent.ActionType.COPY);
    }

    @FXML
    private void handlePaste(ActionEvent event) {
        publishEvent(SystemActionEvent.ActionType.PASTE);
    }

    @FXML
    private void handleFind(ActionEvent event) {
        publishEvent(SystemActionEvent.ActionType.FIND);
    }

    @FXML
    private void handleReplace(ActionEvent event) {
        publishEvent(SystemActionEvent.ActionType.REPLACE);
    }

    @FXML
    private void handleToggleSidebar(ActionEvent event) {
        publishEvent(SystemActionEvent.ActionType.TOGGLE_SIDEBAR);
    }

    @FXML
    private void handleToggleFormatToolbar(ActionEvent event) {
        publishEvent(SystemActionEvent.ActionType.TOGGLE_FORMAT_TOOLBAR);
    }

    @FXML
    private void handleToggleNotesPanel(ActionEvent event) {
        publishEvent(SystemActionEvent.ActionType.TOGGLE_NOTES_LIST);
    }

    @FXML
    private void handleToggleReadingMode(ActionEvent event) {
        publishEvent(SystemActionEvent.ActionType.TOGGLE_READING_MODE);
    }

    @FXML
    private void handleSplitViewMode(ActionEvent event) {
        publishEvent(SystemActionEvent.ActionType.SPLIT_VIEW_MODE);
    }

    @FXML
    private void handleToggleSourceMode(ActionEvent event) {
        publishEvent(SystemActionEvent.ActionType.TOGGLE_SOURCE_MODE);
    }

    @FXML
    private void handleGraphView(ActionEvent event) {
        publishEvent(SystemActionEvent.ActionType.GRAPH_VIEW);
    }

    @FXML
    private void handleKanbanView(ActionEvent event) {
        publishEvent(SystemActionEvent.ActionType.KANBAN_VIEW);
    }

    @FXML
    private void handleKnowledgeInsights(ActionEvent event) {
        publishEvent(SystemActionEvent.ActionType.KNOWLEDGE_INSIGHTS);
    }

    @FXML
    private void handleWorkspaceSave(ActionEvent event) {
        publishEvent(SystemActionEvent.ActionType.WORKSPACE_SAVE);
    }

    @FXML
    private void handleWorkspaceSaveAs(ActionEvent event) {
        publishEvent(SystemActionEvent.ActionType.WORKSPACE_SAVE_AS);
    }

    @FXML
    private void handleWorkspaceManage(ActionEvent event) {
        publishEvent(SystemActionEvent.ActionType.WORKSPACE_MANAGE);
    }

    @FXML
    private void handleImportObsidian(ActionEvent event) {
        publishEvent(SystemActionEvent.ActionType.IMPORT_OBSIDIAN);
    }

    @FXML
    private void handleImportEnex(ActionEvent event) {
        publishEvent(SystemActionEvent.ActionType.IMPORT_ENEX);
    }

    @FXML
    private void handleTogglePrivate(ActionEvent event) {
        publishEvent(SystemActionEvent.ActionType.PRIVATE_TOGGLE);
    }

    @FXML
    private void handleUnlockNotes(ActionEvent event) {
        publishEvent(SystemActionEvent.ActionType.NOTES_UNLOCK);
    }

    @FXML
    private void handleLockNotes(ActionEvent event) {
        publishEvent(SystemActionEvent.ActionType.NOTES_LOCK);
    }

    @FXML
    private void handleNoteHistory(ActionEvent event) {
        publishEvent(SystemActionEvent.ActionType.NOTE_HISTORY);
    }

    @FXML
    private void handleVaultCleanup(ActionEvent event) {
        publishEvent(SystemActionEvent.ActionType.VAULT_CLEANUP);
    }

    @FXML
    private void handleImportObsidianBookmarks(ActionEvent event) {
        publishEvent(SystemActionEvent.ActionType.IMPORT_OBSIDIAN_BOOKMARKS);
    }

    /** The menu the layout button opens: the three layouts, the current one ticked. */
    ContextMenu layoutMenu() {
        ContextMenu menu = new ContextMenu();
        ToggleGroup group = new ToggleGroup();
        for (LayoutMode mode : LayoutMode.values()) {
            String key = switch (mode) {
                case EXPLORER -> "layout.mode.explorer";
                case CLASSIC_COLUMNS -> "layout.mode.columns";
                case CLASSIC_STACKED -> "layout.mode.stacked";
            };
            RadioMenuItem item = new RadioMenuItem(resources != null && resources.containsKey(key)
                    ? resources.getString(key) : key);
            item.setToggleGroup(group);
            item.setSelected(mode == currentLayout);
            item.setOnAction(e -> layoutModeAction.accept(mode));
            menu.getItems().add(item);
        }
        return menu;
    }

    @FXML
    private void handleLayoutMenu(ActionEvent event) {
        if (layoutSwitchBtn != null) {
            layoutMenu().show(layoutSwitchBtn, Side.BOTTOM, 0, 0);
        }
    }

    /** What choosing a layout in the layout menu does. */
    public void setLayoutModeAction(Consumer<LayoutMode> action) {
        this.layoutModeAction = action != null ? action : mode -> {
        };
    }

    /** Ticks {@code mode} in the layout menu and shows the notes-panel toggle only where it applies. */
    public void showLayoutMode(LayoutMode mode) {
        if (mode != null) {
            currentLayout = mode;
        }
        notesToggleAvailable = mode != LayoutMode.EXPLORER;
        applyNotesToggleVisibility();
    }

    private void applyNotesToggleVisibility() {
        if (notesPanelToggleBtn != null) {
            boolean show = layoutTogglesShown && notesToggleAvailable;
            UiNodes.show(notesPanelToggleBtn, show);
        }
    }


    @FXML
    private void handleZoomIn(ActionEvent event) {
        publishEvent(SystemActionEvent.ActionType.ZOOM_IN);
    }

    @FXML
    private void handleZoomOut(ActionEvent event) {
        publishEvent(SystemActionEvent.ActionType.ZOOM_OUT);
    }

    @FXML
    private void handleResetZoom(ActionEvent event) {
        publishEvent(SystemActionEvent.ActionType.RESET_ZOOM);
    }

    @FXML
    private void handleEditorZoomIn(ActionEvent event) {
        publishEvent(SystemActionEvent.ActionType.ZOOM_EDITOR_IN);
    }

    @FXML
    private void handleEditorZoomOut(ActionEvent event) {
        publishEvent(SystemActionEvent.ActionType.ZOOM_EDITOR_OUT);
    }

    @FXML
    private void handleEditorResetZoom(ActionEvent event) {
        publishEvent(SystemActionEvent.ActionType.RESET_EDITOR_ZOOM);
    }

    @FXML
    private void handleCommandPalette(ActionEvent event) {
        showCommandPaletteAction.run();
    }

    @FXML
    private void handleQuickSwitcher(ActionEvent event) {
        showQuickSwitcherAction.run();
    }

    @FXML
    private void handleSearch(ActionEvent event) {
        expandSearch();
    }

    @FXML
    private void handleSearchToggle(ActionEvent event) {
        expandSearch();
    }

    /** Shows the real search field in place of its collapsed icon button and
     *  focuses it — the single entry point every search trigger (the icon click,
     *  the menu item, the Ctrl+Shift+F shortcut, and the overflow menu's search
     *  entry on a narrow window) goes through. */
    public void expandSearch() {
        if (searchField == null) {
            return;
        }
        if (searchToggleBtn != null) {
            UiNodes.show(searchToggleBtn, false);
        }
        UiNodes.show(searchField, true);
        searchField.requestFocus();
        if (!searchField.getText().isEmpty()) {
            searchField.selectAll();
        }
    }

    /** Collapses the search field back to its icon button once it loses focus
     *  with nothing typed — matching Obsidian. Text is left in place if the
     *  field isn't empty, so a query in progress is never lost from a stray
     *  focus change. */
    private void collapseSearchIfEmpty() {
        if (searchField == null || !searchField.getText().isEmpty()) {
            return;
        }
        UiNodes.show(searchField, false);
        if (searchToggleBtn != null) {
            UiNodes.show(searchToggleBtn, true);
        }
    }

    @FXML
    private void handleTagsManager(ActionEvent event) {
        publishEvent(SystemActionEvent.ActionType.TAGS_MANAGER);
    }

    @FXML
    private void handlePluginManager(ActionEvent event) {
        publishEvent(SystemActionEvent.ActionType.PLUGIN_MANAGER);
    }

    @FXML
    private void handlePreferences(ActionEvent event) {
        publishEvent(SystemActionEvent.ActionType.PREFERENCES);
    }

    @FXML
    private void handleSwitchStorage(ActionEvent event) {
        publishEvent(SystemActionEvent.ActionType.SWITCH_STORAGE);
    }

    @FXML
    private void handleGitPanel(ActionEvent event) {
        publishEvent(SystemActionEvent.ActionType.GIT_PANEL);
    }

    @FXML
    private void handleGitSync(ActionEvent event) {
        publishEvent(SystemActionEvent.ActionType.GIT_SYNC);
    }

    @FXML
    private void handleGitCommitPush(ActionEvent event) {
        publishEvent(SystemActionEvent.ActionType.GIT_COMMIT_PUSH);
    }

    @FXML
    private void handleGitPull(ActionEvent event) {
        publishEvent(SystemActionEvent.ActionType.GIT_PULL);
    }

    @FXML
    private void handleGitHistory(ActionEvent event) {
        publishEvent(SystemActionEvent.ActionType.GIT_HISTORY);
    }

    @FXML
    private void handleGitInit(ActionEvent event) {
        publishEvent(SystemActionEvent.ActionType.GIT_INIT);
    }

    @FXML
    private void handleGitAddRemote(ActionEvent event) {
        publishEvent(SystemActionEvent.ActionType.GIT_ADD_REMOTE);
    }

    @FXML
    private void handleDocumentation(ActionEvent event) {
        publishEvent(SystemActionEvent.ActionType.DOCUMENTATION);
    }

    @FXML
    private void handleKeyboardShortcuts(ActionEvent event) {
        showKeyboardShortcutsAction.run();
    }

    @FXML
    private void handleCheckUpdates(ActionEvent event) {
        publishEvent(SystemActionEvent.ActionType.CHECK_UPDATES);
    }

    @FXML
    private void handleAbout(ActionEvent event) {
        publishEvent(SystemActionEvent.ActionType.ABOUT);
    }

    @FXML
    private void handleImport(ActionEvent event) {
        publishEvent(SystemActionEvent.ActionType.IMPORT);
    }

    @FXML
    private void handleExport(ActionEvent event) {
        publishEvent(SystemActionEvent.ActionType.EXPORT);
    }

    @FXML
    private void handleExportVault(ActionEvent event) {
        publishEvent(SystemActionEvent.ActionType.EXPORT_VAULT);
    }

    @FXML
    private void handleDailyNote(ActionEvent event) {
        publishEvent(SystemActionEvent.ActionType.DAILY_NOTE);
    }

    @FXML
    private void handleNewFromTemplate(ActionEvent event) {
        publishEvent(SystemActionEvent.ActionType.NEW_FROM_TEMPLATE);
    }

    @FXML
    private void handleExit(ActionEvent event) {
        publishEvent(SystemActionEvent.ActionType.EXIT);
    }

    @FXML
    private void handleToggleRightPanel(ActionEvent event) {
        toggleRightPanelAction.run();
    }

    @FXML
    private void handleLightTheme(ActionEvent event) {
        if (lightThemeMenuItem.isSelected()) {
            applyLightThemeAction.run();
        }
    }

    @FXML
    private void handleDarkTheme(ActionEvent event) {
        if (darkThemeMenuItem.isSelected()) {
            applyDarkThemeAction.run();
        }
    }

    @FXML
    private void handleSystemTheme(ActionEvent event) {
        if (systemThemeMenuItem.isSelected()) {
            applySystemThemeAction.run();
        }
    }

    public void setResponsiveState(boolean showSearch, boolean showLayoutToggles, boolean showFileActions) {
        if (searchField != null) {
            if (!showSearch) {
                // Too narrow for search at all — it moves into the overflow menu
                // instead, so neither the field nor its collapsed icon belong here.
                UiNodes.show(searchField, false);
                if (searchToggleBtn != null) {
                    UiNodes.show(searchToggleBtn, false);
                }
            } else if (searchToggleBtn == null) {
                UiNodes.show(searchField, true);
            } else if (!searchField.isVisible()) {
                // Only set the default (collapsed) state — if the user already has
                // it expanded, a responsive re-check shouldn't yank it away.
                UiNodes.show(searchToggleBtn, true);
            }
        }
        if (sidebarToggleBtn != null) {
            UiNodes.show(sidebarToggleBtn, showLayoutToggles);
            layoutTogglesShown = showLayoutToggles;
            applyNotesToggleVisibility();
            UiNodes.show(layoutSwitchBtn, showLayoutToggles);
            if (rightPanelToggleBtn != null) {
                UiNodes.show(rightPanelToggleBtn, showLayoutToggles);
            }
        }
        if (newNoteBtn != null) {
            UiNodes.show(newNoteBtn, showFileActions);
            UiNodes.show(newFolderBtn, showFileActions);
            UiNodes.show(newTagBtn, showFileActions);
        }
    }

    private void publishEvent(SystemActionEvent.ActionType actionType) {
        if (eventBus != null) {
            eventBus.publish(new SystemActionEvent(actionType));
        }
    }
}
