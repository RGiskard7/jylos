package com.example.jylos.plugin;

/**
 * Interface that all Jylos plugins must implement.
 * 
 * <p>Plugins extend the functionality of Jylos by:</p>
 * <ul>
 *   <li>Registering commands in the Command Palette</li>
 *   <li>Registering menu items dynamically</li>
 *   <li>Subscribing to application events</li>
 *   <li>Accessing notes, folders, and tags through services</li>
 *   <li>Creating custom UI dialogs and side panels</li>
 * </ul>
 * 
 * @author Edu Díaz (RGiskard7)
 * @since 1.2.0
 */
public interface Plugin {
    
    /**
     * Gets the unique identifier for this plugin.
     * Should be lowercase with hyphens (e.g., "word-count").
     * 
     * @return The plugin ID
     */
    String getId();
    
    /**
     * Gets the display name of this plugin.
     * 
     * @return The plugin name
     */
    String getName();
    
    /**
     * Gets the version of this plugin.
     * Should follow semantic versioning (e.g., "1.0.0").
     * 
     * @return The plugin version
     */
    String getVersion();
    
    /**
     * Initializes the plugin with the given context.
     * This is called when the plugin is loaded and enabled.
     * 
     * @param context The plugin context providing access to services and UI registration
     */
    void initialize(PluginContext context);
    
    /**
     * Shuts down the plugin.
     * This is called when the plugin is disabled or the application is closing.
     * Plugins should clean up resources, unregister commands, and remove UI components.
     */
    void shutdown();
    
    /**
     * Gets the description of this plugin.
     * 
     * @return The plugin description, or empty string if not provided
     */
    default String getDescription() {
        return "";
    }
    
    /**
     * Gets the author of this plugin.
     * 
     * @return The plugin author, or empty string if not provided
     */
    default String getAuthor() {
        return "";
    }
    
    /**
     * Checks if this plugin is enabled.
     * 
     * @return true if enabled, false otherwise
     */
    default boolean isEnabled() {
        return true;
    }
    
    /**
     * Gets the priority for loading this plugin.
     * Lower values are loaded first. Default is 100.
     * 
     * @return The priority value
     */
    default int getPriority() {
        return 100;
    }
    
    /**
     * Gets the list of plugin IDs that this plugin depends on.
     * 
     * @return Array of plugin IDs, or empty array if no dependencies
     */
    default String[] getDependencies() {
        return new String[0];
    }
}
