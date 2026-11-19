package com.example.jylos.plugin;

/**
 * Where a plugin comes from.
 *
 * @author Edu Díaz (RGiskard7)
 * @since 2.6.0
 */
public enum PluginOrigin {
    /** Jylos's own: comes with the installation and is updated with it; it can be turned off, not removed. */
    CORE,
    /** Installed by the user in their plugins folder. */
    COMMUNITY
}
