package com.example.jylos.ui.controller;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.LongPredicate;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.example.jylos.config.LoggerConfig;
import com.example.jylos.data.dao.ExternalChanges;
import com.example.jylos.data.dao.interfaces.NoteDAO;
import com.example.jylos.data.dao.filesystem.VaultWatcher;
import com.example.jylos.data.models.Note;
import com.example.jylos.event.EventBus;
import com.example.jylos.event.events.NoteEvents;
import com.example.jylos.service.NoteService;

import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.util.Duration;

/**
 * Watches the open folder vault for changes made outside the app (another app, a sync service
 * such as iCloud), so the list, the graph, the tags and the open note follow them without a
 * refresh. Only the changed files are read ({@link NoteService#applyExternalChanges}), off the
 * JavaFX thread; what changed is then shown on it. If the system drops changes, the vault is
 * refreshed as with the Refresh button.
 *
 * <p>Notes written by something other than the app's views, such as an MCP client, are shown
 * the same way ({@link NoteEvents.ExternalNoteChangesEvent}): they go through the app's own
 * storage, so the watcher takes them for the app's own writes and does not report them. Like
 * the watcher's, they are shown in batches: once {@link #QUIET} passes without another, or at
 * most {@link #MAX_WAIT_MS} ms after the first, so an agent writing fifty notes in a row
 * refreshes the lists once, not fifty times.</p>
 *
 * @author Edu Díaz (RGiskard7)
 * @since 2.7.0
 */
final class VaultWatchSupport {

    private static final Logger logger = LoggerConfig.getLogger(VaultWatchSupport.class);

    /**
     * What the editor offers to follow a change made outside.
     *
     * @param current          the note shown in the editor, or null
     * @param modified         whether it has unsaved edits
     * @param refreshOpenNotes shows the open note again as it is now, or handles it as missing,
     *                         and the other tabs (what the Refresh button does to them)
     * @param refreshTabs      re-reads the inactive tabs only, closing those whose note is gone
     * @param warnChanged      tells the open note changed outside while it has unsaved edits
     * @param rebindTab        gives the tab of a note moved outside its new id and title
     * @param followOpenNote   makes the editor show the open note under its new id and title, as
     *                         when the app renames it itself (unsaved edits stay)
     */
    record Editor(Supplier<Note> current, BooleanSupplier modified, Runnable refreshOpenNotes, Runnable refreshTabs,
            Runnable warnChanged, TabRebind rebindTab, Consumer<Note> followOpenNote) {
    }

    /** Gives the tab of note {@code oldId} the id {@code newId} and the title {@code title}. */
    @FunctionalInterface
    interface TabRebind {
        void rebind(String oldId, String newId, String title);
    }

    /** How long the changes others write wait for another before they are shown. */
    static final Duration QUIET = Duration.millis(400);
    /** The longest the first of a run of changes waits to be shown while more keep coming. */
    static final long MAX_WAIT_MS = 2000;

    private VaultWatcher watcher;
    private EventBus.Subscription otherWriters;
    /** Changes others wrote that wait to be shown, in the order they were written. */
    private final List<ExternalChanges> pending = new ArrayList<>();
    private long firstPendingAt;
    private PauseTransition quiet;

    /**
     * Starts watching the vault of {@code notes} (nothing for storages without files), and
     * showing the changes others write to it through the app, whatever the storage.
     *
     * @param generation the backend session it belongs to; its changes are dropped once
     *                   {@code active} says it is not the current one
     * @param refreshAll reads the whole vault again (JavaFX thread)
     */
    void start(NoteService notes, long generation, LongPredicate active, EventBus eventBus, Editor editor,
            Runnable refreshAll) {
        if (notes == null) {
            return;
        }
        if (otherWriters == null && eventBus != null) {
            otherWriters = eventBus.subscribe(NoteEvents.ExternalNoteChangesEvent.class, event -> {
                if (active.test(generation)) {
                    queue(event.getChanges(), () -> {
                        if (active.test(generation)) {
                            show(takePending(), eventBus, editor);
                        }
                    });
                }
            });
        }
        if (watcher != null) {
            return;
        }
        Optional<Path> root = notes.getStorageRoot();
        if (root.isEmpty()) {
            return;
        }
        try {
            watcher = VaultWatcher.start(root.get(), paths -> {
                if (!active.test(generation)) {
                    return;
                }
                ExternalChanges changes = notes.applyExternalChanges(paths);
                if (!changes.isEmpty()) {
                    Platform.runLater(() -> {
                        if (active.test(generation)) {
                            show(changes, eventBus, editor);
                        }
                    });
                }
            }, () -> Platform.runLater(() -> {
                if (active.test(generation)) {
                    refreshAll.run();
                }
            }));
        } catch (IOException | RuntimeException e) {
            logger.log(Level.WARNING, "Could not watch the vault for changes made outside the app; Refresh shows them", e);
        }
    }

