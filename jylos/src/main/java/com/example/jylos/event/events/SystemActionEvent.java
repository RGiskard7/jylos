package com.example.jylos.event.events;

import com.example.jylos.event.AppEvent;

/**
 * Event fired when a broad system action is requested from the UI (Menu or
 * Toolbar).
 * 
 * Contract:
 * - Mutating actions (e.g. SAVE, DELETE) must be handled by a single owner to
 *   avoid re-publication loops.
 * - Controllers should not re-publish the same action from their action
 *   handler.
 *
 * @author Edu Díaz (RGiskard7)
 * @since 1.0.0
 */
public class SystemActionEvent extends AppEvent {
    /** Application-level action requested by menus, toolbar buttons or shortcuts. */
    public enum ActionType {
        NEW_NOTE,
        NEW_CANVAS,
        NEW_FOLDER,
        NEW_TAG,
        SAVE,
        SAVE_ALL,
        DELETE,
        IMPORT,
        EXPORT,
        EXIT,
        UNDO,
        REDO,
        CUT,
        COPY,
        PASTE,
        FIND,
        REPLACE,
        TOGGLE_SIDEBAR,
        TOGGLE_NOTES_LIST,
        SWITCH_LAYOUT,
        ZOOM_IN,
        ZOOM_OUT,
        RESET_ZOOM,
        ZOOM_EDITOR_IN,
        ZOOM_EDITOR_OUT,
        RESET_EDITOR_ZOOM,
        LIST_VIEW,
        GRID_VIEW,
        TAGS_MANAGER,
        PLUGIN_MANAGER,
        PREFERENCES,
        SWITCH_STORAGE,
        DOCUMENTATION,
        CHECK_UPDATES,
        ABOUT,
        SORT_FOLDERS,
        EXPAND_ALL_FOLDERS,
        COLLAPSE_ALL_FOLDERS,
        SORT_TAGS,
        SORT_RECENT,
        SORT_FAVORITES,
        SORT_TRASH,
        EMPTY_TRASH,
        REFRESH_NOTES,
        TOGGLE_TAGS,
        TOGGLE_FORMAT_TOOLBAR,
        TOGGLE_READING_MODE,
        TOGGLE_SOURCE_MODE,
        SPLIT_VIEW_MODE,
        TOGGLE_PIN,
        TOGGLE_FAVORITE,
        TOGGLE_RIGHT_PANEL,
        HEADING1,
        HEADING2,
        HEADING3,
        BOLD,
        ITALIC,
        STRIKE,
        UNDERLINE,
        HIGHLIGHT,
        LINK,
        RICH_LINK,
        IMAGE,
        TODO_LIST,
        BULLET_LIST,
        NUMBERED_LIST,
        QUOTE,
        CODE,
        NAVIGATE_BACK,
        NAVIGATE_FORWARD,
        GRAPH_VIEW,
        /** The graph in a new tab (Cmd/Ctrl+click or middle click on its button). */
        GRAPH_VIEW_NEW_TAB,
        KNOWLEDGE_INSIGHTS,
        /** Takes the fields older versions of Jylos wrote out of every note of the vault. */
        VAULT_CLEANUP,
        /** Marks as favorites the notes bookmarked in Obsidian (reads its settings, never writes them). */
        IMPORT_OBSIDIAN_BOOKMARKS,
        IMPORT_OBSIDIAN_SETTINGS,
        FOCUS_MODE,
        KANBAN_VIEW,
        PRIVATE_TOGGLE,
        NOTES_UNLOCK,
        NOTES_LOCK,
        IMPORT_OBSIDIAN,
        IMPORT_ENEX,
        NOTE_HISTORY,
        CLOSE_NOTE,
        QUICK_SWITCHER,
        GIT_PANEL,
        GIT_SYNC,
        GIT_COMMIT_PUSH,
        GIT_PULL,
        GIT_HISTORY,
        GIT_INIT,
        GIT_ADD_REMOTE,
        DAILY_NOTE,
        NEW_FROM_TEMPLATE,
        INSERT_TEMPLATE,
        INSERT_CURRENT_DATE,
        INSERT_CURRENT_TIME,
        EXPORT_VAULT,
        WORKSPACE_SAVE,
        WORKSPACE_SAVE_AS,
        WORKSPACE_OPEN,
        WORKSPACE_MANAGE,
        /** A new empty tab (the + after the tabs, Cmd/Ctrl+T). */
        NEW_TAB,
        /** A new Kanban board note in the current folder. */
        NEW_KANBAN_BOARD,
        /** The vault's home page, in the active tab (or its own tab, if open). */
        GO_HOME,
        /** The vault's home page, in a new tab. */
        GO_HOME_NEW_TAB,
        /** The right panel open on the backlinks of the open note. */
        SHOW_BACKLINKS,
        /** The right panel open on the outgoing links of the open note. */
        SHOW_OUTGOING_LINKS
    }

    private final ActionType actionType;

    public SystemActionEvent(ActionType actionType) {
        this.actionType = actionType;
    }

    public ActionType getActionType() {
        return actionType;
    }
}
