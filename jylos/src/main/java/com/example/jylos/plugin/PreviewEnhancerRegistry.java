package com.example.jylos.plugin;

/**
 * Interface that the MainController must implement to allow plugins to register
 * preview enhancers.
 * 
 * @author Edu Díaz (RGiskard7)
 * @since 1.5.0
 */
public interface PreviewEnhancerRegistry {
    /**
     * Registers a preview enhancer.
     * 
     * @param pluginId The ID of the plugin registering the enhancer
     * @param enhancer The enhancer instance
     */
    void registerPreviewEnhancer(String pluginId, PreviewEnhancer enhancer);

    /**
     * Unregisters a preview enhancer.
     * 
     * @param pluginId The ID of the plugin to unregister
     */
    void unregisterPreviewEnhancer(String pluginId);
}
