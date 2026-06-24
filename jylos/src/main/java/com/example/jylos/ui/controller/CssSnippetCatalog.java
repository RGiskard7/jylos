package com.example.jylos.ui.controller;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

import com.example.jylos.AppDataDirectory;
import com.example.jylos.config.LoggerConfig;

/**
 * Discovers user CSS snippets: small, passive stylesheets the user drops into the
 * {@code snippets/} folder to tweak the interface on top of the active theme
 * (Obsidian-style). Mirrors {@link ThemeCatalog} in spirit — it only locates and
 * validates files; layering them onto the scene is {@link ThemeCommand}'s job.
 *
 * <p>Snippets are deliberately additive: they are appended <em>after</em> the
 * theme stylesheet, so their rules win. A snippet name must be a plain
 * {@code .css} filename with no path segments, so a snippet can never reference a
 * file outside its folder.</p>
 *
 * <p>Both the writable app-data folder ({@code <appData>/snippets}) and a
 * project-local {@code snippets/} folder (handy in development) are scanned; the
 * first occurrence of a given filename wins.</p>
 *
 * @author Edu Díaz (RGiskard7)
 * @since 2.2.0
 */
final class CssSnippetCatalog {

    private static final Logger logger = LoggerConfig.getLogger(CssSnippetCatalog.class);

    /** A discovered snippet: its filename and the {@code file:} URI to load. */
    record SnippetDescriptor(String name, String cssUri) {
    }

    /** Lists the snippet files found in the snippet directories, sorted by name. */
    List<SnippetDescriptor> getAvailableSnippets() {
        return scanSnippets(snippetDirectories());
    }

    /**
     * Resolves the {@code file:} URIs of the enabled snippets, in catalog order.
     * Enabled names that are no longer present are skipped so deleting a snippet
     * file never breaks startup.
     *
     * @param enabledNames filenames the user has switched on (may be {@code null})
     * @return URIs to add to the scene, possibly empty, never {@code null}
     */
    List<String> resolveEnabledUris(Set<String> enabledNames) {
        return resolveEnabled(getAvailableSnippets(), enabledNames);
    }

    /**
     * Scans the given directories for valid {@code .css} snippet files. The first
     * occurrence of a filename wins, so an app-data snippet shadows a project-local
     * one of the same name. Extracted as a static seam so it is unit-testable
     * against a temporary directory.
     */
    static List<SnippetDescriptor> scanSnippets(List<Path> dirs) {
        Map<String, SnippetDescriptor> unique = new LinkedHashMap<>();
        for (Path dir : dirs) {
            if (dir == null || !Files.isDirectory(dir)) {
                continue;
            }
            try (var files = Files.list(dir)) {
                files.filter(Files::isRegularFile)
                        .filter(p -> isValidSnippetName(p.getFileName().toString()))
                        .forEach(p -> {
                            String name = p.getFileName().toString();
                            unique.putIfAbsent(name, new SnippetDescriptor(name, p.toUri().toString()));
                        });
            } catch (Exception e) {
                logger.warning("Could not read snippets directory '" + dir + "': " + e.getMessage());
            }
        }
        List<SnippetDescriptor> out = new ArrayList<>(unique.values());
        out.sort((a, b) -> a.name().compareToIgnoreCase(b.name()));
        return out;
    }

    /** Filters {@code available} to the {@code file:} URIs whose names are enabled, preserving order. */
    static List<String> resolveEnabled(List<SnippetDescriptor> available, Set<String> enabledNames) {
        if (available == null || enabledNames == null || enabledNames.isEmpty()) {
            return List.of();
        }
        List<String> uris = new ArrayList<>();
        for (SnippetDescriptor snippet : available) {
            if (enabledNames.contains(snippet.name())) {
                uris.add(snippet.cssUri());
            }
        }
        return uris;
    }

    /** The writable snippets folder ({@code <appData>/snippets}), created on demand. */
    Path primaryDirectory() {
        Path dir = Paths.get(AppDataDirectory.getSnippetsDirectory());
        try {
            Files.createDirectories(dir);
        } catch (Exception e) {
            logger.warning("Could not create snippets directory '" + dir + "': " + e.getMessage());
        }
        return dir;
    }

    private List<Path> snippetDirectories() {
        return List.of(
                Paths.get(AppDataDirectory.getSnippetsDirectory()),
                Paths.get(System.getProperty("user.dir", "."), "snippets"));
    }

    /**
     * A valid snippet name is a single {@code .css} filename made of safe
     * characters — never a path. This mirrors the rule Glyphary enforces and
     * guarantees a snippet can only ever live inside its own folder.
     */
    static boolean isValidSnippetName(String name) {
        if (name == null) {
            return false;
        }
        String n = name.trim();
        return !n.isEmpty()
                && n.length() <= 120
                && n.toLowerCase(Locale.ROOT).endsWith(".css")
                && n.indexOf('/') < 0
                && n.indexOf('\\') < 0
                && !n.contains("..")
                && n.chars().allMatch(c -> Character.isLetterOrDigit(c)
                        || c == '-' || c == '_' || c == '.' || c == ' ');
    }
}
