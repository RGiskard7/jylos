package com.example.jylos.ui.controller;

import static com.example.jylos.tests.FxTestSupport.onFx;
import static com.example.jylos.tests.FxTestSupport.runFx;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.example.jylos.data.dao.ExternalChanges;
import com.example.jylos.data.dao.filesystem.FolderDAOFileSystem;
import com.example.jylos.data.dao.filesystem.NoteDAOFileSystem;
import com.example.jylos.data.models.Note;
import com.example.jylos.event.EventBus;
import com.example.jylos.event.events.NoteEvents;
import com.example.jylos.service.NoteService;
import com.example.jylos.tests.FxTestSupport;

/**
 * What a change made outside the app does to the note open in the editor: only a change that
 * touched it makes it react. It reloads if it has no unsaved edits, is kept with a warning if
 * it has them (saving then asks, as it always did), and is handled as missing if it was
 * deleted. Everything that lists notes is refreshed in every case.
 */
class ExternalChangeSupportTest {

    private static ExternalChanges changes(Set<String> created, Set<String> changed, Set<String> deleted) {
        return new ExternalChanges(created, changed, deleted, false);
    }

    @AfterEach
    void clearEventBus() {
        EventBus.getInstance().clear();
    }

    @Test
    void theOpenNoteChangedOutsideReloadsWhenItHasNoUnsavedEdits() {
        assertEquals(ExternalChangeSupport.OpenNote.RELOAD,
                ExternalChangeSupport.react(changes(Set.of(), Set.of("A.md"), Set.of()), "A.md", false));
    }

    @Test
    void withUnsavedEditsItIsKeptAndWarned() {
        assertEquals(ExternalChangeSupport.OpenNote.WARN,
                ExternalChangeSupport.react(changes(Set.of(), Set.of("A.md"), Set.of()), "A.md", true));
    }

    @Test
    void aChangeToAnotherNoteLeavesTheOpenOneAlone() {
        assertEquals(ExternalChangeSupport.OpenNote.KEEP,
                ExternalChangeSupport.react(changes(Set.of("C.md"), Set.of("B.md"), Set.of()), "A.md", false));
    }

    @Test
    void aDeletedOpenNoteIsMissingAndWithNoNoteOpenNothingReacts() {
        assertEquals(ExternalChangeSupport.OpenNote.MISSING,
                ExternalChangeSupport.react(changes(Set.of(), Set.of(), Set.of("A.md")), "A.md", true));
        assertEquals(ExternalChangeSupport.OpenNote.KEEP,
                ExternalChangeSupport.react(changes(Set.of(), Set.of("A.md"), Set.of()), null, false));
    }

    @Test
    void theOpenNoteMovedOutsideIsFollowed() {
        ExternalChanges moved = new ExternalChanges(Set.of("Libros/A.md"), Set.of(), Set.of("A.md"), false,
                Map.of("A.md", "Libros/A.md"));

        assertEquals(ExternalChangeSupport.OpenNote.FOLLOW, ExternalChangeSupport.react(moved, "A.md", true));
    }

    /** Tabs of moved notes take their new path, and the editor follows the open one, unsaved edits and all. */
    @Test
    void showingAMoveRebindsTheTabsAndFollowsTheOpenNote() throws Exception {
        Assumptions.assumeTrue(FxTestSupport.isFxRuntimeAvailable());
        Note open = new Note("A.md", "A", "texto");
        open.setFavorite(true);
        ExternalChanges moved = new ExternalChanges(Set.of("Libros/A nueva.md", "C.md"), Set.of(),
                Set.of("A.md", "B.md"), false, Map.of("A.md", "Libros/A nueva.md", "B.md", "C.md"));

        List<String> seen = onFx(() -> run(moved, open, true));

        assertEquals(List.of("lists refreshed", "tab A.md -> Libros/A nueva.md (A nueva)", "tab B.md -> C.md (C)",
                "follows Libros/A nueva.md (A nueva, favorite true)", "tabs refreshed"),
                seen.stream().sorted((x, y) -> order(x) - order(y)).toList());
    }

    private static int order(String step) {
        return step.startsWith("lists") ? 0 : step.startsWith("tab A") ? 1 : step.startsWith("tab B") ? 2
                : step.startsWith("follows") ? 3 : 4;
    }

    /** The editor's own actions are what runs: the ones the Refresh button uses. */
    @Test
    void showingAChangeRefreshesTheListsAndOnlyWhatTheChangeTouched() throws Exception {
        Assumptions.assumeTrue(FxTestSupport.isFxRuntimeAvailable());
        Note open = new Note("A.md", "A", "texto");

        List<String> touched = onFx(() -> run(changes(Set.of(), Set.of("A.md"), Set.of()), open, false));
        List<String> other = onFx(() -> run(changes(Set.of("C.md"), Set.of(), Set.of()), open, false));
        List<String> unsaved = onFx(() -> run(changes(Set.of(), Set.of("A.md"), Set.of()), open, true));
        List<String> deleted = onFx(() -> run(changes(Set.of(), Set.of(), Set.of("A.md")), open, true));

        assertEquals(List.of("lists refreshed", "open notes refreshed"), touched);
        assertEquals(List.of("lists refreshed", "tabs refreshed"), other);
        assertEquals(List.of("lists refreshed", "tabs refreshed", "warned"), unsaved);
        assertEquals(List.of("lists refreshed", "open notes refreshed"), deleted);
    }

