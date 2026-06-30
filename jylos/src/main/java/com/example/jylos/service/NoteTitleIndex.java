package com.example.jylos.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Logger;

import com.example.jylos.config.AppContext;
import com.example.jylos.config.LoggerConfig;
import com.example.jylos.data.models.Note;
import com.example.jylos.event.EventBus;
import com.example.jylos.event.events.NoteEvents;

/**
 * Warm cache of the set of known note titles, used to resolve {@code [[wiki-links]]}
 * during Markdown preview rendering.
 *
 * <p><b>Why this exists (perf P1).</b> {@link com.example.jylos.util.MarkdownPreview}
 * resolves wiki-links on every preview render — which, while editing, happens on
 * essentially every keystroke (after a short debounce). The previous implementation
 * called {@code NoteService.getAllNotes()} inside the render path, pulling the whole
 * note store into memory each time. With hundreds of notes that is a visible cost.</p>
 *
 * <p>This index caches the title {@link Set} and invalidates it only when notes are
 * created, deleted, saved (title may change) or the list is refreshed. Reads are then
 * O(1) for the common case.</p>
 *
 * <p>The cache is intentionally lazy and self-rebuilding: a {@code null} cached value
 * means "stale", and the next {@link #titles()} call repopulates it from the current
 * {@link NoteService}. Invalidation handlers therefore only null the reference, which
 * is safe to run on the JavaFX thread.</p>
 *
 * @author Edu Díaz (RGiskard7)
 * @since 2.0.0
 */
public final class NoteTitleIndex {

    private record Snapshot(Set<String> titles, Map<String, String> noteIdsByNormalizedTitle) {
    }

    private static final Logger logger = LoggerConfig.getLogger(NoteTitleIndex.class);

    private static final NoteTitleIndex INSTANCE = new NoteTitleIndex();

    /** {@code null} means the cache is stale and must be rebuilt on next read. */
    private volatile Snapshot snapshot;
    private final List<EventBus.Subscription> subscriptions = new ArrayList<>();

    private NoteTitleIndex() {
        subscribeToInvalidationEvents();
    }

    public static NoteTitleIndex getInstance() {
        return INSTANCE;
    }

    /**
     * Returns the set of known note titles (never {@code null}). Rebuilds the cache
     * from the current {@link NoteService} when stale. Returns an empty set if the
     * application context is not yet initialized.
     */
    public Set<String> titles() {
        return snapshot().titles();
    }

    /**
     * Resolves {@code title} to the first note id seen with that title (case-insensitive).
     * Returns empty when the title is unknown.
     */
    public Optional<String> findNoteIdByTitle(String title) {
        if (title == null || title.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(snapshot().noteIdsByNormalizedTitle().get(normalize(title)));
    }

    /** Marks the cache stale; the next {@link #titles()} call rebuilds it. */
    public void invalidate() {
        snapshot = null;
    }

    private Snapshot snapshot() {
        Snapshot cached = snapshot;
        if (cached != null) {
            return cached;
        }
        cached = rebuild();
        snapshot = cached;
        return cached;
    }

    private Snapshot rebuild() {
        try {
            if (AppContext.isInitialized()) {
                NoteService ns = AppContext.getNoteService();
                Set<String> titles = new LinkedHashSet<>();
                Map<String, String> noteIdsByNormalizedTitle = new LinkedHashMap<>();
                for (Note note : ns.getAllNotes()) {
                    if (note == null || note.getTitle() == null || note.getTitle().isBlank()) {
                        continue;
                    }
                    titles.add(note.getTitle());
                    if (note.getId() != null && !note.getId().isBlank()) {
                        noteIdsByNormalizedTitle.putIfAbsent(normalize(note.getTitle()), note.getId());
                    }
                }
                return new Snapshot(Set.copyOf(titles), Map.copyOf(noteIdsByNormalizedTitle));
            }
        } catch (Exception e) {
            logger.warning("NoteTitleIndex rebuild failed: " + e.getMessage());
        }
        return new Snapshot(Set.of(), Map.of());
    }

    private void subscribeToInvalidationEvents() {
        EventBus bus = EventBus.getInstance();
        subscriptions.add(bus.subscribe(NoteEvents.NoteCreatedEvent.class, e -> invalidate()));
        subscriptions.add(bus.subscribe(NoteEvents.NoteDeletedEvent.class, e -> invalidate()));
        subscriptions.add(bus.subscribe(NoteEvents.NoteSavedEvent.class, e -> invalidate()));
        subscriptions.add(bus.subscribe(NoteEvents.NoteUpdatedEvent.class, e -> invalidate()));
        subscriptions.add(bus.subscribe(NoteEvents.NotesRefreshRequestedEvent.class, e -> invalidate()));
    }

    public void shutdown() {
        subscriptions.forEach(EventBus.Subscription::cancel);
        subscriptions.clear();
    }

    private static String normalize(String title) {
        return title.trim().toLowerCase(Locale.ROOT);
    }
}
