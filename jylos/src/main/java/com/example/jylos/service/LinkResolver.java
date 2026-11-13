package com.example.jylos.service;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Resolves a wiki link to a file of the vault the way Obsidian does: by path, not by title.
 *
 * <p>A link names a file by its name or by a (partial or relative) path. When several files
 * share the name, the one in the source note's folder (or below it) wins, then the shortest
 * path. Case and Unicode form are ignored, the heading ({@code #…}) and display text
 * ({@code |…}) are dropped, {@code .md} may be omitted, and aliases are tried last.</p>
 *
 * <p>Paths use {@code /} and are relative to the vault root, with extension
 * ({@code Carpeta/Nota.md}, {@code Adjuntos/foto.png}). Pure and immutable.</p>
 */
public final class LinkResolver {

    /** One file of the vault: its path and whether it is a Markdown note. */
    public record Entry(String path, boolean markdown) {
    }

    private final Set<String> paths = new HashSet<>();
    /** Lower-cased NFC file name → paths of the files with that name. */
    private final Map<String, List<String>> byName = new HashMap<>();
    /** Path → its lower-cased NFC form, computed once rather than on every resolution. */
    private final Map<String, String> folded = new HashMap<>();
    /** Lower-cased NFC alias → path. */
    private final Map<String, String> aliases = new HashMap<>();

    public LinkResolver(List<Entry> entries, Map<String, String> aliases) {
        for (Entry entry : entries) {
            if (entry == null || entry.path() == null || entry.path().isBlank()) {
                continue;
            }
            paths.add(entry.path());
            folded.put(entry.path(), fold(entry.path()));
            byName.computeIfAbsent(fold(basename(entry.path())), k -> new ArrayList<>()).add(entry.path());
        }
        if (aliases != null) {
            aliases.forEach((alias, path) -> {
                if (alias != null && !alias.isBlank() && path != null) {
                    this.aliases.putIfAbsent(fold(alias.trim()), path);
                }
            });
        }
    }

    /**
     * The path of the file {@code rawLink} points to from the note at {@code sourcePath},
     * or empty when it points to nothing. A link with only a heading ({@code #Sección})
     * points to the source note itself.
     */
    public Optional<String> resolve(String rawLink, String sourcePath) {
        if (rawLink == null) {
            return Optional.empty();
        }
        String link = stripTarget(nfc(rawLink));
        String source = sourcePath != null ? sourcePath : "";
        if (link.isEmpty()) {
            boolean headingOnly = rawLink.strip().startsWith("#");
            return headingOnly && paths.contains(source) ? Optional.of(source) : Optional.empty();
        }
        Optional<String> direct = first(link, source);
        if (direct.isEmpty()) {
            direct = first(link + ".md", source);
        }
        if (direct.isPresent()) {
            return direct;
        }
        return Optional.ofNullable(aliases.get(fold(link)));
    }

    /**
     * Whether {@code rawLink} points to the same file from every note: it is not relative, it
     * is not a link to a heading of the note itself, and no two files share its name (with or
     * without {@code .md}), so the folder it is written in never decides.
     */
    boolean sameFromEveryFolder(String rawLink) {
        if (rawLink == null || rawLink.strip().startsWith("#")) {
            return false;
        }
        String link = stripTarget(nfc(rawLink));
        if (link.isEmpty()) {
            return false;
        }
        String name = fold(link);
        if (name.startsWith("./") || name.startsWith("../")) {
            return false;
        }
        return atMostOne(byName.get(basename(name))) && atMostOne(byName.get(basename(name + ".md")));
    }

    private static boolean atMostOne(List<String> paths) {
        return paths == null || paths.size() <= 1;
    }

    /** True when {@code rawLink} points to some file from {@code sourcePath}. */
    public boolean exists(String rawLink, String sourcePath) {
        return resolve(rawLink, sourcePath).isPresent();
    }

    private Optional<String> first(String link, String sourcePath) {
        List<String> found = candidates(link, sourcePath);
        return found.isEmpty() ? Optional.empty() : Optional.of(found.get(0));
    }

    private List<String> candidates(String link, String sourcePath) {
        String name = fold(link);
        List<String> sameName = byName.get(basename(name));
        if (sameName == null) {
            return List.of();
        }
        String sourceBase = fold(dirname(sourcePath));
        if (name.startsWith("./") || name.startsWith("../")) {
            String wanted = relativeTo(sourceBase, name);
            for (String path : sameName) {
                if (folded.get(path).equals(wanted)) {
                    return List.of(path);
                }
            }
            if (name.startsWith("../")) {
                // Not there: Obsidian takes the file of that name in the note's own folder.
                for (String path : sameName) {
                    if (dirname(folded.get(path)).equals(sourceBase)) {
                        return List.of(path);
                    }
                }
            }
            return List.of();
        }
        for (String path : sameName) {
            if (folded.get(path).equals(name)) {
                return List.of(path);
            }
        }
        String localPrefix = sourceBase.isEmpty() ? "" : sourceBase + "/";
        List<String> local = new ArrayList<>();
        List<String> other = new ArrayList<>();
        for (String path : sameName) {
            String lower = folded.get(path);
            if (!lower.equals(name) && !lower.endsWith("/" + name)) {
                continue;
            }
            (lower.startsWith(localPrefix) ? local : other).add(path);
        }
        Comparator<String> shortest = Comparator.comparingInt(String::length).thenComparing(Comparator.naturalOrder());
        local.sort(shortest);
        other.sort(shortest);
        local.addAll(other);
        return local;
    }

    /** {@code ./x} and {@code ../x} against the source note's folder (already folded). */
    private static String relativeTo(String sourceDir, String link) {
        String rest = link;
        String dir = sourceDir;
        if (rest.startsWith("./")) {
            rest = rest.substring(2);
        }
        while (rest.startsWith("../")) {
            rest = rest.substring(3);
            dir = dirname(dir);
        }
        while (rest.startsWith("./")) {
            rest = rest.substring(2);
        }
        return dir.isEmpty() ? rest : dir + "/" + rest;
    }

    /** Drops the heading/block part and the display text, and trims. */
    static String stripTarget(String raw) {
        String link = raw;
        int pipe = link.indexOf('|');
        if (pipe >= 0) {
            link = link.substring(0, pipe);
        }
        int hash = link.indexOf('#');
        if (hash >= 0) {
            link = link.substring(0, hash);
        }
        return link.strip();
    }

    static String fold(String value) {
        return nfc(value).toLowerCase(Locale.ROOT);
    }

    private static String nfc(String value) {
        return Normalizer.normalize(value, Normalizer.Form.NFC);
    }

    static String basename(String path) {
        return path.substring(path.lastIndexOf('/') + 1);
    }

    static String dirname(String path) {
        int index = path.lastIndexOf('/');
        return index < 0 ? "" : path.substring(0, index);
    }
}