    /**
     * Notes written by something other than the views (an MCP client) are shown as a change made
     * outside the app, since the vault watcher takes those writes for the app's own: from the
     * moment the vault is watched, and only for the vault session that is current.
     */
    @Test
    void aChangeWrittenByAnotherToolIsShownWhileItsVaultIsTheOneOpen(@TempDir Path vault) throws Exception {
        Assumptions.assumeTrue(FxTestSupport.isFxRuntimeAvailable());
        List<String> seen = new CopyOnWriteArrayList<>();
        AtomicBoolean current = new AtomicBoolean(true);
        VaultWatchSupport watch = new VaultWatchSupport();
        EventBus bus = listening(seen);
        NoteEvents.ExternalNoteChangesEvent openNoteChanged = new NoteEvents.ExternalNoteChangesEvent(Set.of(),
                Set.of("A.md"), Set.of(), false, Map.of());
        try {
            watch.start(service(vault), 1, generation -> current.get(), bus,
                    editor(new AtomicReference<>(new Note("A.md", "A", "texto")), false, seen), () -> seen.add("all"));

            runFx(() -> bus.publish(openNoteChanged));
            List<String> whileCurrent = shown(seen);
            current.set(false);
            runFx(() -> bus.publish(openNoteChanged));
            List<String> afterAnotherVault = shown(seen);
            current.set(true);
            watch.stop();
            runFx(() -> bus.publish(openNoteChanged));
            List<String> afterStop = shown(seen);

            assertEquals(List.of("lists refreshed", "open notes refreshed"), whileCurrent);
            assertEquals(List.of(), afterAnotherVault);
            assertEquals(List.of(), afterStop);
        } finally {
            watch.stop();
        }
    }

    /**
     * A run of writes (an agent saving one note after another) is shown once, when it pauses:
     * the lists are refreshed once, and the open note goes through each change in order, so a
     * note renamed and then edited is followed to its new name and shown as it is now.
     */
    @Test
    void aRunOfWritesIsShownOnceAndInOrder(@TempDir Path vault) throws Exception {
        Assumptions.assumeTrue(FxTestSupport.isFxRuntimeAvailable());
        List<String> seen = new CopyOnWriteArrayList<>();
        VaultWatchSupport watch = new VaultWatchSupport();
        EventBus bus = listening(seen);
        try {
            watch.start(service(vault), 1, generation -> true, bus,
                    editor(new AtomicReference<>(new Note("A.md", "A", "texto")), false, seen), () -> seen.add("all"));

            runFx(() -> {
                bus.publish(new NoteEvents.ExternalNoteChangesEvent(Set.of("B.md"), Set.of(), Set.of("A.md"), false,
                        Map.of("A.md", "B.md")));
                bus.publish(new NoteEvents.ExternalNoteChangesEvent(Set.of(), Set.of("B.md"), Set.of(), false, Map.of()));
                bus.publish(new NoteEvents.ExternalNoteChangesEvent(Set.of("C.md"), Set.of(), Set.of(), false, Map.of()));
            });

            assertEquals(List.of("lists refreshed", "tab A.md -> B.md (B)", "follows B.md (B, favorite false)",
                    "open notes refreshed"), shown(seen));
        } finally {
            watch.stop();
        }
    }

    private static NoteService service(Path vault) {
        return new NoteService(new NoteDAOFileSystem(vault.toString()), new FolderDAOFileSystem(vault.toString()));
    }

    private static EventBus listening(List<String> seen) {
        EventBus bus = EventBus.getInstance();
        bus.clear();
        bus.subscribe(NoteEvents.NotesRefreshRequestedEvent.class, e -> seen.add("lists refreshed"));
        return bus;
    }

    /**
     * What was shown once the changes published so far had time to be: everything shown within
     * a while after them, taken out of {@code seen}.
     */
    private static List<String> shown(List<String> seen) throws Exception {
        Thread.sleep(1500);
        runFx(() -> { });
        List<String> taken = List.copyOf(seen);
        seen.clear();
        return taken;
    }

    private static List<String> run(ExternalChanges changes, Note open, boolean modified) {
        List<String> seen = new ArrayList<>();
        EventBus bus = EventBus.getInstance();
        bus.clear();
        bus.subscribe(NoteEvents.NotesRefreshRequestedEvent.class, e -> seen.add("lists refreshed"));
        VaultWatchSupport.show(changes, bus, editor(open, modified, seen));
        return seen;
    }

    /** An editor that writes down what it is asked to do. */
    private static VaultWatchSupport.Editor editor(Note open, boolean modified, List<String> seen) {
        return editor(new AtomicReference<>(open), modified, seen);
    }

    /** The same, showing the note it follows from then on, as the editor does. */
    private static VaultWatchSupport.Editor editor(AtomicReference<Note> open, boolean modified, List<String> seen) {
        return new VaultWatchSupport.Editor(open::get, () -> modified,
                () -> seen.add("open notes refreshed"), () -> seen.add("tabs refreshed"), () -> seen.add("warned"),
                (from, to, title) -> seen.add("tab " + from + " -> " + to + " (" + title + ")"),
                note -> {
                    seen.add("follows " + note.getId() + " (" + note.getTitle() + ", favorite " + note.isFavorite() + ")");
                    open.set(note);
                });
    }
}
