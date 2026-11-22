package com.example.jylos.ui.controller;

import java.util.List;
import java.util.Map;

import com.example.jylos.event.events.SystemActionEvent.ActionType;

/**
 * Catalog of stable command IDs and legacy aliases, registered in one place.
 *
 * @author Edu Díaz (RGiskard7)
 * @since 2.3.0
 */
class CommandRegistry {

    /** Palette commands that are also menu or toolbar actions: one handler for both. */
    static final Map<String, ActionType> ACTION_COMMANDS = Map.ofEntries(
            Map.entry("cmd.new_note", ActionType.NEW_NOTE),
            Map.entry("cmd.new_tab", ActionType.NEW_TAB),
            Map.entry("cmd.new_canvas", ActionType.NEW_CANVAS),
            Map.entry("cmd.new_kanban_board", ActionType.NEW_KANBAN_BOARD),
            Map.entry("cmd.new_folder", ActionType.NEW_FOLDER),
            Map.entry("cmd.save_all", ActionType.SAVE_ALL),
            Map.entry("cmd.import", ActionType.IMPORT),
            Map.entry("cmd.export", ActionType.EXPORT),
            Map.entry("cmd.undo", ActionType.UNDO),
            Map.entry("cmd.redo", ActionType.REDO),
            Map.entry("cmd.find", ActionType.FIND),
            Map.entry("cmd.replace", ActionType.REPLACE),
            Map.entry("cmd.cut", ActionType.CUT),
            Map.entry("cmd.copy", ActionType.COPY),
            Map.entry("cmd.paste", ActionType.PASTE),
            Map.entry("cmd.toggle_sidebar", ActionType.TOGGLE_SIDEBAR),
            Map.entry("cmd.toggle_info_panel", ActionType.TOGGLE_RIGHT_PANEL),
            Map.entry("cmd.toggle_reading_view", ActionType.TOGGLE_READING_MODE),
            Map.entry("cmd.toggle_source_mode", ActionType.TOGGLE_SOURCE_MODE),
            Map.entry("cmd.split_mode", ActionType.SPLIT_VIEW_MODE),
            Map.entry("cmd.zoom_in", ActionType.ZOOM_IN),
            Map.entry("cmd.zoom_out", ActionType.ZOOM_OUT),
            Map.entry("cmd.reset_zoom", ActionType.RESET_ZOOM),
            Map.entry("cmd.graph_view", ActionType.GRAPH_VIEW),
            Map.entry("cmd.knowledge_insights", ActionType.KNOWLEDGE_INSIGHTS),
            Map.entry("cmd.vault_cleanup", ActionType.VAULT_CLEANUP),
            Map.entry("cmd.import_obsidian_bookmarks", ActionType.IMPORT_OBSIDIAN_BOOKMARKS),
            Map.entry("cmd.import_obsidian_settings", ActionType.IMPORT_OBSIDIAN_SETTINGS),
            Map.entry("cmd.workspace_save", ActionType.WORKSPACE_SAVE),
            Map.entry("cmd.workspace_save_as", ActionType.WORKSPACE_SAVE_AS),
            Map.entry("cmd.workspace_open", ActionType.WORKSPACE_OPEN),
            Map.entry("cmd.workspace_manage", ActionType.WORKSPACE_MANAGE),
            Map.entry("cmd.git_panel", ActionType.GIT_PANEL),
            Map.entry("cmd.git_sync", ActionType.GIT_SYNC),
            Map.entry("cmd.git_commit_push", ActionType.GIT_COMMIT_PUSH),
            Map.entry("cmd.git_pull", ActionType.GIT_PULL),
            Map.entry("cmd.git_init", ActionType.GIT_INIT),
            Map.entry("cmd.git_add_remote", ActionType.GIT_ADD_REMOTE),
            Map.entry("cmd.quick_switcher", ActionType.QUICK_SWITCHER),
            Map.entry("cmd.daily_note", ActionType.DAILY_NOTE),
            Map.entry("cmd.go_home", ActionType.GO_HOME),
            Map.entry("cmd.show_backlinks", ActionType.SHOW_BACKLINKS),
            Map.entry("cmd.show_outgoing_links", ActionType.SHOW_OUTGOING_LINKS),
            Map.entry("cmd.new_from_template", ActionType.NEW_FROM_TEMPLATE),
            Map.entry("cmd.insert_template", ActionType.INSERT_TEMPLATE),
            Map.entry("cmd.insert_current_date", ActionType.INSERT_CURRENT_DATE),
            Map.entry("cmd.insert_current_time", ActionType.INSERT_CURRENT_TIME),
            Map.entry("cmd.export_vault", ActionType.EXPORT_VAULT),
            Map.entry("cmd.tag_manager", ActionType.TAGS_MANAGER),
            Map.entry("cmd.preferences", ActionType.PREFERENCES),
            Map.entry("cmd.toggle_favorite", ActionType.TOGGLE_FAVORITE),
            Map.entry("cmd.refresh", ActionType.REFRESH_NOTES),
            Map.entry("cmd.documentation", ActionType.DOCUMENTATION),
            Map.entry("cmd.about", ActionType.ABOUT),
            Map.entry("cmd.focus_mode", ActionType.FOCUS_MODE),
            Map.entry("cmd.kanban_view", ActionType.KANBAN_VIEW),
            Map.entry("cmd.note_history", ActionType.NOTE_HISTORY),
            Map.entry("cmd.toggle_private", ActionType.PRIVATE_TOGGLE),
            Map.entry("cmd.lock_notes", ActionType.NOTES_LOCK),
            Map.entry("cmd.unlock_notes", ActionType.NOTES_UNLOCK),
            Map.entry("cmd.navigate_back", ActionType.NAVIGATE_BACK),
            Map.entry("cmd.navigate_forward", ActionType.NAVIGATE_FORWARD),
            Map.entry("cmd.close_note", ActionType.CLOSE_NOTE),
            Map.entry("cmd.switch_storage", ActionType.SWITCH_STORAGE),
            Map.entry("cmd.check_updates", ActionType.CHECK_UPDATES),
            Map.entry("cmd.toggle_pin", ActionType.TOGGLE_PIN),
            Map.entry("cmd.toggle_notes_list", ActionType.TOGGLE_NOTES_LIST),
            Map.entry("cmd.toggle_tags", ActionType.TOGGLE_TAGS),
            Map.entry("cmd.toggle_format_toolbar", ActionType.TOGGLE_FORMAT_TOOLBAR),
            Map.entry("cmd.new_tag", ActionType.NEW_TAG),
            Map.entry("cmd.list_view", ActionType.LIST_VIEW),
            Map.entry("cmd.grid_view", ActionType.GRID_VIEW),
            Map.entry("cmd.switch_layout", ActionType.SWITCH_LAYOUT),
            Map.entry("cmd.editor_zoom_in", ActionType.ZOOM_EDITOR_IN),
            Map.entry("cmd.editor_zoom_out", ActionType.ZOOM_EDITOR_OUT),
            Map.entry("cmd.editor_reset_zoom", ActionType.RESET_EDITOR_ZOOM),
            Map.entry("cmd.import_obsidian", ActionType.IMPORT_OBSIDIAN),
            Map.entry("cmd.import_enex", ActionType.IMPORT_ENEX));

