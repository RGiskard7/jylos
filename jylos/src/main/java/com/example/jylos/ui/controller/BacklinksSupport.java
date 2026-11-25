package com.example.jylos.ui.controller;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiConsumer;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import com.example.jylos.data.models.Note;
import com.example.jylos.event.EventBus;
import com.example.jylos.event.events.NoteEvents;
import com.example.jylos.service.BacklinkService;
import com.example.jylos.service.BacklinkService.LinkGroup;
import com.example.jylos.service.BacklinkService.OutgoingLink;
import com.example.jylos.service.LinkOccurrences.Occurrence;
import com.example.jylos.service.NoteService;
import com.example.jylos.ui.components.LinksPanel;

import javafx.concurrent.Task;

/**
 * Feeds the right panel's links views ({@link LinksPanel}): the backlinks (the notes that link
 * to a note, and the notes that mention it without linking, searched only when that section is
 * opened) and the outgoing links (the notes it links to). One panel follows the open note; each
 * copy pinned to a note ({@link #pinnedPanel}) shows that note's, whatever note is open, and
 * follows it when it is renamed. Everything is computed off the JavaFX thread by
 * {@link BacklinkService}, and only for a view in sight; a result that arrives after a newer
 * request is discarded. Clicking an entry opens that note, a missing destination is created,
 * and "Link" rewrites the mention in its note.
 *
 * @author Edu Díaz (RGiskard7)
 * @since 2.0.0
 */
final class BacklinksSupport {

    private BacklinkService backlinkService;
    private NoteService noteService;
    private Function<String, String> i18n;
    private Consumer<Note> openNote;
    private Consumer<String> createNote = title -> { };
    private Consumer<Note> mentionLinked = note -> { };
    private Consumer<String> status = message -> { };
    private BiConsumer<String, Note> onPinnedShown = (copy, note) -> { };
    private BiConsumer<String, String> onPinnedRebound = (copy, noteId) -> { };

    /** The panel that follows the open note. */
    private Feed main;
    /** The copies pinned to a note, by id. */
    private final Map<String, Pinned> pinned = new LinkedHashMap<>();

    /** A pinned copy: its feed, the note it is pinned to, and whether it is in sight. */
    private static final class Pinned {
        final Feed feed;
        String noteId;
        final BooleanSupplier inSight;

        Pinned(Feed feed, String noteId, BooleanSupplier inSight) {
            this.feed = feed;
            this.noteId = noteId;
            this.inSight = inSight;
        }
    }

    /** A links panel, what it computes (backlinks, outgoing links or both) and the note it shows. */
    private final class Feed {
        final LinksPanel panel;
        final boolean backlinks;
        final boolean outgoing;
        Note shownNote;
        final AtomicLong linkedGeneration = new AtomicLong();
        final AtomicLong outgoingGeneration = new AtomicLong();
        final AtomicLong unlinkedGeneration = new AtomicLong();

        Feed(boolean backlinks, boolean outgoing) {
            this.backlinks = backlinks;
            this.outgoing = outgoing;
            panel = new LinksPanel(BacklinksSupport.this::getString, new LinksPanel.Actions() {
                @Override
                public void openNote(Note note) {
                    open(note);
                }

                @Override
                public void openOutgoing(OutgoingLink link) {
                    if (link.note() != null) {
                        open(link.note());
                    } else {
                        createNote.accept(link.name());
                    }
                }

                @Override
                public void linkMention(Note source, Occurrence mention) {
                    BacklinksSupport.this.linkMention(Feed.this, source, mention);
                }

                @Override
                public void unlinkedRequested() {
                    loadUnlinked(Feed.this);
                }
            });
        }

        /** Shows nothing (no note, or out of sight). */
        void clear() {
            shownNote = null;
            panel.reset();
        }

        /** Recomputes (off the FX thread) and renders the links of {@code note}. */
        void show(Note note) {
            boolean sameNote = shownNote != null && note.getId().equals(shownNote.getId());
            shownNote = note;
            if (!sameNote) {
                unlinkedGeneration.incrementAndGet();
                panel.reset();
            }
            if (backlinks) {
                background("backlinks", linkedGeneration, () -> backlinkService.backlinkGroups(note), panel::showLinked);
            }
            if (outgoing) {
                background("outgoing-links", outgoingGeneration, () -> backlinkService.outgoingLinks(note),
                        panel::showOutgoing);
            }
            if (backlinks && sameNote && panel.isUnlinkedExpanded()) {
                loadUnlinked(this);
            }
        }
    }

