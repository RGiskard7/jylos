package com.example.jylos.plugin;

import javafx.scene.Node;

/**
 * Registry through which plugins show their own items in the status bar (word counts, sync
 * state, a clock…), as Obsidian's {@code addStatusBarItem()}. Keyed by plugin id, so a
 * plugin's items are removed wholesale when it is disabled. Mirrors {@link ToolbarRegistry}.
 *
 * @author Edu Díaz (RGiskard7)
 * @since 2.6.0
 */
public interface StatusBarRegistry {

    /**
     * Shows {@code content} in the status bar. The plugin keeps the node and updates it
     * itself (for example {@code label.setText(...)}), as an Obsidian plugin updates the
     * element {@code addStatusBarItem()} returns.
     *
     * @param pluginId owning plugin id
     * @param itemId   stable id, unique within the plugin: registering it again replaces the item
     * @param content  what to show, usually a {@code Label}
     */
    void registerStatusBarItem(String pluginId, String itemId, Node content);

    /** Removes one item of the given plugin. */
    void removeStatusBarItem(String pluginId, String itemId);

    /** Removes every item of the given plugin (called on disable). */
    void removeStatusBarItems(String pluginId);
}
