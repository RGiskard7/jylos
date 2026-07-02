package com.example.jylos.ui.controller;

import java.util.List;

/**
 * Catalog of stable command IDs and legacy aliases, registered in one place.
 */
class CommandRegistry {

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
            new CommandDef("cmd.new_canvas", "New Canvas"),
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
            new CommandDef("cmd.editor_mode", "Editor Mode"),
            new CommandDef("cmd.preview_mode", "Preview Mode"),
            new CommandDef("cmd.split_mode", "Split Mode"),
            new CommandDef("cmd.zoom_in", "Zoom In"),
            new CommandDef("cmd.zoom_out", "Zoom Out"),
            new CommandDef("cmd.reset_zoom", "Reset Zoom"),
            new CommandDef("cmd.theme_light", "Light Theme"),
            new CommandDef("cmd.theme_dark", "Dark Theme"),
            new CommandDef("cmd.theme_system", "System Theme"),
            new CommandDef("cmd.quick_switcher", "Quick Switcher"),
            new CommandDef("cmd.global_search", "Global Search"),
            new CommandDef("cmd.daily_note", "Open Today's Daily Note"),
            new CommandDef("cmd.new_from_template", "New Note from Template…"),
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
            new CommandDef("cmd.about", "About Jylos"));

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
