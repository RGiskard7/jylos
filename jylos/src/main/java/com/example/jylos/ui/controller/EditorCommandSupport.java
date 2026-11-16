package com.example.jylos.ui.controller;

import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;

import com.example.jylos.event.events.SystemActionEvent.ActionType;

/**
 * The window's commands that act on the open editor's own bars and views: the tags bar, the
 * reading view and the format bar. Each does nothing while there is no editor.
 *
 * @author Edu Díaz (RGiskard7)
 * @since 2.6.0
 */
final class EditorCommandSupport {

    private EditorCommandSupport() {
    }

    static void register(Map<ActionType, Runnable> handlers, Supplier<EditorController> editor) {
        handlers.put(ActionType.TOGGLE_TAGS, () -> onEditor(editor, EditorController::toggleTagsBar));
        handlers.put(ActionType.TOGGLE_READING_MODE, () -> onEditor(editor, EditorController::toggleReadingMode));
        handlers.put(ActionType.TOGGLE_FORMAT_TOOLBAR, () -> onEditor(editor, e -> e.formatToolbar().toggle()));
    }

    private static void onEditor(Supplier<EditorController> editor, Consumer<EditorController> action) {
        EditorController controller = editor.get();
        if (controller != null) {
            action.accept(controller);
        }
    }
}
