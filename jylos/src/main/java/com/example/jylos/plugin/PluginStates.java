package com.example.jylos.plugin;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.logging.Logger;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;
import java.util.stream.Stream;

import com.example.jylos.config.LoggerConfig;

/**
 * Whether each plugin is on, kept in the plugin manager's preferences as
 * {@code plugin.enabled.<id>}. Without that key: the older {@code plugin.disabled.<id>} if it is
 * there; otherwise a community plugin is on and one of Jylos's own is as {@link CorePlugins} says.
 *
 * @author Edu Díaz (RGiskard7)
 * @since 2.6.0
 */
public final class PluginStates {

    private static final Logger logger = LoggerConfig.getLogger(PluginStates.class);

    static final String ENABLED_PREFIX = "plugin.enabled.";
    /** Written by older versions, which only stored a plugin turned off. */
    static final String DISABLED_PREFIX = "plugin.disabled.";
    /** Set once {@link #initialize} has decided; its value is the version of that decision. */
    static final String DEFAULTS_VERSION_KEY = "plugins.defaults.version";
    /** Whether community plugins are loaded (restricted mode off). */
    static final String COMMUNITY_KEY = "plugins.community.enabled";

    private final Preferences node;

    public PluginStates(Preferences node) {
        this.node = node;
    }

    /**
     * On the first start of a version with per-plugin defaults: if Jylos {@code ranBefore}, writes
     * down the state each of its own plugins had, every one on unless the user turned it off, so
     * the new defaults only reach new installs. Done once.
     */
    public static void initialize(Preferences node, boolean ranBefore) {
        if (node.get(DEFAULTS_VERSION_KEY, null) != null) {
            return;
        }
        if (ranBefore) {
            for (String id : CorePlugins.ids()) {
                if (node.get(ENABLED_PREFIX + id, null) == null) {
                    node.putBoolean(ENABLED_PREFIX + id, !node.getBoolean(DISABLED_PREFIX + id, false));
                }
                node.remove(DISABLED_PREFIX + id);
            }
        }
        node.put(DEFAULTS_VERSION_KEY, "1");
        try {
            node.flush();
        } catch (BackingStoreException e) {
            logger.warning("Could not store the plugins' states: " + e.getMessage());
        }
    }

    public boolean isEnabled(String pluginId, PluginOrigin origin) {
        String chosen = node.get(ENABLED_PREFIX + pluginId, null);
        if (chosen != null) {
            return Boolean.parseBoolean(chosen);
        }
        if (node.get(DISABLED_PREFIX + pluginId, null) != null) {
            return !node.getBoolean(DISABLED_PREFIX + pluginId, false);
        }
        return origin != PluginOrigin.CORE || CorePlugins.enabledByDefault(pluginId);
    }

    /**
     * Whether community plugins are loaded. The first time it is asked, it is decided once: yes
     * if the user's plugins folder already holds plugins of their own (they had them before
     * updating), no otherwise (a new install starts in restricted mode). Copies of Jylos's own
     * plugins in that folder do not count.
     */
    public boolean communityAllowed(Path userPluginsDir) {
        String stored = node.get(COMMUNITY_KEY, null);
        if (stored != null) {
            return Boolean.parseBoolean(stored);
        }
        boolean allowed = holdsOwnPlugins(userPluginsDir);
        node.putBoolean(COMMUNITY_KEY, allowed);
        return allowed;
    }

    public void setCommunityAllowed(boolean allowed) {
        node.putBoolean(COMMUNITY_KEY, allowed);
    }

    private static boolean holdsOwnPlugins(Path dir) {
        if (dir == null || !Files.isDirectory(dir)) {
            return false;
        }
        try (Stream<Path> files = Files.list(dir)) {
            return files.map(f -> f.getFileName().toString())
                    .filter(name -> name.toLowerCase(Locale.ROOT).endsWith(".jar"))
                    .anyMatch(name -> !CorePlugins.shippedJarNames().contains(name)
                            && !RetiredPlugins.isRetiredJarName(name));
        } catch (IOException e) {
            logger.warning("Could not read the plugins folder " + dir + ": " + e.getMessage());
            return false;
        }
    }

    public void setEnabled(String pluginId, boolean enabled) {
        node.putBoolean(ENABLED_PREFIX + pluginId, enabled);
        node.remove(DISABLED_PREFIX + pluginId);
    }

    /** Forgets what was chosen for the plugin: it goes back to its default. */
    public void forget(String pluginId) {
        node.remove(ENABLED_PREFIX + pluginId);
        node.remove(DISABLED_PREFIX + pluginId);
    }
}
