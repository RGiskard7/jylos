package com.example.jylos.ui.controller;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import com.example.jylos.tests.FxTestSupport;

/**
 * Every command of the command palette has an action: none is lost when the palette's
 * commands and the menus' actions share one table.
 */
class CommandActionsTest {

    private static final List<String> COMMANDS = List.of(
            "cmd.new_note",
            "cmd.new_canvas",
            "cmd.new_folder",
            "cmd.save",
            "cmd.save_all",
            "cmd.import",
            "cmd.export",
            "cmd.delete_note",
            "cmd.undo",
            "cmd.redo",
            "cmd.find",
            "cmd.replace",
            "cmd.cut",
            "cmd.copy",
            "cmd.paste",
            "cmd.bold",
            "cmd.italic",
            "cmd.underline",
            "cmd.insert_link",
            "cmd.insert_rich_link",
            "cmd.insert_image",
            "cmd.insert_todo",
            "cmd.insert_list",
            "cmd.toggle_sidebar",
            "cmd.toggle_info_panel",
            "cmd.toggle_reading_view",
            "cmd.toggle_source_mode",
            "cmd.split_mode",
            "cmd.zoom_in",
            "cmd.zoom_out",
            "cmd.reset_zoom",
            "cmd.theme_light",
            "cmd.theme_dark",
            "cmd.theme_system",
            "cmd.graph_view",
            "cmd.knowledge_insights",
            "cmd.workspace_save",
            "cmd.workspace_save_as",
            "cmd.workspace_open",
            "cmd.workspace_manage",
            "cmd.git_panel",
            "cmd.git_sync",
            "cmd.git_commit_push",
            "cmd.git_pull",
            "cmd.git_init",
            "cmd.git_add_remote",
            "cmd.quick_switcher",
            "cmd.global_search",
            "cmd.daily_note",
            "cmd.new_from_template",
            "cmd.export_vault",
            "cmd.goto_all_notes",
            "cmd.goto_favorites",
            "cmd.goto_recent",
            "cmd.tag_manager",
            "cmd.preferences",
            "cmd.toggle_favorite",
            "cmd.refresh",
            "cmd.plugins.manage",
            "cmd.keyboard_shortcuts",
            "cmd.documentation",
            "cmd.about",
            "cmd.focus_mode",
            "cmd.kanban_view",
            "cmd.note_history",
            "cmd.toggle_private",
            "cmd.lock_notes",
            "cmd.unlock_notes",
            "cmd.navigate_back",
            "cmd.navigate_forward",
            "cmd.close_note",
            "cmd.switch_storage",
            "cmd.check_updates",
            "cmd.toggle_pin",
            "cmd.toggle_notes_list",
            "cmd.toggle_tags",
            "cmd.new_tag",
            "cmd.list_view",
            "cmd.grid_view",
            "cmd.switch_layout",
            "cmd.editor_zoom_in",
            "cmd.editor_zoom_out",
            "cmd.editor_reset_zoom",
            "cmd.import_obsidian",
            "cmd.import_enex",
            "cmd.strike",
            "cmd.highlight",
            "cmd.quote",
            "cmd.code",
            "cmd.insert_bullet_list");

    @Test
    void everyPaletteCommandHasAnAction() throws Exception {
        Assumptions.assumeTrue(FxTestSupport.isFxRuntimeAvailable());
        List<String> missing = FxTestSupport.onFx(() -> {
            MainController main = new MainController();
            Method resolve = MainController.class.getDeclaredMethod("resolveCommandAction", String.class);
            resolve.setAccessible(true);
            List<String> out = new ArrayList<>();
            for (String id : COMMANDS) {
                if (resolve.invoke(main, id) == null) {
                    out.add(id);
                }
            }
            return out;
        });
        assertTrue(missing.isEmpty(), "commands without an action: " + missing);
    }
}
