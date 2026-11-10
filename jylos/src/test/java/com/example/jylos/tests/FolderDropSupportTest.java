package com.example.jylos.tests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.example.jylos.data.dao.sqlite.FolderDAOSQLite;
import com.example.jylos.data.dao.sqlite.NoteDAOSQLite;
import com.example.jylos.data.models.Folder;
import com.example.jylos.data.models.Note;
import com.example.jylos.event.AppEvent;
import com.example.jylos.event.EventBus;
import com.example.jylos.event.events.FolderEvents;
import com.example.jylos.event.events.NoteEvents;
import com.example.jylos.service.FolderService;
import com.example.jylos.service.NoteService;
import com.example.jylos.ui.controller.FolderDropSupport;

import javafx.application.Platform;

/**
 * Characterization of dropping a dragged note or folder onto a folder row (moved out of
 * SidebarController so the Explorer tree can share it). Runs against real SQLite services.
 */
class FolderDropSupportTest {

    @TempDir
    Path tempDir;

    private Connection connection;
    private FolderService folders;
    private NoteService notes;
    private final List<String> statuses = new ArrayList<>();
    private final AtomicInteger afterMoveRuns = new AtomicInteger();
    private EventBus bus;
    private FolderDropSupport drop;

    @BeforeEach
    void setUp() throws Exception {
        SQLiteTestSupport.configureFreshDatabase(tempDir.resolve("drop.db"));
        connection = SQLiteTestSupport.openConnection();
        FolderDAOSQLite folderDAO = new FolderDAOSQLite(connection);
        NoteDAOSQLite noteDAO = new NoteDAOSQLite(connection);
        folders = new FolderService(folderDAO, noteDAO);
        notes = new NoteService(noteDAO, folderDAO);
        drop = new FolderDropSupport(() -> notes, () -> folders, () -> bus, key -> key + " {0}",
                statuses::add, afterMoveRuns::incrementAndGet);
    }

    @AfterEach
    void tearDown() throws Exception {
        SQLiteTestSupport.closeAndReset(connection);
    }

    @Test
    void aNoteDroppedOnAFolderMovesIntoIt() {
        Folder trabajo = folders.createFolder("Trabajo");
        Note note = notes.createNote("Acta", "x");

        assertTrue(drop.canAccept("note:" + note.getId(), trabajo));
        assertTrue(drop.drop("note:" + note.getId(), trabajo));

        assertEquals(List.of("Acta"), titles(notes.getNotesByFolder(trabajo)));
        assertEquals(List.of(), titles(notes.getNotesByFolder(new Folder("ROOT", "Jylos"))));
        assertEquals(List.of("status.note_moved_folder Trabajo"), statuses);
        assertEquals(1, afterMoveRuns.get());
    }

    @Test
    void aFolderDroppedOnAnotherFolderBecomesItsSubfolder() {
        Folder trabajo = folders.createFolder("Trabajo");
        Folder archivo = folders.createFolder("Archivo");

        assertTrue(drop.drop("folder:" + archivo.getId(), trabajo));

        assertEquals(List.of("Archivo"), titles(folders.getSubfolders(folders.getFolderById(trabajo.getId()).get())));
        assertEquals(List.of("Trabajo"), titles(folders.getSubfolders(new Folder("ROOT", "Jylos"))));
        assertEquals(List.of("status.folder_moved Trabajo"), statuses);
        assertEquals(1, afterMoveRuns.get());
    }

    @Test
    void aFolderDroppedOnTheVaultRootMovesToTheTopLevel() {
        Folder trabajo = folders.createFolder("Trabajo");
        Folder reuniones = folders.createSubfolder("Reuniones", trabajo);

        assertTrue(drop.drop("folder:" + reuniones.getId(), new Folder("ROOT", "Jylos")));

        assertEquals(List.of("Reuniones", "Trabajo"),
                titles(folders.getSubfolders(new Folder("ROOT", "Jylos"))).stream().sorted().toList());
    }

