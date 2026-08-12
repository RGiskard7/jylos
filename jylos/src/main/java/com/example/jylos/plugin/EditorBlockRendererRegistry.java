package com.example.jylos.plugin;

/**
 * Registry the shell implements so plugins can render fenced blocks inside the editor.
 *
 * <p>Mirrors {@link PreviewEnhancerRegistry}: the plugin layer declares the contract and
 * the UI layer owns the wiring, keeping {@code PluginContext} free of editor internals.</p>
 *
 * @author Edu Díaz (RGiskard7)
 * @since 2.5.0
 */
public interface EditorBlockRendererRegistry {

    /**
     * Registers a renderer for a fenced-block language.
     *
     * @param pluginId the registering plugin
     * @param language the info string to match, compared case-insensitively (e.g. {@code "dataview"})
     * @param renderer the renderer invoked for each matching block
     */
    void registerEditorBlockRenderer(String pluginId, String language, EditorBlockRenderer renderer);

    /**
     * Removes every renderer registered by a plugin (called when it is disabled).
     *
     * @param pluginId the plugin whose renderers should be dropped
     */
    void unregisterEditorBlockRenderers(String pluginId);
}