    /** Palette commands that format the open note: what the editor's format buttons do. */
    static final Map<String, ActionType> FORMAT_COMMANDS = Map.ofEntries(
            Map.entry("cmd.bold", ActionType.BOLD),
            Map.entry("cmd.italic", ActionType.ITALIC),
            Map.entry("cmd.underline", ActionType.UNDERLINE),
            Map.entry("cmd.insert_link", ActionType.LINK),
            Map.entry("cmd.insert_rich_link", ActionType.RICH_LINK),
            Map.entry("cmd.insert_image", ActionType.IMAGE),
            Map.entry("cmd.insert_todo", ActionType.TODO_LIST),
            Map.entry("cmd.insert_list", ActionType.NUMBERED_LIST),
            Map.entry("cmd.strike", ActionType.STRIKE),
            Map.entry("cmd.highlight", ActionType.HIGHLIGHT),
            Map.entry("cmd.quote", ActionType.QUOTE),
            Map.entry("cmd.code", ActionType.CODE),
            Map.entry("cmd.insert_bullet_list", ActionType.BULLET_LIST));


    @FunctionalInterface
    interface RouteRegistrar {
        void register(String id, String legacyName, Runnable action);
    }

    @FunctionalInterface
    interface AliasRegistrar {
        void registerAlias(String alias, String commandId);
    }

