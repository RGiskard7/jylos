package com.example.jylos.plugin;

import java.util.Map;
import java.util.Set;

/**
 * Jylos's own plugins, by id: Mermaid, compiled into the app, and those built from
 * {@code plugins-source/} and shipped in the installation; and whether each one starts on in a
 * new install.
 *
 * @author Edu Díaz (RGiskard7)
 * @since 2.6.0
 */
public final class CorePlugins {

    /**
     * Each one's state in a new install. Off: those that open a port (MCP Server) or write files
     * outside the vault on their own (Publish, Auto Backup), until the user wants them.
     */
    private static final Map<String, Boolean> ENABLED_BY_DEFAULT = Map.ofEntries(
            Map.entry("mermaid-renderer", true),
            Map.entry("outline", true),
            Map.entry("table-of-contents", true),
            Map.entry("word-count", true),
            Map.entry("reading-time", true),
            Map.entry("templates", true),
            Map.entry("daily-notes", true),
            Map.entry("calendar", true),
            Map.entry("dataview", true),
            Map.entry("mcp-server", false),
            Map.entry("publish", false),
            Map.entry("auto-backup", false));

    /** The names of the JARs the installation ships them as (Mermaid is in the app itself). */
    private static final Set<String> SHIPPED_JARS = Set.of(
            "AutoBackupPlugin.jar", "CalendarPlugin.jar", "DailyNotesPlugin.jar", "DataviewPlugin.jar",
            "McpServerPlugin.jar", "OutlinePlugin.jar", "PublishPlugin.jar", "ReadingTimePlugin.jar",
            "TableOfContentsPlugin.jar", "TemplatesPlugin.jar", "WordCountPlugin.jar");

    private CorePlugins() {
    }

    /** The JAR names they are shipped as. */
    public static Set<String> shippedJarNames() {
        return SHIPPED_JARS;
    }

    public static boolean isCore(String pluginId) {
        return pluginId != null && ENABLED_BY_DEFAULT.containsKey(pluginId);
    }

    /** All of them. */
    public static Set<String> ids() {
        return ENABLED_BY_DEFAULT.keySet();
    }

    /** Whether it starts on in a new install; {@code true} for an id that is not one of Jylos's. */
    public static boolean enabledByDefault(String pluginId) {
        return ENABLED_BY_DEFAULT.getOrDefault(pluginId, true);
    }
}
