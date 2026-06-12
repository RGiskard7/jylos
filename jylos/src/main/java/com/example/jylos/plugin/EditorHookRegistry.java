package com.example.jylos.plugin;

/**
 * Registry through which plugins contribute {@link EditorHook}s, keyed by plugin id
 * so a plugin's hooks can be removed wholesale when it is disabled. Mirrors
 * {@link PreviewEnhancerRegistry}/{@link SidePanelRegistry}.
 *
 * @author Edu Díaz (RGiskard7)
 * @since 2.0.0
 */
public interface EditorHookRegistry {

    /**
     * Registers an editor hook for a plugin. A plugin may register several hooks;
     * they run in registration order.
     */
    void registerEditorHook(String pluginId, EditorHook hook);

    /** Removes every hook registered by the given plugin (called on disable). */
    void unregisterEditorHooks(String pluginId);
}
