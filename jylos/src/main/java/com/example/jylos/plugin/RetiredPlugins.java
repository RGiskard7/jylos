package com.example.jylos.plugin;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

import com.example.jylos.config.LoggerConfig;

/**
 * Plugins Jylos no longer has. They are never loaded again, whichever folder their JAR is in,
 * and what they left on a user's machine is removed: their settings, and the copy that older
 * versions of Jylos put in the user's plugins folder (marked by {@link #BUNDLED_COPY_MARKER}).
 * A JAR the user put there without that mark is left alone; it is just not loaded.
 *
 * @author Edu Díaz (RGiskard7)
 * @since 2.6.0
 */
public final class RetiredPlugins {

    private static final Logger logger = LoggerConfig.getLogger(RetiredPlugins.class);

    /** Left in the user's plugins folder by the older versions that copied their plugins there. */
    static final String BUNDLED_COPY_MARKER = ".bundled-plugins-copied";

    /** Each retired plugin's id and the JAR name Jylos shipped it as. */
    private static final Map<String, String> RETIRED = Map.of(
            // The AI assistant: an early trial, removed. It kept an API key in its settings.
            "ai-assistant", "AIPlugin.jar");

    private RetiredPlugins() {
    }

    public static boolean isRetired(String pluginId) {
        return pluginId != null && RETIRED.containsKey(pluginId);
    }

    /**
     * Removes what retired plugins left: their settings under {@code pluginsNode} (the plugin
     * manager's preferences) and the JARs Jylos copied into {@code userPluginsDir}.
     *
     * @return what was removed, one line each, for the log; empty when there was nothing
     */
    public static List<String> cleanUp(Path userPluginsDir, Preferences pluginsNode) {
        List<String> done = new ArrayList<>();
        boolean copiedByJylos = userPluginsDir != null && Files.exists(userPluginsDir.resolve(BUNDLED_COPY_MARKER));
        for (Map.Entry<String, String> retired : RETIRED.entrySet()) {
            String id = retired.getKey();
            if (copiedByJylos) {
                Path jar = userPluginsDir.resolve(retired.getValue());
                try {
                    if (Files.deleteIfExists(jar)) {
                        done.add("Removed retired plugin " + id + ": " + jar);
                    }
                } catch (IOException e) {
                    logger.warning("Could not remove retired plugin " + jar + ": " + e.getMessage());
                }
            }
            if (pluginsNode != null) {
                try {
                    boolean settings = pluginsNode.nodeExists(id);
                    if (settings) {
                        pluginsNode.node(id).removeNode();
                    }
                    boolean state = pluginsNode.get(PluginStates.DISABLED_PREFIX + id, null) != null
                            || pluginsNode.get(PluginStates.ENABLED_PREFIX + id, null) != null;
                    new PluginStates(pluginsNode).forget(id);
                    if (settings || state) {
                        pluginsNode.flush();
                        done.add("Removed the settings of retired plugin " + id);
                    }
                } catch (BackingStoreException | IllegalStateException e) {
                    logger.warning("Could not remove the settings of retired plugin " + id + ": " + e.getMessage());
                }
            }
        }
        return done;
    }
}
