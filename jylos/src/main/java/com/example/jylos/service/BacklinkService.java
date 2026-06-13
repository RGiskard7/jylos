package com.example.jylos.service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import com.example.jylos.config.LoggerConfig;
import com.example.jylos.data.models.Note;
import com.example.jylos.event.EventBus;
import com.example.jylos.event.events.NoteEvents;
import com.example.jylos.util.WikiLinkResolver;

/**
 * Computes <em>backlinks</em>: the notes whose content links to a given note
 * (via {@code [[wiki-links]]} or {@code [label](note)} internal links).
 *
 * <p>Uses the same link-extraction semantics as the Markdown preview and the
 * graph ({@link WikiLinkResolver#extractLinkTargets(String)}). Each note's link
 * targets are read from its <em>full</em> content (the list/{@code getAllNotes}
 * body is truncated, so links deep in long notes would otherwise be missed).</p>
 *
 * <h3>Index (perf P2)</h3>
 * <p>The service keeps two warm maps:</p>
 * <ul>
 *   <li><b>forward</b> — {@code noteId → outgoing link targets} (cached per note,
 *       validated by the note's {@code modified} timestamp).</li>
 *   <li><b>inverse</b> — {@code normalized target title → set of note ids linking
 *       to it}. This makes {@link #backlinksFor(Note)} an O(1) lookup instead of a
 *       full {@code contains()} scan over every note.</li>
 * </ul>
 *
 * <p>Note events invalidate only the affected note's forward entry (a cheap,
 * FX-thread-safe operation); the (potentially expensive) full-content read happens
 * lazily inside {@link #backlinksFor(Note)}, which its callers already run off the
 * JavaFX thread.</p>
 *
 * @author Edu Díaz (RGiskard7)
 * @since 1.6.0
 */
public class BacklinkService {

    private static final Logger logger = LoggerConfig.getLogger(BacklinkService.class);

    private final NoteService noteService;

    /** noteId → cached outgoing targets, validated by {@code modified} timestamp. */
    private final Map<String, CachedLinks> forward = new ConcurrentHashMap<>();
    /** normalized target title → ids of notes that link to it. */
    private final Map<String, Set<String>> inverse = new ConcurrentHashMap<>();

    private record CachedLinks(String modified, Set<String> targets) {
    }

    public BacklinkService(NoteService noteService) {
        this.noteService = noteService;
        subscribeToInvalidationEvents();
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

        // Ensure every current note's forward targets are indexed (lazy, only reads
        // content for notes whose cache is missing or stale). Keeps the inverse map
        // consistent as a side effect.
        List<Note> allNotes = noteService.getAllNotes();
        for (Note note : allNotes) {
            if (note != null && note.getId() != null) {
                ensureIndexed(note);
            }
        }

        Set<String> linkingIds = inverse.getOrDefault(wanted, Set.of());
        if (linkingIds.isEmpty()) {
            return List.of();
        }
        List<Note> result = new ArrayList<>();
        for (Note note : allNotes) {
            if (note == null || note.getId() == null
                    || Objects.equals(note.getId(), targetId)) {
                continue;
            }
            if (linkingIds.contains(note.getId())) {
                result.add(note);
            }
        }
        result.sort((a, b) -> safeTitle(a).compareToIgnoreCase(safeTitle(b)));
        return result;
    }

    /** Drops cached link data (e.g. when the vault changes wholesale). */
    public void invalidate() {
        forward.clear();
        inverse.clear();
    }

    /**
     * Indexes a single note's outgoing targets if missing or stale, updating both the
     * forward and inverse maps. Reads full content only when needed.
     */
    private void ensureIndexed(Note note) {
        String id = note.getId();
        String modified = note.getModifiedDate();
        CachedLinks cached = forward.get(id);
        if (cached != null && Objects.equals(cached.modified(), modified)) {
            return;
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
                .collect(Collectors.toCollection(HashSet::new));

        applyToInverse(id, cached != null ? cached.targets() : Set.of(), targets);
        forward.put(id, new CachedLinks(modified, targets));
    }

    /** Removes a note from both maps (on deletion). */
    private void removeFromIndex(String id) {
        if (id == null) {
            return;
        }
        CachedLinks cached = forward.remove(id);
        if (cached != null) {
            applyToInverse(id, cached.targets(), Set.of());
        }
    }

    /** Reconciles the inverse map for {@code id}: drop {@code oldTargets}, add {@code newTargets}. */
    private void applyToInverse(String id, Set<String> oldTargets, Set<String> newTargets) {
        for (String t : oldTargets) {
            if (!newTargets.contains(t)) {
                Set<String> ids = inverse.get(t);
                if (ids != null) {
                    ids.remove(id);
                    if (ids.isEmpty()) {
                        inverse.remove(t);
                    }
                }
            }
        }
        for (String t : newTargets) {
            inverse.computeIfAbsent(t, k -> ConcurrentHashMap.newKeySet()).add(id);
        }
    }

    private void subscribeToInvalidationEvents() {
        EventBus bus = EventBus.getInstance();
        // Cheap, FX-thread-safe: just drop the note's forward entry so the next
        // backlinksFor() re-reads its content lazily (off the FX thread).
        bus.subscribe(NoteEvents.NoteSavedEvent.class, e -> dropForward(idOf(e.getNote())));
        bus.subscribe(NoteEvents.NoteCreatedEvent.class, e -> dropForward(idOf(e.getNote())));
        bus.subscribe(NoteEvents.NoteUpdatedEvent.class, e -> dropForward(idOf(e.getNote())));
        bus.subscribe(NoteEvents.NoteDeletedEvent.class, e -> removeFromIndex(e.getNoteId()));
        bus.subscribe(NoteEvents.NotesRefreshRequestedEvent.class, e -> invalidate());
    }

    private void dropForward(String id) {
        if (id == null) {
            return;
        }
        CachedLinks cached = forward.remove(id);
        if (cached != null) {
            // Keep inverse consistent until re-indexed; remove this id's contributions.
            applyToInverse(id, cached.targets(), Set.of());
        }
    }

    private static String idOf(Note note) {
        return note != null ? note.getId() : null;
    }

    private static String safeTitle(Note note) {
        return note.getTitle() != null ? note.getTitle() : "";
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
