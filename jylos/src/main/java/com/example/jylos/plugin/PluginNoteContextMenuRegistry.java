package com.example.jylos.plugin;

import java.util.List;
import java.util.function.Consumer;

import com.example.jylos.data.models.Note;

/**
 * Registry for plugin-contributed items in a note's right-click context menu
 * (the notes list, both list-view and grid-view). Mirrors {@link
 * PluginMenuRegistry}'s own shape — a plugin registers, the owning UI queries
 * {@link #getNoteContextMenuItems()} when it builds a note's menu, and
 * everything the plugin added is torn down together on unload/disable.
 *
 * @author Edu Díaz (RGiskard7)
 * @since 2.5.6
 */
public interface PluginNoteContextMenuRegistry {

    /**
     * Registers a note context menu item for a plugin.
     *
     * @param pluginId The plugin ID
     * @param label    The menu item's visible text
     * @param action   Invoked with the right-clicked note when chosen
     */
    void registerNoteContextMenuItem(String pluginId, String label, Consumer<Note> action);

    /**
     * Removes all note context menu items for a plugin.
     * Called when a plugin is disabled or unloaded.
     *
     * @param pluginId The plugin ID
     */
    void removePluginNoteContextMenuItems(String pluginId);

    /**
     * All currently registered note context menu items, across every enabled
     * plugin, in registration order — the UI appends them as-is when it builds
     * a note's context menu.
     */
    List<NoteContextMenuEntry> getNoteContextMenuItems();
}
