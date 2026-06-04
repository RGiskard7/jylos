package com.example.jylos.plugin;

/**
 * Naming helpers for plugin commands and stable identifiers.
 */
public final class PluginIds {

    public static final String COMMAND_PREFIX = "plugin.";

    private PluginIds() {
    }

    /**
     * Builds a stable command-palette id: {@code plugin.<pluginId>.<slug>}.
     */
    public static String commandId(String pluginId, String commandLabel) {
        if (pluginId == null || pluginId.isBlank()) {
            return COMMAND_PREFIX + slug(commandLabel);
        }
        return COMMAND_PREFIX + pluginId.trim() + "." + slug(commandLabel);
    }

    static String slug(String value) {
        if (value == null || value.isBlank()) {
            return "command";
        }
        String normalized = value.trim().toLowerCase()
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-+|-+$", "");
        return normalized.isEmpty() ? "command" : normalized;
    }
}
