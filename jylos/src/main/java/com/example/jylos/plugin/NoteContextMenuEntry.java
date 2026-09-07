package com.example.jylos.plugin;

import java.util.function.Consumer;

import com.example.jylos.data.models.Note;

/**
 * One plugin-contributed item in a note's right-click context menu (the notes
 * list, both its list-view and grid-view cells).
 *
 * @param pluginId the plugin that registered this item, so it can be cleaned up
 *                 when that plugin is disabled/unloaded
 * @param label    the menu item's visible text
 * @param action   invoked with the right-clicked note when the item is chosen
 * @author Edu Díaz (RGiskard7)
 * @since 2.5.6
 */
public record NoteContextMenuEntry(String pluginId, String label, Consumer<Note> action) {
}
