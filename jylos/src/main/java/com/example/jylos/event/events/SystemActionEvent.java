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
 */
public class SystemActionEvent extends AppEvent {
    public enum ActionType {
        NEW_NOTE,
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
        EDITOR_ONLY_MODE,
        SPLIT_VIEW_MODE,
        PREVIEW_ONLY_MODE,
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
        IMAGE,
        TODO_LIST,
        BULLET_LIST,
        NUMBERED_LIST,
        QUOTE,
        CODE,
        NAVIGATE_BACK,
        NAVIGATE_FORWARD,
        GRAPH_VIEW,
        KNOWLEDGE_INSIGHTS,
        FOCUS_MODE,
        KANBAN_VIEW,
        PRIVATE_TOGGLE,
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
        GIT_INIT,
        GIT_ADD_REMOTE,
        DAILY_NOTE,
        NEW_FROM_TEMPLATE,
        EXPORT_VAULT
    }

    private final ActionType actionType;

    public SystemActionEvent(ActionType actionType) {
        this.actionType = actionType;
    }

    public ActionType getActionType() {
        return actionType;
    }
}
