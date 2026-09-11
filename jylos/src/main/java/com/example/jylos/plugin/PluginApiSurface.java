package com.example.jylos.plugin;

import java.util.List;
import java.util.Set;

/**
 * The single source of truth for which {@code com.example.jylos.*} classes a plugin may
 * reach on the app's own classloader. {@link PluginClassLoader} enforces it at load time;
 * {@code PluginContractGuardTest} enforces it at build time by scanning every built-in
 * plugin's imports against it, so the two cannot silently drift apart.
 *
 * <p>Deliberately narrow, and deliberately not the whole of {@code com.example.jylos}:
 * most of the app (UI controllers, DAOs, the database layer, …) is internal implementation
 * a plugin has no documented right to depend on — see {@link PluginContext} for the actual
 * plugin API. Everything outside {@code com.example.jylos} (the JDK, JavaFX, and any
 * third-party library the app itself bundles, like Gson or commonmark) is unaffected by
 * this list; it only ever gates the app's own internal namespace.</p>
 *
 * <p>The two lists exist for a reason: whole packages ({@link #ALLOWED_PACKAGE_PREFIXES})
 * are genuinely part of the plugin-facing domain model or service layer, safe to expose
 * wholesale; individual classes ({@link #ALLOWED_CLASSES}) are specific, stable utilities
 * outside those packages that real built-in plugins already depend on (rendering
 * Markdown/wiki-links the same way the live preview does, checking whether a note is an
 * attachment) without pulling in everything else their package happens to contain — some
 * of which (a `WebView`-facing HTML builder, for one) has no business being plugin-facing.</p>
 *
 * @author Edu Díaz (RGiskard7)
 * @since 4.6.0
 */
public final class PluginApiSurface {

    private PluginApiSurface() {
    }

    /**
     * Package prefixes (each already ending in a dot) a plugin may reach in full.
     */
    public static final List<String> ALLOWED_PACKAGE_PREFIXES = List.of(
            "com.example.jylos.plugin.",
            "com.example.jylos.data.models.",
            "com.example.jylos.service.",
            "com.example.jylos.event.",
            "com.example.jylos.graph.");

    /**
     * Individual classes, outside the packages above, that are still sanctioned.
     */
    public static final Set<String> ALLOWED_CLASSES = Set.of(
            "com.example.jylos.util.MarkdownProcessor",
            "com.example.jylos.util.WikiLinkResolver",
            "com.example.jylos.util.AttachmentType");

    /**
     * Whether {@code className} (a binary class name, e.g. {@code "com.example.jylos.data.models.Note"})
     * is part of the documented plugin-facing surface.
     *
     * <p>Only meaningful for names inside the {@code com.example.jylos} namespace — the
     * JDK, JavaFX and third-party libraries are never gated by this class at all, so
     * callers should not (and {@link PluginClassLoader} does not) consult this method for
     * those.</p>
     *
     * @param className the fully qualified class name being resolved
     * @return {@code true} if a plugin may load this class from the app's own classloader
     */
    public static boolean isAllowed(String className) {
        if (className == null) {
            return false;
        }
        for (String prefix : ALLOWED_PACKAGE_PREFIXES) {
            if (className.startsWith(prefix)) {
                return true;
            }
        }
        return ALLOWED_CLASSES.contains(className);
    }
}
