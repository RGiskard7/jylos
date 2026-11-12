package com.example.jylos.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import com.example.jylos.data.models.Note;
import com.example.jylos.event.EventBus;
import com.example.jylos.event.events.NoteEvents;
import com.example.jylos.util.WikiLinkResolver;

/**
 * Computes <em>backlinks</em>: the notes whose content links to a given note
 * (via {@code [[wiki-links]]} or {@code [label](note)} internal links).
 *
 * <p>A link points to the note it names <em>by path</em>, as in Obsidian: two notes with
 * the same name in different folders are told apart, a folder path picks one, and aliases
 * count (see {@link LinkResolver}). Links inside code are not links.</p>
 *
 * <p>Uses the links the data layer already extracted when each note was read
 * ({@link Note#getLinkTargets()}, the links as written, computed with the same
 * {@code WikiLinkResolver} semantics as the Markdown preview and the graph). The
 * index therefore performs <em>no</em> file I/O of its own.</p>
 *
 * <h2>Index (perf P2)</h2>
 * <ul>
 *   <li><b>forward</b> — {@code noteId → links as written} (cached per note, validated by
 *       the note's {@code modified} timestamp; note events refresh one entry).</li>
 *   <li><b>inverse</b> — {@code target note id → ids of the notes linking to it}, built
 *       from the forward map with the vault's {@link LinkIndex}. Where a link points depends
 *       on every note (a new "Reunión" in the source's folder takes over the link), so the
 *       inverse is rebuilt, in memory, whenever the forward map or the link index changes.
 *       {@link #backlinksFor(Note)} is then a lookup.</li>
 * </ul>
 *
 * <h2>Coverage on large vaults</h2>
 * <p>For notes loaded via the list (lightweight read), links cover the indexed
 * content head (~16&nbsp;KB) rather than the entire file; a note opened or saved
 * (full read) carries whole-body links. This trades exhaustive link discovery in
 * very long notes for not re-reading every file — a deliberate choice that avoids
 * blocking on on-demand downloads of cloud-backed (e.g. iCloud-offloaded) files.</p>
 *
 * @author Edu Díaz (RGiskard7)
 * @since 1.6.0
 */
public class BacklinkService {

    private final NoteService noteService;
    private final List<EventBus.Subscription> subscriptions = new ArrayList<>();

    /** noteId → cached outgoing links (as written), validated by {@code modified} timestamp. */
    private final Map<String, CachedLinks> forward = new ConcurrentHashMap<>();
    /** True once the whole current vault snapshot has been indexed. */
    private volatile boolean fullIndexBuilt;
    /** Bumped whenever {@link #forward} changes, so the inverse map knows it is stale. */
    private volatile long forwardRevision;
    private Inverse inverse;

    private record CachedLinks(String modified, List<String> links) {
    }

    /** target id → linking ids, valid for one link index and one forward revision. */
    private record Inverse(LinkIndex links, long revision, Map<String, Set<String>> byTarget) {
    }

    /**
     * Creates a backlink index bound to a note service and invalidated by note events.
     *
     * @param noteService service used to load the vault snapshot and resolve note ids
     * @param eventBus event bus used to invalidate stale index entries
     */
    public BacklinkService(NoteService noteService, EventBus eventBus) {
        this.noteService = Objects.requireNonNull(noteService, "noteService");
        subscribeToInvalidationEvents(eventBus);
    }

    /**
     * Returns the notes that link to {@code target}, sorted by title. Excludes the
     * note itself. Safe to call off the JavaFX thread (reads files).
     *
     * @param target note whose incoming links should be resolved
     * @return notes that link to {@code target}, sorted by title
     */
    public List<Note> backlinksFor(Note target) {
        if (noteService == null || target == null || target.getId() == null || target.getId().isBlank()) {
            return List.of();
        }
        String targetId = target.getId();

        ensureFullIndex();

        Set<String> linkingIds = inverse().getOrDefault(targetId, Set.of());
        if (linkingIds.isEmpty()) {
            return List.of();
        }
        List<Note> result = new ArrayList<>();
        for (String linkingId : linkingIds) {
            if (Objects.equals(linkingId, targetId)) {
                continue;
            }
            noteService.getNoteById(linkingId).ifPresent(result::add);
        }
        result.sort((a, b) -> safeTitle(a).compareToIgnoreCase(safeTitle(b)));
        return result;
    }

    /** Drops cached link data (e.g. when the vault changes wholesale). */
    public void invalidate() {
        forward.clear();
        fullIndexBuilt = false;
        forwardRevision++;
    }

    private void ensureFullIndex() {
        if (fullIndexBuilt || noteService == null) {
            return;
        }
        synchronized (this) {
            if (fullIndexBuilt || noteService == null) {
                return;
            }
            for (Note note : noteService.getAllNotes()) {
                if (note != null && note.getId() != null) {
                    ensureIndexed(note);
                }
            }
            fullIndexBuilt = true;
        }
    }

    /** The inverse map for the current link index, rebuilt in memory when stale. */
    private synchronized Map<String, Set<String>> inverse() {
        LinkIndex links = noteService.linkIndex();
        long revision = forwardRevision;
        Inverse current = inverse;
        if (current != null && current.links() == links && current.revision() == revision) {
            return current.byTarget();
        }
        Map<String, Set<String>> byTarget = new HashMap<>();
        forward.forEach((sourceId, cached) -> {
            for (String link : cached.links()) {
                links.resolveId(link, sourceId)
                        .ifPresent(targetId -> byTarget.computeIfAbsent(targetId, k -> new HashSet<>()).add(sourceId));
            }
        });
        inverse = new Inverse(links, revision, byTarget);
        return byTarget;
    }

    /**
     * Indexes a single note's outgoing links if missing or stale.
     *
     * <p>Uses the links the DAO already extracted when the note was read
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
        // Prefer the links the DAO already extracted at read time (no I/O). If a note
        // carries none (e.g. the SQLite DAO, which loads full content but does not
        // pre-index links), derive them in-memory from its content — still no file read.
        List<String> links = note.getLinkTargets();
        if (links == null) {
            links = WikiLinkResolver.extractRawLinks(note.getContent() == null ? "" : note.getContent());
        }
        forward.put(id, new CachedLinks(modified, List.copyOf(links)));
        forwardRevision++;
    }

    /** Removes a note from the index (on deletion). */
    private void removeFromIndex(String id) {
        if (id != null && forward.remove(id) != null) {
            forwardRevision++;
        }
    }

    private void subscribeToInvalidationEvents(EventBus bus) {
        Objects.requireNonNull(bus, "eventBus");
        subscriptions.add(bus.subscribe(NoteEvents.NoteSavedEvent.class, e -> reindex(e.getNote())));
        subscriptions.add(bus.subscribe(NoteEvents.NoteCreatedEvent.class, e -> reindex(e.getNote())));
        subscriptions.add(bus.subscribe(NoteEvents.NoteUpdatedEvent.class, e -> reindex(e.getNote())));
        subscriptions.add(bus.subscribe(NoteEvents.NoteDeletedEvent.class, e -> removeFromIndex(e.getNoteId())));
        subscriptions.add(bus.subscribe(NoteEvents.NotesRefreshRequestedEvent.class, e -> invalidate()));
    }

    public void shutdown() {
        subscriptions.forEach(EventBus.Subscription::cancel);
        subscriptions.clear();
    }

    private void reindex(Note note) {
        if (note == null || note.getId() == null) {
            return;
        }
        removeFromIndex(note.getId());
        ensureIndexed(note);
    }

    private static String safeTitle(Note note) {
        return note.getTitle() != null ? note.getTitle() : "";
    }
}
