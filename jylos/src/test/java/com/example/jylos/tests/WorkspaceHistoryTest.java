package com.example.jylos.tests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.example.jylos.workspace.SessionStore;
import com.example.jylos.workspace.TabHistory;
import com.example.jylos.workspace.Workspace;
import com.example.jylos.workspace.WorkspaceHistoryRepository;
import com.example.jylos.workspace.WorkspaceRepository;
import com.example.jylos.workspace.WorkspaceService;

/**
 * A workspace keeps each tab's back and forward, a workspace can be renamed, and the last
 * session (the tabs open when the app closed, and the workspace in use) is kept to reopen it.
 */
class WorkspaceHistoryTest {

    private static final List<TabHistory> HISTORY = List.of(
            new TabHistory(List.of("notas/a.md", "jylos:view/graph"), List.of("c.md")),
            TabHistory.NONE,
            new TabHistory(List.of(), List.of("d.md", "e.md")));

    private static Workspace live(List<String> open, String active) {
        return WorkspaceService.liveState(open, active, "SPLIT", true, false, 0.22, 0.25, "filesystem");
    }

    @Test
    void eachWorkspaceKeepsItsOwnHistory(@TempDir Path dir) {
        WorkspaceHistoryRepository repository = new WorkspaceHistoryRepository(dir.resolve("workspace-history.dat"));
        repository.save("w1", HISTORY);
        repository.save("w2", List.of(new TabHistory(List.of("x.md"), List.of())));
        repository.save("w3", List.of());

        WorkspaceHistoryRepository reread = new WorkspaceHistoryRepository(dir.resolve("workspace-history.dat"));
        assertEquals(HISTORY, reread.load("w1"));
        assertEquals(List.of(new TabHistory(List.of("x.md"), List.of())), reread.load("w2"));
        assertEquals(List.of(), reread.load("w3"));
        assertEquals(List.of(), reread.load("unknown"));

        reread.save("w1", List.of(TabHistory.NONE));
        reread.delete("w2");
        assertEquals(List.of(TabHistory.NONE), reread.load("w1"));
        assertEquals(List.of(), reread.load("w2"));
    }

    @Test
    void theServiceSavesRenamesAndDeletesAWorkspaceWithItsHistory(@TempDir Path dir) {
        WorkspaceService service = new WorkspaceService(new WorkspaceRepository(dir.resolve("workspaces.dat")),
                new WorkspaceHistoryRepository(dir.resolve("workspace-history.dat")));
        Workspace writing = service.save("Escritura", live(List.of("a.md", "b.md", "c.md"), "a.md"), HISTORY);
        Workspace research = service.save("Investigación", live(List.of("x.md"), "x.md"), List.of());

        assertEquals(HISTORY, service.history(writing.id()));

        Optional<Workspace> renamed = service.rename(writing.id(), "  Libro ");
        assertEquals(Optional.of("Libro"), renamed.map(Workspace::name));
        assertEquals(List.of("a.md", "b.md", "c.md"), service.findByName("Libro").orElseThrow().openNoteIds());
        assertEquals(HISTORY, service.history(writing.id()), "renaming keeps the history");

        assertTrue(service.rename(writing.id(), "investigación").isEmpty(), "a name another workspace has");
        assertTrue(service.rename("unknown", "Otro").isEmpty(), "an unknown workspace");
        assertEquals(List.of("Investigación", "Libro"), service.list().stream().map(Workspace::name).toList());

        service.delete(writing.id());
        assertEquals(List.of(), service.history(writing.id()));
        assertEquals(List.of("Investigación"), service.list().stream().map(Workspace::name).toList());
        assertEquals(research.id(), service.list().get(0).id());
    }

    @Test
    void theSessionKeepsTheTabsTheirHistoryAndTheWorkspaceInUse(@TempDir Path dir) {
        assertTrue(new SessionStore(dir).load().isEmpty(), "no session before the first close");

        Workspace tabs = live(List.of("a.md", "jylos:view/graph", "b.md"), "b.md");
        new SessionStore(dir).save(new Workspace("w1", "Escritura", null, null, tabs.openNoteIds(),
                tabs.activeNoteId(), "", true, false, -1, -1, ""), HISTORY);

        SessionStore reread = new SessionStore(dir);
        Workspace session = reread.load().orElseThrow();
        assertEquals(List.of("w1", "Escritura", "[a.md, jylos:view/graph, b.md]", "b.md"),
                List.of(session.id(), session.name(), session.openNoteIds().toString(), session.activeNoteId()));
        assertEquals(HISTORY, reread.history());

        reread.save(new Workspace("", "", null, null, List.of("c.md"), "c.md", "", true, false, -1, -1, ""),
                List.of(TabHistory.NONE));
        assertEquals(List.of("c.md"), new SessionStore(dir).load().orElseThrow().openNoteIds());
        assertEquals(List.of(TabHistory.NONE), new SessionStore(dir).history());
    }

    /** "No workspace" keeps its own tabs, apart from the session, to come back to them. */
    @Test
    void theTabsOfNoWorkspaceAreKeptApartFromTheSession(@TempDir Path dir) {
        SessionStore store = new SessionStore(dir);
        assertTrue(store.loadDefault().isEmpty(), "nothing kept yet");

        store.saveDefault(live(List.of("p.md", "q.md"), "q.md"), HISTORY);
        store.save(new Workspace("w1", "Escritura", null, null, List.of("a.md"), "a.md", "", true, false, -1, -1, ""),
                List.of(TabHistory.NONE));
        store.saveDefault(live(List.of("p.md", "r.md"), "r.md"), HISTORY.subList(0, 1));

        SessionStore reread = new SessionStore(dir);
        assertEquals(List.of("p.md", "r.md"), reread.loadDefault().orElseThrow().openNoteIds());
        assertEquals(HISTORY.subList(0, 1), reread.defaultHistory());
        assertEquals(List.of("w1", "[a.md]"), List.of(reread.load().orElseThrow().id(),
                reread.load().orElseThrow().openNoteIds().toString()));
        assertEquals(List.of(TabHistory.NONE), reread.history());
    }
}