    /**
     * Shows what changed (JavaFX thread): everything that lists notes is refreshed from what the
     * vault already listed (no file is read), and the open note reacts only if the change
     * touched it ({@link ExternalChangeSupport}).
     */
    static void show(ExternalChanges changes, EventBus eventBus, Editor editor) {
        show(List.of(changes), eventBus, editor);
    }

    /**
     * Shows a batch of changes, in the order they happened (JavaFX thread): the lists once, and
     * the open note through each change in turn, so a note moved and then edited is followed to
     * its new path and then shown as it is now. The open note is read again at most once, at the
     * end, and the other tabs too.
     */
    static void show(List<ExternalChanges> batch, EventBus eventBus, Editor editor) {
        eventBus.publish(new NoteEvents.NotesRefreshRequestedEvent());
        boolean readOpenNoteAgain = false;
        boolean warn = false;
        for (ExternalChanges changes : batch) {
            Note current = editor.current().get();
            String currentId = current != null ? current.getId() : null;
            changes.renamed().forEach((from, to) -> editor.rebindTab().rebind(from, to, titleOf(to)));
            switch (ExternalChangeSupport.react(changes, currentId, editor.modified().getAsBoolean())) {
                case FOLLOW -> editor.followOpenNote().accept(movedTo(current, changes.renamed().get(currentId)));
                case RELOAD, MISSING -> readOpenNoteAgain = true;
                case WARN -> warn = true;
                case KEEP -> {
                }
            }
        }
        if (readOpenNoteAgain) {
            editor.refreshOpenNotes().run();
        } else {
            editor.refreshTabs().run();
        }
        if (warn) {
            editor.warnChanged().run();
        }
    }

    /** Holds {@code changes} until the run they belong to pauses, then runs {@code flush} (JavaFX thread). */
    private void queue(ExternalChanges changes, Runnable flush) {
        long now = System.currentTimeMillis();
        synchronized (pending) {
            if (pending.isEmpty()) {
                firstPendingAt = now;
            }
            pending.add(changes);
        }
        if (quiet == null) {
            quiet = new PauseTransition();
        }
        quiet.stop();
        quiet.setOnFinished(e -> flush.run());
        quiet.setDuration(now - firstPendingAt >= MAX_WAIT_MS ? Duration.ONE : QUIET);
        quiet.playFromStart();
    }

    private List<ExternalChanges> takePending() {
        synchronized (pending) {
            List<ExternalChanges> batch = List.copyOf(pending);
            pending.clear();
            return batch;
        }
    }

    /** The title the vault lists a note under: its file name, without {@code .md} for a note. */
    private static String titleOf(String id) {
        String name = id.substring(id.lastIndexOf('/') + 1);
        return name.endsWith(".md") ? name.substring(0, name.length() - 3) : name;
    }

    /** {@code note} as it is after its file moved to {@code id}: same marks, new id and title. */
    private static Note movedTo(Note note, String id) {
        Note moved = new Note(id, titleOf(id), note.getContent());
        moved.setFavorite(note.isFavorite());
        moved.setPinned(note.isPinned());
        moved.setPrivate(note.isPrivate());
        moved.setDeleted(note.isDeleted());
        moved.setDeletedDate(note.getDeletedDate());
        moved.setStatus(note.getStatus());
        return moved;
    }

    /** Stops watching; nothing is reported afterwards. */
    void stop() {
        if (otherWriters != null) {
            otherWriters.cancel();
            otherWriters = null;
        }
        // What waits belongs to the vault being left.
        if (quiet != null) {
            quiet.stop();
        }
        synchronized (pending) {
            pending.clear();
        }
        if (watcher != null) {
            watcher.close();
            watcher = null;
        }
    }

    /**
     * Ends the vault's session (another vault is opened, or the app closes): stops watching it,
     * then lets its storage write what it keeps pending ({@link NoteDAO#close()}).
     */
    void endSession(NoteDAO notes) {
        stop();
        if (notes != null) {
            notes.close();
        }
    }
}
