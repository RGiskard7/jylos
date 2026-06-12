package com.example.jylos.plugin;

/**
 * Registry through which plugins add buttons to the main toolbar, keyed by plugin id
 * so a plugin's buttons can be removed wholesale when it is disabled. Mirrors
 * {@link SidePanelRegistry}.
 *
 * @author Edu Díaz (RGiskard7)
 * @since 2.0.0
 */
public interface ToolbarRegistry {

    /**
     * Adds a button to the main toolbar.
     *
     * @param pluginId    owning plugin id
     * @param buttonId    stable id, unique within the plugin (used for replacement)
     * @param tooltip     tooltip text shown on hover
     * @param iconLiteral Ikonli Feather literal (e.g. {@code "fth-clock"}); when
     *                    {@code null}, {@code tooltip} is rendered as the button text
     * @param action      invoked on the JavaFX Application Thread when clicked
     */
    void registerToolbarButton(String pluginId, String buttonId, String tooltip,
            String iconLiteral, Runnable action);

    /** Removes every toolbar button registered by the given plugin (called on disable). */
    void removeToolbarButtons(String pluginId);
}
