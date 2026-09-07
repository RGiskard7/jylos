package com.example.jylos.plugin;

import java.util.Locale;
import java.util.MissingResourceException;
import java.util.ResourceBundle;

/**
 * Loads a plugin's own translations, in the app's current language.
 *
 * <p>Plugins run their menu registrations, dialogs and alerts through here instead of
 * hardcoding English so they follow whichever language the user picked in Settings — the
 * same one the core app's own UI uses. {@link Locale#getDefault()} is authoritative for
 * that: {@code Main} calls {@link Locale#setDefault(Locale)} once at startup (and
 * {@code AppSettings} again on every language change) from the persisted "language"
 * preference, so it reflects the live choice for the whole process, plugins included —
 * nothing plugin-specific needs threading through {@link PluginContext} for this.</p>
 *
 * <p>Each plugin ships its own {@code messages.properties} / {@code messages_es.properties}
 * (etc.) next to its source, in its own package — not entries added to the core app's
 * {@code com.example.jylos.i18n.messages} bundle, which only the core UI owns. {@code
 * scripts/build-plugins.sh} copies these resource files into the built plugin JAR
 * alongside the compiled classes so they are actually there to load at runtime.</p>
 *
 * @author Edu Díaz (RGiskard7)
 */
public final class PluginI18n {

    private PluginI18n() {
    }

    /**
     * Loads {@code <pluginClass's package>.messages} for the app's current language, resolved
     * through the plugin's own classloader (so it finds the bundle inside the plugin's JAR,
     * not the app's).
     *
     * @param pluginClass any class from the plugin whose bundle should be loaded — typically
     *                    {@code getClass()} on the {@link Plugin} implementation itself
     */
    public static ResourceBundle bundle(Class<?> pluginClass) {
        String baseName = pluginClass.getPackageName() + ".messages";
        try {
            return ResourceBundle.getBundle(baseName, Locale.getDefault(), pluginClass.getClassLoader());
        } catch (MissingResourceException e) {
            return null; // no messages.properties shipped (yet) — callers fall back to `fallback` in tr()
        }
    }

    /**
     * Looks up {@code key} in {@code bundle}, or returns {@code fallback} (the English
     * string a plugin would otherwise have hardcoded) if the bundle is {@code null} (not
     * shipped) or missing that specific key — a plugin mid-translation, or one that failed
     * to package its resources, degrades to readable English instead of throwing or
     * showing a raw bundle key like {@code "!menu.publish!"}.
     */
    public static String tr(ResourceBundle bundle, String key, String fallback) {
        if (bundle == null) {
            return fallback;
        }
        try {
            return bundle.getString(key);
        } catch (MissingResourceException e) {
            return fallback;
        }
    }
}
