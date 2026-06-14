package com.example.jylos.service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import com.example.jylos.data.models.Note;
import com.example.jylos.event.EventBus;
import com.example.jylos.event.events.NoteEvents;
import com.example.jylos.util.WikiLinkResolver;

/**
 * Computes <em>backlinks</em>: the notes whose content links to a given note
 * (via {@code [[wiki-links]]} or {@code [label](note)} internal links).
 *
 * <p>Uses the link targets the data layer already extracted when each note was read
 * ({@link Note#getLinkTargets()}, computed with the same
 * {@code WikiLinkResolver} semantics as the Markdown preview and the graph). The
 * index therefore performs <em>no</em> file I/O of its own.</p>
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
 * FX-thread-safe operation); re-indexing then reuses the targets carried by the
 * refreshed {@link Note}.</p>
 *
 * <h3>Coverage on large vaults</h3>
 * <p>For notes loaded via the list (lightweight read), targets cover the indexed
 * content head (~16&nbsp;KB) rather than the entire file; a note opened or saved
 * (full read) carries whole-body targets. This trades exhaustive link discovery in
 * very long notes for not re-reading every file — a deliberate choice that avoids
 * blocking on on-demand downloads of cloud-backed (e.g. iCloud-offloaded) files.</p>
 *
 * @author Edu Díaz (RGiskard7)
 * @since 1.6.0
 */
public class BacklinkService {

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
     * forward and inverse maps.
     *
     * <p>Uses the link targets the DAO already extracted when the note was read
     * ({@link Note#getLinkTargets()}); it performs <em>no</em> file I/O. This keeps the
     * backlink index cheap even on large or cloud-backed vaults, where reading every
     * note's full content again would block on per-file on-demand downloads.</p>
     */
    private void ensureIndexed(Note note) {
        String id = note.getId();
        String modified = note.getModifiedDate();
        CachedLinks cached = forward.get(id);
        if (cached != null && Objects.equals(cached.modified(), modified)) {
            return;
        }
        // Prefer the targets the DAO already extracted at read time (no I/O). If a note
        // carries none (e.g. the SQLite DAO, which loads full content but does not
        // pre-index links), derive them in-memory from its content — still no file read.
        List<String> raw = note.getLinkTargets();
        if (raw == null) {
            raw = WikiLinkResolver.extractLinkTargets(note.getContent() == null ? "" : note.getContent());
        }
        Set<String> targets = raw.stream()
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
