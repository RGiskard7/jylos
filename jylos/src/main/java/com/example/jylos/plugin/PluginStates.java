package com.example.jylos.plugin;

import java.util.logging.Logger;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

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
