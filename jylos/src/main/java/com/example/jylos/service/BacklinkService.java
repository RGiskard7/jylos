package com.example.jylos.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiFunction;
import java.util.function.BooleanSupplier;

import com.example.jylos.data.models.Note;
import com.example.jylos.event.EventBus;
import com.example.jylos.event.events.NoteEvents;
import com.example.jylos.util.AttachmentType;
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
    private final AtomicLong forwardRevision = new AtomicLong();
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

    /**
     * The links (or mentions) of one note to the open note, with where each is.
     *
     * @param note        the note that contains them
     * @param occurrences their places, in order
     */
    public record LinkGroup(Note note, List<LinkOccurrences.Occurrence> occurrences) {
    }

    /**
     * Where the open note links to.
     *
     * @param name  the destination's name: its title, or what the link says when it does not exist yet
     * @param note  the destination, or null when no such note exists yet
     * @param count how many links of the open note point there
     */
    public record OutgoingLink(String name, Note note, int count) {
        public boolean resolved() {
            return note != null;
        }
    }

    /**
     * The notes that link to {@code target}, each with the links themselves and the line
     * they are on, sorted by title. Reads the linking notes in full.
     */
    public List<LinkGroup> backlinkGroups(Note target) {
        if (target == null || target.getId() == null) {
            return List.of();
        }
        LinkIndex links = noteService.linkIndex();
        List<LinkGroup> groups = new ArrayList<>();
        for (Note linking : backlinksFor(target)) {
            Note full = noteService.getNoteById(linking.getId()).orElse(null);
            if (full == null || full.getContent() == null) {
                continue;
            }
            // Links in the properties count too (as in Obsidian); their lines are numbered below
            // zero so they never join a line of the body.
            List<LinkOccurrences.Occurrence> points = new ArrayList<>();
            for (LinkOccurrences.Occurrence o : LinkOccurrences.links(noteService.propertiesText(full.getId()))) {
                if (links.resolveId(o.target(), full.getId()).filter(target.getId()::equals).isPresent()) {
                    points.add(new LinkOccurrences.Occurrence(-(o.line() + 1), o.start(), o.end(), o.lineText(),
                            o.target()));
                }
            }
            LinkOccurrences.links(full.getContent()).stream()
                    .filter(o -> links.resolveId(o.target(), full.getId()).filter(target.getId()::equals).isPresent())
                    .forEach(points::add);
            if (!points.isEmpty()) {
                groups.add(new LinkGroup(full, points));
            }
        }
        return groups;
    }

    /**
     * Notes that name {@code target} (its title or one of its aliases) in plain text without
     * linking it, with each mention. Reads every note in full, so it is meant for a
     * background thread; {@code cancelled} is polled between notes.
     */
    public List<LinkGroup> unlinkedMentions(Note target, BooleanSupplier cancelled) {
        if (target == null || target.getId() == null || target.getTitle() == null || target.getTitle().isBlank()) {
            return List.of();
        }
        List<String> terms = new ArrayList<>();
        terms.add(target.getTitle());
        terms.addAll(LinkIndex.aliasesOf(target));
        List<LinkGroup> groups = new ArrayList<>();
        for (Note candidate : noteService.getAllNotes()) {
            if (cancelled != null && cancelled.getAsBoolean()) {
                return List.of();
            }
            if (candidate == null || candidate.getId() == null || candidate.getId().equals(target.getId())
                    || AttachmentType.fromName(candidate.getId())
                            != AttachmentType.MARKDOWN) {
                continue;
            }
            Note full = noteService.getNoteById(candidate.getId()).orElse(null);
            if (full == null || full.getContent() == null) {
                continue;
            }
            List<LinkOccurrences.Occurrence> found = LinkOccurrences.mentions(full.getContent(), terms);
            if (!found.isEmpty()) {
                groups.add(new LinkGroup(full, found));
            }
        }
        groups.sort((a, b) -> safeTitle(a.note()).compareToIgnoreCase(safeTitle(b.note())));
        return groups;
    }

    /**
     * Where {@code note} links to: each destination once (with how many links point there),
     * in order of first appearance. A destination that does not exist yet has no note.
     */
    public List<OutgoingLink> outgoingLinks(Note note) {
        if (note == null || note.getId() == null) {
            return List.of();
        }
        Note full = noteService.getNoteById(note.getId()).orElse(note);
        LinkIndex links = noteService.linkIndex();
        Map<String, int[]> counts = new LinkedHashMap<>();
        Map<String, String> names = new HashMap<>();
        Map<String, String> ids = new HashMap<>();
        for (LinkOccurrences.Occurrence o : LinkOccurrences.links(full.getContent())) {
            Optional<String> id = links.resolveId(o.target(), full.getId());
            String key = id.map(i -> "id:" + i).orElse("name:" + o.target().toLowerCase(Locale.ROOT));
            counts.computeIfAbsent(key, k -> new int[1])[0]++;
            names.putIfAbsent(key, WikiLinkResolver.extractTitle(o.target()));
            id.ifPresent(i -> ids.putIfAbsent(key, i));
        }
        List<OutgoingLink> out = new ArrayList<>();
        counts.forEach((key, count) -> {
            Note destination = ids.containsKey(key) ? noteService.getNoteById(ids.get(key)).orElse(null) : null;
            String name = destination != null && destination.getTitle() != null
                    ? destination.getTitle() : names.get(key);
            out.add(new OutgoingLink(name, destination, count[0]));
        });
        return out;
    }

    /**
     * Turns a mention into a link: rewrites that text in {@code sourceNoteId} as
     * {@code [[Title]]}, or {@code [[Title|mention]]} when the mention says it differently
     * (an alias, another case). When another note with the same name would win the link
     * from there, the link carries the folder path. Does nothing and returns false when the
     * note no longer has the mention where it was found.
     *
     * @return true when the note was rewritten and saved
     */
    public boolean linkMention(String sourceNoteId, LinkOccurrences.Occurrence mention, Note target) {
        if (sourceNoteId == null || mention == null || target == null || target.getId() == null
                || target.getTitle() == null) {
            return false;
        }
        Note source = noteService.getNoteById(sourceNoteId).orElse(null);
        if (source == null || source.getContent() == null) {
            return false;
        }
        LinkIndex links = noteService.linkIndex();
        String title = target.getTitle();
        String link = title;
        if (!links.resolveId(title, sourceNoteId).filter(target.getId()::equals).isPresent()) {
            String path = links.pathOf(target.getId());
            if (path != null) {
                link = path.toLowerCase(Locale.ROOT).endsWith(".md")
                        ? path.substring(0, path.length() - 3) : path;
            }
        }
        String matched = mention.matched();
        String replacement = matched.equals(link) ? "[[" + link + "]]" : "[[" + link + "|" + matched + "]]";
        String rewritten = LinkOccurrences.replace(source.getContent(), mention, replacement);
        if (rewritten == null) {
            return false;
        }
        source.setContent(rewritten);
        noteService.updateNote(source);
        reindex(source);
        return true;
    }

    /** Drops cached link data (e.g. when the vault changes wholesale). */
    public void invalidate() {
        forward.clear();
        fullIndexBuilt = false;
        forwardRevision.incrementAndGet();
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
        long revision = forwardRevision.get();
        Inverse current = inverse;
        if (current != null && current.links() == links && current.revision() == revision) {
            return current.byTarget();
        }
        Map<String, Set<String>> byTarget = new HashMap<>();
        BiFunction<String, String, Optional<String>> resolve = links.batchResolver();
        forward.forEach((sourceId, cached) -> {
            for (String link : cached.links()) {
                resolve.apply(link, sourceId)
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
        forwardRevision.incrementAndGet();
    }

    /** Removes a note from the index (on deletion). */
    private void removeFromIndex(String id) {
        if (id != null && forward.remove(id) != null) {
            forwardRevision.incrementAndGet();
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