    private Supplier<Boolean> backlinksVisible = () -> true;

    /**
     * @param backlinksVisible whether either view that follows the open note is in sight; while
     *                         neither is, nothing is computed for it (a view brought into sight
     *                         asks for a {@link #refresh})
     */
    void wire(BacklinkService backlinkService, NoteService noteService,
            Function<String, String> i18n, Consumer<Note> openNote, Supplier<Boolean> backlinksVisible) {
        this.backlinkService = backlinkService;
        this.noteService = noteService;
        this.i18n = i18n;
        this.openNote = openNote;
        this.backlinksVisible = backlinksVisible != null ? backlinksVisible : () -> true;
        main = new Feed(true, true);
    }

    /** The backlinks and outgoing links views that follow the open note, with their actions (once wired). */
    LinksPanel links() {
        return main != null ? main.panel : null;
    }

    /**
     * A copy of a links view pinned to the note {@code noteId}: {@code kind} is
     * {@link RightPanelSupport#BACKLINKS} (the panel itself is its view) or
     * {@link RightPanelSupport#OUTGOING} (its {@link LinksPanel#outgoingView()}). It works its
     * links out only while {@code inSight}, when asked ({@link #refreshPinned}) or when notes
     * are created, deleted or saved.
     */
    LinksPanel pinnedPanel(String copyId, String kind, String noteId, BooleanSupplier inSight) {
        boolean backlinks = RightPanelSupport.BACKLINKS.equals(kind);
        Pinned copy = new Pinned(new Feed(backlinks, !backlinks), noteId, inSight != null ? inSight : () -> true);
        pinned.put(copyId, copy);
        return copy.feed.panel;
    }

    /** The note the copy {@code copyId} is pinned to, or {@code null} for no such copy. */
    String pinnedNoteId(String copyId) {
        Pinned copy = pinned.get(copyId);
        return copy != null ? copy.noteId : null;
    }

    /** Opens the note the copy {@code copyId} is pinned to, if it is there. */
    void openPinned(String copyId) {
        Pinned copy = pinned.get(copyId);
        if (copy != null && noteService != null) {
            noteService.getNoteById(copy.noteId).ifPresent(this::open);
        }
    }

    /** The copy {@code copyId} is gone: it is not fed any more. */
    void forget(String copyId) {
        pinned.remove(copyId);
    }

    /** Told, each time a copy is worked out, the note it shows ({@code null} when its note is not there). */
    void setOnPinnedShown(BiConsumer<String, Note> onPinnedShown) {
        this.onPinnedShown = onPinnedShown != null ? onPinnedShown : (copy, note) -> { };
    }

    /** Told the copy and the new id of its note when that note is renamed. */
    void setOnPinnedRebound(BiConsumer<String, String> onPinnedRebound) {
        this.onPinnedRebound = onPinnedRebound != null ? onPinnedRebound : (copy, noteId) -> { };
    }

    /** Works the copy {@code copyId} out again, if it is in sight (one brought into sight asks so). */
    void refreshPinned(String copyId) {
        Pinned copy = pinned.get(copyId);
        if (copy == null || !copy.inSight.getAsBoolean() || backlinkService == null) {
            return;
        }
        Note note = noteService != null ? noteService.getNoteById(copy.noteId).orElse(null) : null;
        if (note == null) {
            copy.feed.clear();
        } else {
            copy.feed.show(note);
        }
        onPinnedShown.accept(copyId, note);
    }

    /** The note {@code from} is now {@code to}: the copies pinned to it follow, and say so. */
    void rebind(String from, String to) {
        if (from == null || to == null || from.equals(to)) {
            return;
        }
        pinned.forEach((copyId, copy) -> {
            if (from.equals(copy.noteId)) {
                copy.noteId = to;
                onPinnedRebound.accept(copyId, to);
            }
        });
    }

    private void refreshAllPinned() {
        List.copyOf(pinned.keySet()).forEach(this::refreshPinned);
    }

