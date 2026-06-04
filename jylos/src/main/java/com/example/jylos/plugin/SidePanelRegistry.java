package com.example.jylos.plugin;

import javafx.scene.Node;

/**
 * Registry for plugin side panels.
 * Allows plugins to register custom UI panels in the right sidebar.
 * 
 * @author Edu Díaz (RGiskard7)
 * @since 1.2.0
 */
public interface SidePanelRegistry {
    
    /**
     * Registers a side panel for a plugin.
     * 
     * @param pluginId The plugin ID
     * @param panelId The unique panel ID
     * @param title The panel title
     * @param content The panel content (JavaFX Node)
     * @param icon The icon (emoji or text), can be null
     */
    void registerSidePanel(String pluginId, String panelId, String title, Node content, String icon);
    
    /**
     * Removes a side panel.
     * 
     * @param pluginId The plugin ID
     * @param panelId The panel ID to remove
     */
    void removeSidePanel(String pluginId, String panelId);
    
    /**
     * Removes all side panels for a plugin.
     * 
     * @param pluginId The plugin ID
     */
    void removeAllSidePanels(String pluginId);
    
    /**
     * Shows or hides the plugin panels section.
     * 
     * @param visible true to show, false to hide
     */
    void setPluginPanelsVisible(boolean visible);
    
    /**
     * Checks if the plugin panels section is visible.
     * 
     * @return true if visible, false otherwise
     */
    boolean isPluginPanelsVisible();
}
