package com.example.jylos.plugin;

import java.util.Set;

/**
 * Jylos's own plugins, by id: Mermaid, compiled into the app, and those built from
 * {@code plugins-source/} and shipped in the installation.
 *
 * @author Edu Díaz (RGiskard7)
 * @since 2.6.0
 */
public final class CorePlugins {

    private static final Set<String> IDS = Set.of(
            "mermaid-renderer", "auto-backup", "calendar", "daily-notes", "dataview", "mcp-server",
            "outline", "publish", "reading-time", "table-of-contents", "templates", "word-count");

    private CorePlugins() {
    }

    public static boolean isCore(String pluginId) {
        return pluginId != null && IDS.contains(pluginId);
    }

    /** All of them. */
    public static Set<String> ids() {
        return IDS;
    }
}