    /**
     * Follows the notes created, deleted and saved: that changes what every link points to (a
     * destination that did not exist now does, a backlink may now lead nowhere), so the panel of
     * the open note is recomputed on a creation or a deletion, and the pinned copies on any of
     * them (a note saved may now link to theirs) and when the notes are loaded again; a note
     * renamed takes its copies along. The caller cancels the returned subscriptions.
     */
    List<EventBus.Subscription> subscribe(EventBus eventBus) {
        return List.of(
                eventBus.subscribe(NoteEvents.NoteCreatedEvent.class, e -> {
                    refresh(main != null ? main.shownNote : null);
                    refreshAllPinned();
                }),
                eventBus.subscribe(NoteEvents.NoteDeletedEvent.class, e -> {
                    refresh(main != null ? main.shownNote : null);
                    refreshAllPinned();
                }),
                eventBus.subscribe(NoteEvents.NoteSavedEvent.class, e -> {
                    if (e.getNote() != null) {
                        rebind(e.getPreviousNoteId(), e.getNote().getId());
                    }
                    refreshAllPinned();
                }),
                // The notes loaded again (the vault just read and its links indexed).
                eventBus.subscribe(NoteEvents.NotesRefreshRequestedEvent.class, e -> refreshAllPinned()));
    }

    /** What to do to open a note that a link points to but that does not exist yet (given its name). */
    void setCreateNoteAction(Consumer<String> createNote) {
        this.createNote = createNote != null ? createNote : title -> { };
    }

    /** Called with a note that "Link" rewrote and saved, so the rest of the app can refresh it. */
    void setMentionLinkedAction(Consumer<Note> mentionLinked) {
        this.mentionLinked = mentionLinked != null ? mentionLinked : note -> { };
    }

    void setStatusAction(Consumer<String> status) {
        this.status = status != null ? status : message -> { };
    }

    /** Recomputes (off the FX thread) and renders the links of {@code note} in the panel that follows the open note. */
    void refresh(Note note) {
        if (main == null) {
            return;
        }
        if (backlinksVisible != null && !backlinksVisible.get()) {
            main.clear();
            return;
        }
        if (backlinkService == null || note == null || note.getId() == null) {
            main.clear();
            return;
        }
        main.show(note);
    }

    private void loadUnlinked(Feed feed) {
        Note note = feed.shownNote;
        if (note == null || backlinkService == null) {
            return;
        }
        long generation = feed.unlinkedGeneration.incrementAndGet();
        background("unlinked-mentions", feed.unlinkedGeneration, generation,
                () -> backlinkService.unlinkedMentions(note, () -> feed.unlinkedGeneration.get() != generation),
                feed.panel::showUnlinked);
    }

    private void linkMention(Feed feed, Note source, Occurrence mention) {
        Note target = feed.shownNote;
        if (target == null || backlinkService == null) {
            return;
        }
        Task<Boolean> task = new Task<>() {
            @Override
            protected Boolean call() {
                return backlinkService.linkMention(source.getId(), mention, target);
            }
        };
        task.setOnSucceeded(e -> {
            if (Boolean.TRUE.equals(task.getValue())) {
                noteService.getNoteById(source.getId()).ifPresent(mentionLinked);
                feed.show(target);
                if (feed.panel.isUnlinkedExpanded()) {
                    loadUnlinked(feed);
                }
            } else {
                status.accept(getString("links.mention_changed"));
                loadUnlinked(feed);
            }
        });
        start("link-mention", task);
    }

    private <T> void background(String name, AtomicLong counter, Callable<T> work, Consumer<T> render) {
        background(name, counter, counter.incrementAndGet(), work, render);
    }

    private <T> void background(String name, AtomicLong counter, long generation, Callable<T> work,
            Consumer<T> render) {
        Task<T> task = new Task<>() {
            @Override
            protected T call() throws Exception {
                return work.call();
            }
        };
        task.setOnSucceeded(e -> {
            // Discard the result if a newer request already started.
            if (counter.get() == generation) {
                render.accept(task.getValue());
            }
        });
        task.setOnFailed(e -> {
            if (counter.get() == generation) {
                render.accept(null);
            }
        });
        start(name, task);
    }

    private static void start(String name, Task<?> task) {
        Thread thread = new Thread(task, name);
        thread.setDaemon(true);
        thread.start();
    }

    private void open(Note link) {
        Note full = noteService != null ? noteService.getNoteById(link.getId()).orElse(link) : link;
        if (openNote != null) {
            openNote.accept(full);
        }
    }

    private String getString(String key) {
        return i18n != null ? i18n.apply(key) : key;
    }
}
