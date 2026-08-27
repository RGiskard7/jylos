package com.example.jylos.ui.controller;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

/**
 * Every command added to the palette must actually resolve to a real action — a typo in
 * either {@link CommandRegistry#DEFAULT_COMMANDS} or {@code MainController}'s
 * {@code resolveCommandAction} switch leaves a palette entry that does nothing when
 * clicked, silently. This calls the real, private {@code resolveCommandAction} via
 * reflection (no FXML/UI needed — the method only builds and returns method
 * references/lambdas, it doesn't invoke them) so a missing case shows up as a test
 * failure instead of a dead menu entry a user finds by hand.
 */
class CommandRegistryCoverageTest {

    // Added alongside the Focus Mode toolbar button and a full audit of
    // SystemActionEvent.ActionType against the palette — see CommandRegistry for the
    // full catalog. Listed explicitly (not read via reflection off DEFAULT_COMMANDS)
    // so this test also catches an id typo'd identically wrong in both places.
    private static final List<String> NEWLY_ADDED_COMMAND_IDS = List.of(
            "cmd.focus_mode", "cmd.kanban_view", "cmd.note_history", "cmd.toggle_private",
            "cmd.lock_notes", "cmd.unlock_notes", "cmd.navigate_back", "cmd.navigate_forward",
            "cmd.close_note", "cmd.switch_storage", "cmd.check_updates", "cmd.toggle_pin",
            "cmd.toggle_notes_list", "cmd.toggle_tags", "cmd.new_tag", "cmd.list_view",
            "cmd.grid_view", "cmd.switch_layout", "cmd.editor_zoom_in", "cmd.editor_zoom_out",
            "cmd.editor_reset_zoom", "cmd.import_obsidian", "cmd.import_enex", "cmd.strike",
            "cmd.highlight", "cmd.quote", "cmd.code", "cmd.insert_bullet_list");

    @Test
    void everyNewlyAddedCommandResolvesToARealAction() throws Exception {
        MainController controller = new MainController();
        Method resolve = MainController.class.getDeclaredMethod("resolveCommandAction", String.class);
        resolve.setAccessible(true);

        for (String commandId : NEWLY_ADDED_COMMAND_IDS) {
            Object action = resolve.invoke(controller, commandId);
            assertNotNull(action, "cmd id '" + commandId + "' does not resolve to an action "
                    + "— missing case in MainController.resolveCommandAction");
        }
    }

    @Test
    void everyNewlyAddedCommandIsInTheDefaultRegistry() {
        Set<String> registeredIds = new HashSet<>();
        CommandRegistry registry = new CommandRegistry();
        registry.registerDefaultRoutes(
                (id, legacyName, action) -> registeredIds.add(id),
                (alias, commandId) -> { },
                commandId -> () -> { });

        for (String commandId : NEWLY_ADDED_COMMAND_IDS) {
            assertTrue(registeredIds.contains(commandId),
                    "cmd id '" + commandId + "' is missing from CommandRegistry.DEFAULT_COMMANDS");
        }
    }

    @Test
    void unknownCommandIdsStillResolveToNull() throws Exception {
        MainController controller = new MainController();
        Method resolve = MainController.class.getDeclaredMethod("resolveCommandAction", String.class);
        resolve.setAccessible(true);

        assertTrue(resolve.invoke(controller, "cmd.this_id_does_not_exist") == null);
    }
}
