package com.example.jylos.service;

import java.util.Set;
import java.util.logging.Logger;
import java.util.stream.Collectors;

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

    private static final Logger logger = LoggerConfig.getLogger(NoteTitleIndex.class);

    private static final NoteTitleIndex INSTANCE = new NoteTitleIndex();

    /** {@code null} means the cache is stale and must be rebuilt on next read. */
    private volatile Set<String> cachedTitles;

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
        Set<String> snapshot = cachedTitles;
        if (snapshot != null) {
            return snapshot;
        }
        snapshot = rebuild();
        cachedTitles = snapshot;
        return snapshot;
    }

    /** Marks the cache stale; the next {@link #titles()} call rebuilds it. */
    public void invalidate() {
        cachedTitles = null;
    }

    private Set<String> rebuild() {
        try {
            if (AppContext.isInitialized()) {
                NoteService ns = AppContext.getNoteService();
                return ns.getAllNotes().stream()
                        .map(Note::getTitle)
                        .filter(t -> t != null)
                        .collect(Collectors.toUnmodifiableSet());
            }
        } catch (Exception e) {
            logger.warning("NoteTitleIndex rebuild failed: " + e.getMessage());
        }
        return Set.of();
    }

    private void subscribeToInvalidationEvents() {
        EventBus bus = EventBus.getInstance();
        bus.subscribe(NoteEvents.NoteCreatedEvent.class, e -> invalidate());
        bus.subscribe(NoteEvents.NoteDeletedEvent.class, e -> invalidate());
        bus.subscribe(NoteEvents.NoteSavedEvent.class, e -> invalidate());
        bus.subscribe(NoteEvents.NoteUpdatedEvent.class, e -> invalidate());
        bus.subscribe(NoteEvents.NotesRefreshRequestedEvent.class, e -> invalidate());
    }
}
