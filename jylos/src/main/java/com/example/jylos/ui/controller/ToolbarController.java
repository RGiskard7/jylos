package com.example.jylos.ui.controller;

import com.example.jylos.config.AppContext;
import com.example.jylos.event.EventBus;
import com.example.jylos.event.events.SystemActionEvent;
import com.example.jylos.event.events.UIEvents;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;

/**
 * Controller for the Top Menu Bar and Toolbar (ToolbarView.fxml).
 */
public class ToolbarController {

    private EventBus eventBus;

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
    private Button newNoteBtn;
    @FXML
    private Button newFolderBtn;
    @FXML
    private Button newTagBtn;
    @FXML
    private Button saveBtn;
    @FXML
    private Button deleteBtn;
    @FXML
    private TextField searchField;
    @FXML
    private ToggleButton listViewButton;
    @FXML
    private ToggleButton gridViewButton;
    @FXML
    private MenuButton toolbarOverflowBtn;
    @FXML
    private Separator toolbarSeparator1;
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
    private RadioMenuItem englishLangMenuItem;
    @FXML
    private RadioMenuItem spanishLangMenuItem;
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

    public void setEventBus(EventBus eventBus) {
        this.eventBus = eventBus != null ? eventBus
                : (AppContext.isInitialized() ? AppContext.getEventBus() : null);
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

    public RadioMenuItem getEnglishLangMenuItem() {
        return englishLangMenuItem;
    }

    public RadioMenuItem getSpanishLangMenuItem() {
        return spanishLangMenuItem;
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

    public ToggleButton getListViewButton() {
        return listViewButton;
    }

    public ToggleButton getGridViewButton() {
        return gridViewButton;
    }

    public Button getLayoutSwitchBtn() {
        return layoutSwitchBtn;
    }

    public MenuButton getToolbarOverflowBtn() {
        return toolbarOverflowBtn;
    }

    public Separator getToolbarSeparator1() {
        return toolbarSeparator1;
    }

    @FXML
    private void handleNewNote(ActionEvent event) {
        publishEvent(SystemActionEvent.ActionType.NEW_NOTE);
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
    private void handleDelete(ActionEvent event) {
        publishEvent(SystemActionEvent.ActionType.DELETE);
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
    private void handleToggleNotesPanel(ActionEvent event) {
        publishEvent(SystemActionEvent.ActionType.TOGGLE_NOTES_LIST);
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
    private void handleWorkspaceOpen(ActionEvent event) {
        publishEvent(SystemActionEvent.ActionType.WORKSPACE_OPEN);
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
    private void handleViewLayoutSwitch(ActionEvent event) {
        publishEvent(SystemActionEvent.ActionType.SWITCH_LAYOUT);
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
        if (eventBus != null)
            eventBus.publish(new UIEvents.ShowCommandPaletteEvent());
    }

    @FXML
    private void handleQuickSwitcher(ActionEvent event) {
        if (eventBus != null)
            eventBus.publish(new UIEvents.ShowQuickSwitcherEvent());
    }

    @FXML
    private void handleSearch(ActionEvent event) {
        if (searchField != null) {
            searchField.requestFocus();
            if (!searchField.getText().isEmpty()) {
                searchField.selectAll();
            }
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
        if (eventBus != null) {
            eventBus.publish(new com.example.jylos.event.events.UIEvents.ShowKeyboardShortcutsEvent());
        }
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
    private void handleListView(ActionEvent event) {
        publishEvent(SystemActionEvent.ActionType.LIST_VIEW);
    }

    @FXML
    private void handleGridView(ActionEvent event) {
        publishEvent(SystemActionEvent.ActionType.GRID_VIEW);
    }

    @FXML
    private void handleLightTheme(ActionEvent event) {
        if (eventBus != null && lightThemeMenuItem.isSelected()) {
            eventBus.publish(new UIEvents.ThemeChangedEvent("light"));
        }
    }

    @FXML
    private void handleDarkTheme(ActionEvent event) {
        if (eventBus != null && darkThemeMenuItem.isSelected()) {
            eventBus.publish(new UIEvents.ThemeChangedEvent("dark"));
        }
    }

    @FXML
    private void handleSystemTheme(ActionEvent event) {
        if (eventBus != null && systemThemeMenuItem.isSelected()) {
            eventBus.publish(new UIEvents.ThemeChangedEvent("system"));
        }
    }

    public void setResponsiveState(boolean showSearch, boolean showLayoutToggles, boolean showFileActions) {
        if (searchField != null) {
            searchField.setVisible(showSearch);
            searchField.setManaged(showSearch);
        }
        if (sidebarToggleBtn != null) {
            sidebarToggleBtn.setVisible(showLayoutToggles);
            sidebarToggleBtn.setManaged(showLayoutToggles);
            notesPanelToggleBtn.setVisible(showLayoutToggles);
            notesPanelToggleBtn.setManaged(showLayoutToggles);
            layoutSwitchBtn.setVisible(showLayoutToggles);
            layoutSwitchBtn.setManaged(showLayoutToggles);
        }
        if (newNoteBtn != null) {
            newNoteBtn.setVisible(showFileActions);
            newNoteBtn.setManaged(showFileActions);
            newFolderBtn.setVisible(showFileActions);
            newFolderBtn.setManaged(showFileActions);
            newTagBtn.setVisible(showFileActions);
            newTagBtn.setManaged(showFileActions);
            saveBtn.setVisible(showFileActions);
            saveBtn.setManaged(showFileActions);
            deleteBtn.setVisible(showFileActions);
            deleteBtn.setManaged(showFileActions);
        }
    }

    private void publishEvent(SystemActionEvent.ActionType actionType) {
        if (eventBus != null) {
            eventBus.publish(new SystemActionEvent(actionType));
        }
    }
}
