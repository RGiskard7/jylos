package com.example.jylos.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import com.example.jylos.config.LoggerConfig;
import com.example.jylos.data.models.Note;
import com.example.jylos.util.WikiLinkResolver;

/**
 * Computes <em>backlinks</em>: the notes whose content links to a given note
 * (via {@code [[wiki-links]]} or {@code [label](note)} internal links).
 *
 * <p>Uses the same link-extraction semantics as the Markdown preview and the
 * graph ({@link WikiLinkResolver#extractLinkTargets(String)}). Each note's link
 * targets are read from its <em>full</em> content and cached per id, validated by
 * the note's {@code modified} timestamp (the list/{@code getAllNotes} body is
 * truncated, so links deep in long notes would otherwise be missed).</p>
 *
 * @author Edu Díaz (RGiskard7)
 * @since 1.6.0
 */
public class BacklinkService {

    private static final Logger logger = LoggerConfig.getLogger(BacklinkService.class);

    private final NoteService noteService;
    private final java.util.Map<String, CachedLinks> cache = new ConcurrentHashMap<>();

    private record CachedLinks(String modified, Set<String> targets) {
    }

    public BacklinkService(NoteService noteService) {
        this.noteService = noteService;
    }

    /**
     * Returns the notes that link to {@code target}, sorted by title. Excludes the
     * note itself. Safe to call off the JavaFX thread (reads files).
     */
    public List<Note> backlinksFor(Note target) {
        if (noteService == null || target == null
                || target.getTitle() == null || target.getTitle().isBlank()) {
            return List.of();
        }
        String wanted = normalize(target.getTitle());
        String targetId = target.getId();

        List<Note> result = new ArrayList<>();
        for (Note note : noteService.getAllNotes()) {
            if (note == null || note.getId() == null || Objects.equals(note.getId(), targetId)) {
                continue;
            }
            if (targetsOf(note).contains(wanted)) {
                result.add(note);
            }
        }
        result.sort((a, b) -> safeTitle(a).compareToIgnoreCase(safeTitle(b)));
        return result;
    }

    /** Drops cached link data (e.g. when the vault changes wholesale). */
    public void invalidate() {
        cache.clear();
    }

    private Set<String> targetsOf(Note note) {
        String id = note.getId();
        String modified = note.getModifiedDate();
        CachedLinks cached = cache.get(id);
        if (cached != null && Objects.equals(cached.modified(), modified)) {
            return cached.targets();
        }
        String content = note.getContent();
        try {
            Note full = noteService.getNoteById(id).orElse(null);
            if (full != null && full.getContent() != null) {
                content = full.getContent();
            }
        } catch (Exception e) {
            logger.fine("Backlinks: could not read full content for " + id + ": " + e.getMessage());
        }
        Set<String> targets = WikiLinkResolver.extractLinkTargets(content == null ? "" : content).stream()
                .map(BacklinkService::normalize)
                .collect(Collectors.toSet());
        cache.put(id, new CachedLinks(modified, targets));
        return targets;
    }

    private static String safeTitle(Note note) {
        return note.getTitle() != null ? note.getTitle() : "";
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
