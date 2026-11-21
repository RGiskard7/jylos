package com.example.jylos.ui.controller;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Optional;
import java.util.function.BooleanSupplier;
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

import javafx.application.Platform;

/**
 * Watches the open folder vault for changes made outside the app (another app, a sync service
 * such as iCloud), so the list, the graph, the tags and the open note follow them without a
 * refresh. Only the changed files are read ({@link NoteService#applyExternalChanges}), off the
 * JavaFX thread; what changed is then shown on it. If the system drops changes, the vault is
 * refreshed as with the Refresh button.
 *
 * @author Edu Díaz (RGiskard7)
 * @since 2.6.1
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
     */
    record Editor(Supplier<Note> current, BooleanSupplier modified, Runnable refreshOpenNotes, Runnable refreshTabs,
            Runnable warnChanged) {
    }

    private VaultWatcher watcher;

    /**
     * Starts watching the vault of {@code notes} (nothing for storages without files).
     *
     * @param generation the backend session it belongs to; its changes are dropped once
     *                   {@code active} says it is not the current one
     * @param refreshAll reads the whole vault again (JavaFX thread)
     */
    void start(NoteService notes, long generation, LongPredicate active, EventBus eventBus, Editor editor,
            Runnable refreshAll) {
        if (watcher != null || notes == null) {
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
        eventBus.publish(new NoteEvents.NotesRefreshRequestedEvent());
        Note current = editor.current().get();
        switch (ExternalChangeSupport.react(changes, current != null ? current.getId() : null,
                editor.modified().getAsBoolean())) {
            case RELOAD, MISSING -> editor.refreshOpenNotes().run();
            case WARN -> {
                editor.refreshTabs().run();
                editor.warnChanged().run();
            }
            case KEEP -> editor.refreshTabs().run();
        }
    }

    /** Stops watching; nothing is reported afterwards. */
    void stop() {
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