    interface CommandActionProvider {
        Runnable actionFor(String commandId);
    }

    private record CommandDef(String id, String legacyName) {
    }

    private static final List<CommandDef> DEFAULT_COMMANDS = List.of(
            new CommandDef("cmd.new_note", "New Note"),
            new CommandDef("cmd.new_tab", "New Tab"),
            new CommandDef("cmd.new_canvas", "New Canvas"),
            new CommandDef("cmd.new_kanban_board", "New Kanban Board"),
            new CommandDef("cmd.new_folder", "New Folder"),
            new CommandDef("cmd.save", "Save"),
            new CommandDef("cmd.save_all", "Save All"),
            new CommandDef("cmd.import", "Import"),
            new CommandDef("cmd.export", "Export"),
            new CommandDef("cmd.delete_note", "Delete Note"),
            new CommandDef("cmd.undo", "Undo"),
            new CommandDef("cmd.redo", "Redo"),
            new CommandDef("cmd.find", "Find"),
            new CommandDef("cmd.replace", "Find and Replace"),
            new CommandDef("cmd.cut", "Cut"),
            new CommandDef("cmd.copy", "Copy"),
            new CommandDef("cmd.paste", "Paste"),
            new CommandDef("cmd.bold", "Bold"),
            new CommandDef("cmd.italic", "Italic"),
            new CommandDef("cmd.underline", "Underline"),
            new CommandDef("cmd.insert_link", "Insert Link"),
            new CommandDef("cmd.insert_rich_link", "Insert Rich Link"),
            new CommandDef("cmd.insert_image", "Insert Image"),
            new CommandDef("cmd.insert_todo", "Insert Todo"),
            new CommandDef("cmd.insert_list", "Insert List"),
            new CommandDef("cmd.toggle_sidebar", "Toggle Sidebar"),
            new CommandDef("cmd.graph_view", "Graph View"),
            new CommandDef("cmd.knowledge_insights", "Knowledge Insights"),
            new CommandDef("cmd.vault_cleanup", "Clean Up Jylos Metadata…"),
            new CommandDef("cmd.import_obsidian_bookmarks", "Import Obsidian Bookmarks"),
            new CommandDef("cmd.import_obsidian_settings", "Import Obsidian Settings…"),
            new CommandDef("cmd.workspace_save", "Workspace: Save Current"),
            new CommandDef("cmd.workspace_save_as", "Workspace: Save As…"),
            new CommandDef("cmd.workspace_open", "Workspace: Open…"),
            new CommandDef("cmd.workspace_manage", "Workspace: Manage…"),
            new CommandDef("cmd.git_panel", "Git: Sync Panel"),
            new CommandDef("cmd.git_sync", "Git: Synchronize"),
            new CommandDef("cmd.git_commit_push", "Git: Commit & Push"),
            new CommandDef("cmd.git_pull", "Git: Pull"),
            new CommandDef("cmd.git_init", "Git: Initialize"),
            new CommandDef("cmd.git_add_remote", "Git: Add Remote"),
            new CommandDef("cmd.toggle_info_panel", "Toggle Info Panel"),
            new CommandDef("cmd.toggle_reading_view", "Toggle Editing/Reading View"),
            new CommandDef("cmd.toggle_source_mode", "Toggle Source Mode"),
            new CommandDef("cmd.split_mode", "Open Linked Reading View"),
            new CommandDef("cmd.zoom_in", "Zoom In"),
            new CommandDef("cmd.zoom_out", "Zoom Out"),
            new CommandDef("cmd.reset_zoom", "Reset Zoom"),
            new CommandDef("cmd.theme_light", "Light Theme"),
            new CommandDef("cmd.theme_dark", "Dark Theme"),
            new CommandDef("cmd.theme_system", "System Theme"),
            new CommandDef("cmd.quick_switcher", "Quick Switcher"),
            new CommandDef("cmd.global_search", "Global Search"),
            new CommandDef("cmd.daily_note", "Open Today's Daily Note"),
            new CommandDef("cmd.go_home", "Go to Home Page"),
            new CommandDef("cmd.show_backlinks", "Show backlinks"),
            new CommandDef("cmd.show_outgoing_links", "Show outgoing links"),
            new CommandDef("cmd.new_from_template", "New Note from Template…"),
            new CommandDef("cmd.insert_template", "Templates: Insert template"),
            new CommandDef("cmd.insert_current_date", "Templates: Insert current date"),
            new CommandDef("cmd.insert_current_time", "Templates: Insert current time"),
            new CommandDef("cmd.export_vault", "Export All Notes (PDF/HTML)…"),
            new CommandDef("cmd.goto_all_notes", "Go to All Notes"),
            new CommandDef("cmd.goto_favorites", "Go to Favorites"),
            new CommandDef("cmd.goto_recent", "Go to Recent"),
            new CommandDef("cmd.tag_manager", "Tag Manager"),
            new CommandDef("cmd.preferences", "Preferences"),
            new CommandDef("cmd.toggle_favorite", "Toggle Favorite"),
            new CommandDef("cmd.refresh", "Refresh"),
            new CommandDef("cmd.plugins.manage", "Plugins: Manage Plugins"),
            new CommandDef("cmd.keyboard_shortcuts", "Keyboard Shortcuts"),
            new CommandDef("cmd.documentation", "Documentation"),
            new CommandDef("cmd.about", "About Jylos"),
            // Present in the ActionType catalog and reachable from a menu/toolbar/shortcut,
            // but missing from the palette until now — found by auditing SystemActionEvent.ActionType
            // against this list.
            new CommandDef("cmd.focus_mode", "Toggle Focus Mode"),
            new CommandDef("cmd.kanban_view", "Kanban View"),
            new CommandDef("cmd.note_history", "Note History"),
            new CommandDef("cmd.toggle_private", "Toggle Private Note"),
            new CommandDef("cmd.lock_notes", "Lock Private Notes"),
            new CommandDef("cmd.unlock_notes", "Unlock Private Notes"),
            new CommandDef("cmd.navigate_back", "Navigate Back"),
            new CommandDef("cmd.navigate_forward", "Navigate Forward"),
            new CommandDef("cmd.close_note", "Close Note"),
            new CommandDef("cmd.switch_storage", "Switch Storage Backend…"),
            new CommandDef("cmd.check_updates", "Check for Updates"),
            new CommandDef("cmd.toggle_pin", "Toggle Pin"),
            new CommandDef("cmd.toggle_notes_list", "Toggle Notes List"),
            new CommandDef("cmd.toggle_tags", "Toggle Tags Panel"),
            new CommandDef("cmd.toggle_format_toolbar", "Toggle Format Bar"),
            new CommandDef("cmd.new_tag", "New Tag"),
            new CommandDef("cmd.list_view", "Switch to List View"),
            new CommandDef("cmd.grid_view", "Switch to Grid View"),
            new CommandDef("cmd.switch_layout", "Switch Notes Layout"),
            new CommandDef("cmd.editor_zoom_in", "Zoom In (Editor Font)"),
            new CommandDef("cmd.editor_zoom_out", "Zoom Out (Editor Font)"),
            new CommandDef("cmd.editor_reset_zoom", "Reset Editor Font Zoom"),
            new CommandDef("cmd.import_obsidian", "Import Obsidian Vault…"),
            new CommandDef("cmd.import_enex", "Import Evernote (.enex)…"),
            new CommandDef("cmd.strike", "Strikethrough"),
            new CommandDef("cmd.highlight", "Highlight"),
            new CommandDef("cmd.quote", "Quote"),
            new CommandDef("cmd.code", "Inline Code"),
            new CommandDef("cmd.insert_bullet_list", "Insert Bullet List"));

    void registerDefaultRoutes(RouteRegistrar routeRegistrar, AliasRegistrar aliasRegistrar,
            CommandActionProvider actionProvider) {
        if (routeRegistrar == null || actionProvider == null) {
            return;
        }

        for (CommandDef def : DEFAULT_COMMANDS) {
            Runnable action = actionProvider.actionFor(def.id());
            if (action != null) {
                routeRegistrar.register(def.id(), def.legacyName(), action);
            }
        }

        if (aliasRegistrar != null) {
            aliasRegistrar.registerAlias("Toggle Right Panel", "cmd.toggle_info_panel");
        }
    }
}
