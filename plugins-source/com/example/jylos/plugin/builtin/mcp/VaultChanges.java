package com.example.jylos.plugin.builtin.mcp;

import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.example.jylos.data.models.Note;
import com.example.jylos.event.events.NoteEvents.ExternalNoteChangesEvent;
import com.example.jylos.service.NoteService;

/**
 * Tells the app what a tool changed, so its views (the explorer, the notes list, tags, graph)
 * and the note open in the editor follow it, as they follow a change made outside the app
 * ({@link ExternalNoteChangesEvent}).
 *
 * <p>Without it nothing in the window moves until a manual refresh: the tools write through
 * the same services as the app, so the vault watcher takes their writes for the app's own and
 * stays quiet. Only a write that happened is reported, once it is done.</p>
 *
 * @author Edu Díaz (RGiskard7)
 * @since 2.7.0
 */
final class VaultChanges {

    private static final Logger logger = Logger.getLogger(VaultChanges.class.getName());

    private final Consumer<ExternalNoteChangesEvent> sink;

    /** @param sink gets each change; the plugin publishes it */
    VaultChanges(Consumer<ExternalNoteChangesEvent> sink) {
        this.sink = sink;
    }

    /** The note {@code id} is new. */
    void created(String id) {
        report(Set.of(id), Set.of(), Set.of(), false, Map.of());
    }

    /** The notes {@code ids} changed where they are. */
    void changed(Collection<String> ids) {
        if (!ids.isEmpty()) {
            report(Set.of(), Set.copyOf(ids), Set.of(), false, Map.of());
        }
    }

    /** The note {@code id} is gone (to the trash). */
    void deleted(String id) {
        report(Set.of(), Set.of(), Set.of(id), false, Map.of());
    }

    /**
     * The note {@code from} is now {@code to}: renamed or moved. A storage whose ids are not
     * paths keeps the id, and then the note only changed.
     */
    void moved(String from, String to) {
        if (from.equals(to)) {
            changed(Set.of(from));
        } else {
            report(Set.of(to), Set.of(), Set.of(from), false, Map.of(from, to));
        }
    }

    /** A folder was created: no note changed. */
    void foldersChanged() {
        report(Set.of(), Set.of(), Set.of(), true, Map.of());
    }

    /**
     * What a write that touches notes it does not name did, from the note ids before and after
     * it (restoring from the trash, deleting a folder with its notes).
     */
    void between(Set<String> before, Set<String> after, boolean folders) {
        Set<String> created = new HashSet<>(after);
        created.removeAll(before);
        Set<String> deleted = new HashSet<>(before);
        deleted.removeAll(after);
        report(created, Set.of(), deleted, folders, Map.of());
    }

    /** The ids of the notes the vault lists now. */
    static Set<String> noteIds(NoteService noteService) {
        Set<String> ids = new HashSet<>();
        for (Note note : noteService.getAllNotes()) {
            ids.add(note.getId());
        }
        return ids;
    }

    /** The write is done whatever happens here: a failure to tell the app is only logged. */
    private void report(Set<String> created, Set<String> changed, Set<String> deleted, boolean folders,
            Map<String, String> renamed) {
        try {
            sink.accept(new ExternalNoteChangesEvent(created, changed, deleted, folders, renamed));
        } catch (RuntimeException e) {
            logger.log(Level.WARNING, "Could not tell the app about a change made over MCP", e);
        }
    }
}
