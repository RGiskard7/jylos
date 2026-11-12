package com.example.jylos.ui.controller;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicLong;
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
import javafx.scene.layout.VBox;

/**
 * Feeds the right-panel links section ({@link LinksPanel}) for the open note: the notes that
 * link to it, the notes that mention it without linking (searched only when that section is
 * opened) and the notes it links to. Everything is computed off the JavaFX thread by
 * {@link BacklinkService}; a result that arrives after a newer request is discarded. Clicking
 * an entry opens that note, a missing destination is created, and "Link" rewrites the
 * mention in its note.
 *
 * @author Edu Díaz (RGiskard7)
 * @since 2.0.0
 */
final class BacklinksSupport {

    private VBox backlinksContent;
    private LinksPanel panel;
    private BacklinkService backlinkService;
    private NoteService noteService;
    private Function<String, String> i18n;
    private Consumer<Note> openNote;
    private Supplier<Boolean> backlinksVisible = () -> true;
    private Consumer<String> createNote = title -> { };
    private Consumer<Note> mentionLinked = note -> { };
    private Consumer<String> status = message -> { };

    /** The note the panel is showing, to tell a refresh of the same note from a new one. */
    private Note shownNote;
    private final AtomicLong linkedGeneration = new AtomicLong();
    private final AtomicLong outgoingGeneration = new AtomicLong();
    private final AtomicLong unlinkedGeneration = new AtomicLong();

    void wire(VBox backlinksContent, BacklinkService backlinkService, NoteService noteService,
            Function<String, String> i18n, Consumer<Note> openNote, Supplier<Boolean> backlinksVisible) {
        this.backlinksContent = backlinksContent;
        this.backlinkService = backlinkService;
        this.noteService = noteService;
        this.i18n = i18n;
        this.openNote = openNote;
        this.backlinksVisible = backlinksVisible != null ? backlinksVisible : () -> true;
        if (backlinksContent != null) {
            panel = new LinksPanel(this::getString, new LinksPanel.Actions() {
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
                    BacklinksSupport.this.linkMention(source, mention);
                }

                @Override
                public void unlinkedRequested() {
                    loadUnlinked();
                }
            });
            backlinksContent.getChildren().setAll(panel);
        }
    }

    /**
     * Follows the notes created and deleted: that changes what every link points to (a
     * destination that did not exist now does, a backlink may now lead nowhere), so the open
     * note's panel is recomputed. The caller cancels the returned subscriptions.
     */
    List<EventBus.Subscription> subscribe(EventBus eventBus) {
        return List.of(
                eventBus.subscribe(NoteEvents.NoteCreatedEvent.class, e -> refresh(shownNote)),
                eventBus.subscribe(NoteEvents.NoteDeletedEvent.class, e -> refresh(shownNote)));
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

    /** Recomputes (off the FX thread) and renders the links of {@code note}. */
    void refresh(Note note) {
        if (panel == null) {
            return;
        }
        if (backlinksVisible != null && !backlinksVisible.get()) {
            shownNote = null;
            panel.reset();
            return;
        }
        if (backlinkService == null || note == null || note.getId() == null) {
            shownNote = null;
            panel.reset();
            return;
        }
        boolean sameNote = shownNote != null && note.getId().equals(shownNote.getId());
        shownNote = note;
        if (!sameNote) {
            unlinkedGeneration.incrementAndGet();
            panel.reset();
        }
        background("backlinks", linkedGeneration, () -> backlinkService.backlinkGroups(note), panel::showLinked);
        background("outgoing-links", outgoingGeneration, () -> backlinkService.outgoingLinks(note),
                panel::showOutgoing);
        if (sameNote && panel.isUnlinkedExpanded()) {
            loadUnlinked();
        }
    }

    private void loadUnlinked() {
        Note note = shownNote;
        if (note == null || backlinkService == null) {
            return;
        }
        long generation = unlinkedGeneration.incrementAndGet();
        background("unlinked-mentions", unlinkedGeneration, generation,
                () -> backlinkService.unlinkedMentions(note, () -> unlinkedGeneration.get() != generation),
                panel::showUnlinked);
    }

    private void linkMention(Note source, Occurrence mention) {
        Note target = shownNote;
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
                refresh(target);
                if (panel.isUnlinkedExpanded()) {
                    loadUnlinked();
                }
            } else {
                status.accept(getString("links.mention_changed"));
                loadUnlinked();
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
