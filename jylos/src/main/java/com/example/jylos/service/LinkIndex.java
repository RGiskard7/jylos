package com.example.jylos.service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Function;

import com.example.jylos.data.models.Note;

/**
 * Vault-wide link resolution by note id: knows the path of every note in the vault and
 * resolves a link written in one note to the id of the note it points to, the way Obsidian
 * does (see {@link LinkResolver}).
 *
 * <p>The path of a note is its file path in a folder vault and its folder chain plus title in
 * SQLite; the caller supplies it. Aliases come from the {@code aliases} (or {@code alias})
 * property, as a list or a single value. Immutable; rebuilt when notes change.</p>
 *
 * @author Edu Díaz (RGiskard7)
 * @since 2.6.0
 */
public final class LinkIndex {

    /** An index with no notes: every link is unresolved. */
    public static final LinkIndex EMPTY = new LinkIndex(new LinkResolver(List.of(), Map.of()), Map.of(), Map.of(),
            List.of());

    private final LinkResolver resolver;
    private final Map<String, String> pathById;
    private final Map<String, String> idByPath;
    private final List<LinkSuggestion> suggestions;

    private LinkIndex(LinkResolver resolver, Map<String, String> pathById, Map<String, String> idByPath,
            List<LinkSuggestion> suggestions) {
        this.resolver = resolver;
        this.pathById = pathById;
        this.idByPath = idByPath;
        this.suggestions = suggestions;
    }

    /**
     * Builds the index for {@code notes}; {@code pathOf} gives each note's vault path
     * ({@code Carpeta/Nota.md}), or null to leave the note out.
     */
    public static LinkIndex build(Collection<Note> notes, Function<Note, String> pathOf) {
        List<LinkResolver.Entry> entries = new ArrayList<>();
        Map<String, String> aliases = new HashMap<>();
        Map<String, String> pathById = new HashMap<>();
        Map<String, String> idByPath = new HashMap<>();
        Map<String, List<String>> aliasesByPath = new LinkedHashMap<>();
        for (Note note : notes) {
            if (note == null || note.getId() == null || note.getId().isBlank()) {
                continue;
            }
            String path = pathOf.apply(note);
            if (path == null || path.isBlank()) {
                continue;
            }
            path = path.replace('\\', '/');
            if (idByPath.putIfAbsent(path, note.getId()) != null) {
                continue;
            }
            pathById.put(note.getId(), path);
            entries.add(new LinkResolver.Entry(path, path.toLowerCase(Locale.ROOT).endsWith(".md")));
            List<String> noteAliases = aliasesOf(note);
            for (String alias : noteAliases) {
                aliases.putIfAbsent(alias, path);
            }
            aliasesByPath.put(path, noteAliases);
        }
        return new LinkIndex(new LinkResolver(entries, aliases), Map.copyOf(pathById), Map.copyOf(idByPath),
                suggestionsFor(aliasesByPath));
    }

    /**
     * Each note as [[ suggests it: named by its file name (without {@code .md}), in its folder,
     * linked by that name when no other note has it (as the resolver compares names) and by its
     * path otherwise; by name, then folder.
     */
    private static List<LinkSuggestion> suggestionsFor(Map<String, List<String>> aliasesByPath) {
        Map<String, Integer> sameName = new HashMap<>();
        aliasesByPath.keySet().forEach(path -> sameName.merge(LinkResolver.fold(nameOf(path)), 1, Integer::sum));
        List<LinkSuggestion> out = new ArrayList<>();
        aliasesByPath.forEach((path, noteAliases) -> {
            String name = nameOf(path);
            int slash = path.lastIndexOf('/');
            String folder = slash >= 0 ? path.substring(0, slash) : "";
            String link = sameName.get(LinkResolver.fold(name)) == 1 ? name
                    : folder.isEmpty() ? name : folder + "/" + name;
            out.add(new LinkSuggestion(name, folder, link, noteAliases));
        });
        out.sort(Comparator.comparing(LinkSuggestion::name, String.CASE_INSENSITIVE_ORDER)
                .thenComparing(LinkSuggestion::folder, String.CASE_INSENSITIVE_ORDER));
        return List.copyOf(out);
    }

    /** The file name of {@code path}, without {@code .md} for a note. */
    private static String nameOf(String path) {
        String name = path.substring(path.lastIndexOf('/') + 1);
        return name.toLowerCase(Locale.ROOT).endsWith(".md") ? name.substring(0, name.length() - 3) : name;
    }

    /** The id of the note {@code rawLink} points to from the note {@code sourceNoteId}. */
    public Optional<String> resolveId(String rawLink, String sourceNoteId) {
        String source = sourceNoteId != null ? pathById.get(sourceNoteId) : null;
        return resolver.resolve(rawLink, source).map(idByPath::get);
    }

    /**
     * Resolves many links at once (every link of the vault, as the backlink map does), answering
     * exactly as {@link #resolveId}. The same link is written in many notes, and most links
     * point to the same note whichever folder they are written in (no other file has that
     * name): those are resolved once. For one pass; not thread-safe.
     */
    public BiFunction<String, String, Optional<String>> batchResolver() {
        Map<String, Optional<String>> fromAnyFolder = new HashMap<>();
        Set<String> dependsOnFolder = new HashSet<>();
        return (rawLink, sourceNoteId) -> {
            if (rawLink == null) {
                return Optional.empty();
            }
            Optional<String> known = fromAnyFolder.get(rawLink);
            if (known != null) {
                return known;
            }
            if (dependsOnFolder.contains(rawLink)) {
                return resolveId(rawLink, sourceNoteId);
            }
            Optional<String> answer = resolveId(rawLink, sourceNoteId);
            if (resolver.sameFromEveryFolder(rawLink)) {
                fromAnyFolder.put(rawLink, answer);
            } else {
                dependsOnFolder.add(rawLink);
            }
            return answer;
        };
    }

    /** True when {@code rawLink} written in {@code sourceNoteId} points to a note. */
    public boolean exists(String rawLink, String sourceNoteId) {
        return resolveId(rawLink, sourceNoteId).isPresent();
    }

    /** Every note as [[ suggests it while a link is typed (see {@link LinkSuggestion}). */
    public List<LinkSuggestion> suggestions() {
        return suggestions;
    }

    /** The vault path of a note, or null when it is not indexed. */
    public String pathOf(String noteId) {
        return noteId != null ? pathById.get(noteId) : null;
    }

    /** The note's aliases, from its {@code aliases} or {@code alias} property. */
    static List<String> aliasesOf(Note note) {
        Map<String, String> props = note.getCustomProperties();
        if (props == null || props.isEmpty()) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        for (Map.Entry<String, String> entry : props.entrySet()) {
            String key = entry.getKey();
            if (key != null && (key.equalsIgnoreCase("aliases") || key.equalsIgnoreCase("alias"))) {
                out.addAll(splitList(entry.getValue()));
            }
        }
        return out;
    }

    /** {@code [a, "b"]} or {@code a} → the values, unquoted. */
    private static List<String> splitList(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        String stripped = raw.strip();
        if (stripped.startsWith("[") && stripped.endsWith("]")) {
            stripped = stripped.substring(1, stripped.length() - 1);
        }
        List<String> values = new ArrayList<>();
        for (String part : stripped.split(",")) {
            String value = part.strip();
            if (value.length() >= 2 && (value.startsWith("\"") && value.endsWith("\"")
                    || value.startsWith("'") && value.endsWith("'"))) {
                value = value.substring(1, value.length() - 1).strip();
            }
            if (!value.isEmpty()) {
                values.add(value);
            }
        }
        return values;
    }
}
