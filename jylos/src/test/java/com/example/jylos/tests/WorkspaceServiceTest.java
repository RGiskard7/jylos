package com.example.jylos.tests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.example.jylos.workspace.Workspace;
import com.example.jylos.workspace.WorkspaceRepository;
import com.example.jylos.workspace.WorkspaceService;

/** Tests workspace serialization and the service CRUD over a temp-file repository. */
class WorkspaceServiceTest {

    private WorkspaceService service(Path dir) {
        return new WorkspaceService(new WorkspaceRepository(dir.resolve("workspaces.dat")));
    }

    private Workspace live(List<String> open, String active) {
        return WorkspaceService.liveState(open, active, "SPLIT", true, false, 0.22, 0.25, "filesystem");
    }

    @Test
    void serializeAndParseRoundTrip() {
        Workspace ws = new Workspace("id-1", "Programming", "2026-06-13T10:00:00Z", "2026-06-13T11:00:00Z",
                List.of("a.md", "folder/b.md"), "a.md", "SPLIT", true, false, 0.22, 0.25, "filesystem");
        Workspace back = Workspace.parse(ws.serialize());
        assertNotNull(back);
        assertEquals(ws, back, "round-trip must preserve every field");
    }

    @Test
    void parseRejectsCorruptLines() {
        assertNull(Workspace.parse(null));
        assertNull(Workspace.parse(""));
        assertNull(Workspace.parse("not a valid workspace line"));
    }

    @Test
    void parsePreservesEmptyOpenListAndNullActive() {
        Workspace ws = new Workspace("id", "Empty", "c", "u", List.of(), null,
                "", false, false, -1, -1, "sqlite");
        Workspace back = Workspace.parse(ws.serialize());
        assertNotNull(back);
        assertTrue(back.openNoteIds().isEmpty());
        assertNull(back.activeNoteId());
    }

    @Test
    void saveCreatesAndLoadsWorkspace(@TempDir Path dir) {
        WorkspaceService service = service(dir);
        Workspace saved = service.save("Research", live(List.of("n1", "n2"), "n1"));

        assertNotNull(saved.id());
        assertEquals("Research", saved.name());
        assertEquals(List.of("n1", "n2"), saved.openNoteIds());

        // A fresh service over the same file sees the persisted workspace.
        List<Workspace> reloaded = service(dir).list();
        assertEquals(1, reloaded.size());
        assertEquals("Research", reloaded.get(0).name());
    }

    @Test
    void saveWithExistingNameOverwritesKeepingId(@TempDir Path dir) {
        WorkspaceService service = service(dir);
        Workspace first = service.save("Writing", live(List.of("a"), "a"));
        Workspace second = service.save("writing", live(List.of("a", "b", "c"), "b")); // same name, different case

        assertEquals(first.id(), second.id(), "same name overwrites, keeping the id");
        assertEquals(first.createdAt(), second.createdAt(), "creation time preserved on overwrite");
        assertEquals(1, service.list().size(), "no duplicate created");
        assertEquals(3, service.list().get(0).openNoteIds().size(), "state updated");
    }

    @Test
    void updateByIdRefreshesState(@TempDir Path dir) {
        WorkspaceService service = service(dir);
        Workspace saved = service.save("Personal", live(List.of("a"), "a"));

        var updated = service.update(saved.id(), live(List.of("a", "b"), "b"));
        assertTrue(updated.isPresent());
        assertEquals(2, updated.get().openNoteIds().size());
        assertEquals(saved.id(), updated.get().id());

        assertTrue(service.update("does-not-exist", live(List.of(), null)).isEmpty());
    }

    @Test
    void deleteRemovesWorkspace(@TempDir Path dir) {
        WorkspaceService service = service(dir);
        Workspace a = service.save("A", live(List.of("x"), "x"));
        service.save("B", live(List.of("y"), "y"));

        service.delete(a.id());
        List<Workspace> remaining = service.list();
        assertEquals(1, remaining.size());
        assertEquals("B", remaining.get(0).name());
    }

    @Test
    void corruptLineIsSkippedNotFatal(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("workspaces.dat");
        WorkspaceService service = new WorkspaceService(new WorkspaceRepository(file));
        Workspace good = service.save("Good", live(List.of("a"), "a"));

        // Prepend a garbage line; the good workspace must still load.
        String contents = Files.readString(file);
        Files.writeString(file, "###corrupt###\n" + contents);

        List<Workspace> loaded = service.list();
        assertEquals(1, loaded.size());
        assertEquals(good.id(), loaded.get(0).id());
        assertFalse(loaded.isEmpty());
    }
}
