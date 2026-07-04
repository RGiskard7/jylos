package com.example.jylos.ui.theme;

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
 * Discovers user CSS snippets layered over the active theme.
 */
public final class CssSnippetCatalog {

    private static final Logger logger = LoggerConfig.getLogger(CssSnippetCatalog.class);

    public record SnippetDescriptor(String name, String cssUri) {
    }

    public List<SnippetDescriptor> getAvailableSnippets() {
        return scanSnippets(snippetDirectories());
    }

    public List<String> resolveEnabledUris(Set<String> enabledNames) {
        return resolveEnabled(getAvailableSnippets(), enabledNames);
    }

    public static List<SnippetDescriptor> scanSnippets(List<Path> dirs) {
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

    public static List<String> resolveEnabled(List<SnippetDescriptor> available, Set<String> enabledNames) {
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

    public Path primaryDirectory() {
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

    public static boolean isValidSnippetName(String name) {
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
