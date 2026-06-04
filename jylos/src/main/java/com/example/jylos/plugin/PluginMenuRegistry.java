package com.example.jylos.plugin;

/**
 * Registry for plugin menu items.
 * Allows plugins to register menu items dynamically in categorized submenus.
 * 
 * @author Edu Díaz (RGiskard7)
 * @since 1.2.0
 */
public interface PluginMenuRegistry {
    
    /**
     * Registers a menu item for a plugin.
     * 
     * @param pluginId The plugin ID
     * @param category The menu category (e.g., "Core", "Productivity", "AI")
     * @param itemName The menu item name
     * @param action The action to execute
     */
    void registerMenuItem(String pluginId, String category, String itemName, Runnable action);
    
    /**
     * Registers a menu item with a keyboard shortcut.
     * 
     * @param pluginId The plugin ID
     * @param category The menu category
     * @param itemName The menu item name
     * @param shortcut The keyboard shortcut (e.g., "Ctrl+Shift+W")
     * @param action The action to execute
     */
    void registerMenuItem(String pluginId, String category, String itemName, String shortcut, Runnable action);
    
    /**
     * Adds a separator in a plugin's menu category.
     * 
     * @param pluginId The plugin ID
     * @param category The menu category
     */
    void addMenuSeparator(String pluginId, String category);
    
    /**
     * Removes all menu items for a plugin.
     * Called when a plugin is disabled or unloaded.
     * 
     * @param pluginId The plugin ID
     */
    void removePluginMenuItems(String pluginId);
    
    /**
     * Checks if a plugin is enabled.
     * 
     * @param pluginId The plugin ID
     * @return true if enabled, false otherwise
     */
    boolean isPluginEnabled(String pluginId);
}