    @Test
    void aFolderCannotBeDroppedOnItselfOrIntoItsOwnSubfolder() {
        Folder trabajo = folders.createFolder("Trabajo");
        Folder reuniones = folders.createSubfolder("Reuniones", trabajo);

        assertFalse(drop.canAccept("folder:" + trabajo.getId(), trabajo));
        assertFalse(drop.canAccept("folder:" + trabajo.getId(), reuniones));
        assertFalse(drop.drop("folder:" + trabajo.getId(), reuniones));

        assertEquals(List.of("Reuniones"), titles(folders.getSubfolders(folders.getFolderById(trabajo.getId()).get())));
        assertEquals(List.of("Trabajo"), titles(folders.getSubfolders(new Folder("ROOT", "Jylos"))));
        assertEquals(0, afterMoveRuns.get());
        assertEquals(List.of(), statuses);
    }

    @Test
    void invalidPayloadsAndTargetsAreRejectedWithoutSideEffects() {
        Folder trabajo = folders.createFolder("Trabajo");
        Note note = notes.createNote("Acta", "x");

        assertFalse(drop.canAccept(null, trabajo));
        assertFalse(drop.canAccept("tag:x", trabajo));
        assertFalse(drop.canAccept("note:" + note.getId(), null));
        assertFalse(drop.canAccept("note:" + note.getId(), new Folder("INVISIBLE_ROOT", "x")));
        assertFalse(drop.canAccept("folder:", trabajo));
        assertFalse(drop.drop("note:no-such-note", trabajo));

        assertEquals(List.of("Acta"), titles(notes.getNotesByFolder(new Folder("ROOT", "Jylos"))));
        assertEquals(0, afterMoveRuns.get());
    }

    @Test
    void withoutServicesNothingIsAccepted() {
        FolderDropSupport unwired = new FolderDropSupport(() -> null, () -> null, () -> null, k -> k,
                statuses::add, afterMoveRuns::incrementAndGet);
        Folder trabajo = new Folder("t", "Trabajo");

        assertFalse(unwired.canAccept("note:a", trabajo));
        assertFalse(unwired.canAccept("folder:b", trabajo));
    }

    @Test
    void dragPayloadsNameTheItemAndTheVaultRootCannotBeDragged() {
        Note note = new Note("n1", "Acta", "x");
        assertEquals("note:n1", FolderDropSupport.notePayload(note));
        assertEquals("folder:a/b", FolderDropSupport.folderPayload(new Folder("a\\b", "B")));
        assertEquals(null, FolderDropSupport.folderPayload(new Folder("ROOT", "Jylos")));
        assertEquals(null, FolderDropSupport.notePayload(null));
    }

    @Test
    void aSuccessfulMoveAsksEveryListenerToReload() throws Exception {
        Assumptions.assumeTrue(FxTestSupport.isFxRuntimeAvailable());
        bus = EventBus.getInstance();
        List<Class<?>> published = new ArrayList<>();
        List<EventBus.Subscription> subs = List.of(
                bus.subscribe(NoteEvents.NoteSavedEvent.class, e -> published.add(e.getClass())),
                bus.subscribe(FolderEvents.FoldersRefreshRequestedEvent.class, e -> published.add(e.getClass())),
                bus.subscribe(NoteEvents.NotesRefreshRequestedEvent.class, e -> published.add(e.getClass())));
        try {
            Folder trabajo = folders.createFolder("Trabajo");
            Note note = notes.createNote("Acta", "x");
            assertTrue(drop.drop("note:" + note.getId(), trabajo));
            flushFxQueue();

            assertEquals(List.<Class<? extends AppEvent>>of(NoteEvents.NoteSavedEvent.class,
                    FolderEvents.FoldersRefreshRequestedEvent.class, NoteEvents.NotesRefreshRequestedEvent.class),
                    published);
        } finally {
            subs.forEach(EventBus.Subscription::cancel);
        }
    }

    private static void flushFxQueue() throws InterruptedException {
        CountDownLatch done = new CountDownLatch(1);
        Platform.runLater(done::countDown);
        assertTrue(done.await(10, TimeUnit.SECONDS));
    }

    private static List<String> titles(List<? extends com.example.jylos.data.models.interfaces.Component> items) {
        return items.stream().map(c -> c.getTitle()).toList();
    }
}
