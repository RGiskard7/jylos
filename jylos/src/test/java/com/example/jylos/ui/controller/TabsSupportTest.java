package com.example.jylos.ui.controller;

import static com.example.jylos.tests.FxTestSupport.onFx;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.prefs.Preferences;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.example.jylos.data.dao.filesystem.FolderDAOFileSystem;
import com.example.jylos.data.dao.filesystem.NoteDAOFileSystem;
import com.example.jylos.data.dao.filesystem.TagDAOFileSystem;
import com.example.jylos.data.models.Note;
import com.example.jylos.event.events.SystemActionEvent.ActionType;
import com.example.jylos.service.NoteService;
import com.example.jylos.service.TagService;
import com.example.jylos.tests.FxTestSupport;
import com.example.jylos.ui.components.EditorTabs;
import com.example.jylos.ui.components.EditorTabsModel.OpenTarget;
import com.example.jylos.ui.components.ViewTabs;
import com.example.jylos.workspace.TabHistory;
import com.example.jylos.ui.graph.GraphCanvas;

import javafx.fxml.FXMLLoader;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

/**
 * The editor's tabs, as in Obsidian: what is opened takes the active tab (a new one with
 * Cmd/Ctrl), each tab goes back and forward through what it showed (the graph and a new tab
 * included), a new tab is filled by what is opened from it, and closing a tab shows the one
 * next to it, whatever it holds.
 */
class TabsSupportTest {

    @TempDir
    Path vault;
    private Preferences prefs;
    private NoteService notes;
    private TagService tags;

    @BeforeEach
    void setUp() throws Exception {
        prefs = Preferences.userRoot().node("/com/example/jylos/test/" + UUID.randomUUID());
        GraphController.usePreferences(prefs);
        Files.writeString(vault.resolve("A.md"), "[[B]]", StandardCharsets.UTF_8);
        Files.writeString(vault.resolve("B.md"), "texto", StandardCharsets.UTF_8);
        NoteDAOFileSystem dao = new NoteDAOFileSystem(vault.toString());
        notes = new NoteService(dao, new FolderDAOFileSystem(vault.toString()));
        tags = new TagService(new TagDAOFileSystem(dao), dao);
    }

    @AfterEach
    void tearDown() throws Exception {
        GraphController.usePreferences(null);
        prefs.removeNode();
    }

    /** As a note does, the graph opens in the active tab; with Cmd/Ctrl, in a new tab after it. */
    @Test
    void theGraphOpensInTheActiveTabOrANewOne() throws Exception {
        Assumptions.assumeTrue(FxTestSupport.isFxRuntimeAvailable());

        List<String> seen = onFx(() -> {
            Fixture f = new Fixture();
            f.notes("A.md", "B.md");
            f.tabs.open("A.md", "A", OpenTarget.CURRENT_TAB);
            f.views.openGraph();
            String inTheActiveTab = f.state() + " " + f.host.calls;
            Fixture g = new Fixture();
            g.notes("A.md", "B.md");
            g.tabs.open("A.md", "A", OpenTarget.CURRENT_TAB);
            g.views.openGraph(OpenTarget.NEW_TAB);
            return List.of(inTheActiveTab, g.state());
        });

        // Nothing follows a note while the graph is shown (the outline, the links, the counts).
        assertEquals(List.of("tabs [GRAPH, B.md] active GRAPH graph shown [confirm, no note, changed]",
                "tabs [A.md, GRAPH, B.md] active GRAPH graph shown"), seen);
    }

    /**
     * The menu and toolbar actions reach the tabs: the graph's opens it in the active tab, and
     * the one of Cmd/Ctrl+click or middle click on its button in a new tab after it.
     */
    @Test
    void theGraphActionsOpenItInTheActiveTabOrANewOne() throws Exception {
        Assumptions.assumeTrue(FxTestSupport.isFxRuntimeAvailable());

        List<String> seen = onFx(() -> {
            List<String> out = new ArrayList<>();
            for (ActionType action : List.of(ActionType.GRAPH_VIEW, ActionType.GRAPH_VIEW_NEW_TAB)) {
                Fixture f = new Fixture();
                f.notes("A.md", "B.md");
                f.tabs.open("A.md", "A", OpenTarget.CURRENT_TAB);
                Map<ActionType, Runnable> handlers = new EnumMap<>(ActionType.class);
                f.views.register(handlers);
                handlers.get(action).run();
                out.add(action + ": " + f.state());
            }
            return out;
        });

        assertEquals(List.of("GRAPH_VIEW: tabs [GRAPH, B.md] active GRAPH graph shown",
                "GRAPH_VIEW_NEW_TAB: tabs [A.md, GRAPH, B.md] active GRAPH graph shown"), seen);
    }

    /** The graph is opened once: opening it again brings its tab to the front. */
    @Test
    void openingTheGraphAgainBringsItsTabToTheFront() throws Exception {
        Assumptions.assumeTrue(FxTestSupport.isFxRuntimeAvailable());

        List<String> seen = onFx(() -> {
            Fixture f = new Fixture();
            f.notes("A.md", "B.md");
            f.views.openGraph();
            f.views.select("A.md");
            String onTheNote = f.state();
            f.host.calls.clear();
            f.views.openGraph();
            f.views.openGraph(); // already in front: nothing to do
            return List.of(onTheNote, f.state(), String.valueOf(f.host.calls));
        });

        assertEquals(List.of("tabs [A.md, GRAPH] active A.md graph hidden",
                "tabs [A.md, GRAPH] active GRAPH graph shown", "[confirm, no note, changed]"), seen);
    }

    /** Leaving a note with unsaved edits asks first, as switching notes does; cancelling stays. */
    @Test
    void cancellingTheSavePromptKeepsTheNote() throws Exception {
        Assumptions.assumeTrue(FxTestSupport.isFxRuntimeAvailable());

        String seen = onFx(() -> {
            Fixture f = new Fixture();
            f.notes("A.md", "B.md");
            f.host.leave = false;
            f.views.openGraph();
            f.views.newTab();
            f.views.select("A.md");
            f.views.close("B.md");
            return f.state();
        });

        assertEquals("tabs [A.md, B.md] active B.md graph hidden", seen);
    }

    @Test
    void pickingANoteTabHidesTheGraphAndShowsTheNote() throws Exception {
        Assumptions.assumeTrue(FxTestSupport.isFxRuntimeAvailable());

        List<String> seen = onFx(() -> {
            Fixture f = new Fixture();
            f.notes("A.md", "B.md");
            f.views.openGraph();
            f.host.calls.clear();
            f.views.select("A.md");
            return List.of(f.graphShown(), String.valueOf(f.host.calls));
        });

        assertEquals(List.of("graph hidden", "[confirm, show A.md, changed]"), seen);
    }

    /** The note loaded in the editor is not in sight while the graph or a new tab is in front of it. */
    @Test
    void theOpenNoteIsInSightOnlyWhileItsTabIsInFront() throws Exception {
        Assumptions.assumeTrue(FxTestSupport.isFxRuntimeAvailable());

        List<String> seen = onFx(() -> {
            Fixture f = new Fixture();
            f.notes("A.md");
            Note a = new Note("A.md", "A", "");
            f.tabs.open("A.md", "A", OpenTarget.CURRENT_TAB);
            List<String> out = new ArrayList<>();
            out.add("note: " + idOf(f.views.inSight(a)));
            f.views.openGraph(OpenTarget.NEW_TAB);
            out.add("graph: " + idOf(f.views.inSight(a)));
            f.views.newTab();
            out.add("new tab: " + idOf(f.views.inSight(a)));
            f.views.select("A.md");
            out.add("back on the note: " + idOf(f.views.inSight(a)));
            return out;
        });

        assertEquals(List.of("note: A.md", "graph: null", "new tab: null", "back on the note: A.md"), seen);
    }

    private static String idOf(Note note) {
        return note != null ? note.getId() : null;
    }

    @Test
    void closingTheGraphShowsTheTabNextToIt() throws Exception {
        Assumptions.assumeTrue(FxTestSupport.isFxRuntimeAvailable());

        List<String> seen = onFx(() -> {
            Fixture f = new Fixture();
            f.notes("A.md", "B.md");
            f.views.openGraph();
            f.host.calls.clear();
            f.views.close(ViewTabs.GRAPH);
            String withANoteLeft = f.state() + " " + f.host.calls;
            f.tabs.clear();
            f.views.openGraph();
            f.host.calls.clear();
            f.views.close(ViewTabs.GRAPH);
            String alone = f.state() + " " + f.host.calls;
            return List.of(withANoteLeft, alone);
        });

        assertEquals(List.of("tabs [A.md] active A.md graph hidden [show A.md, changed]",
                "tabs [] active null graph hidden [empty, changed]"), seen);
    }

    /** Closing a note shows the tab next to it even when that is a new tab or the graph. */
    @Test
    void closingANoteShowsTheTabNextToItWhateverItHolds() throws Exception {
        Assumptions.assumeTrue(FxTestSupport.isFxRuntimeAvailable());

        List<String> seen = onFx(() -> {
            Fixture f = new Fixture();
            f.notes("A.md");
            f.views.newTab();
            f.views.select("A.md");
            f.host.calls.clear();
            f.views.close("A.md");
            String newTabNext = f.state() + " " + f.host.calls;
            Fixture g = new Fixture();
            g.views.openGraph();
            g.notes("A.md");
            g.host.calls.clear();
            g.views.close("A.md");
            return List.of(newTabNext, g.state() + " " + g.host.calls);
        });

        assertEquals(List.of("tabs [EMPTY] active EMPTY graph hidden [confirm, empty, changed]",
                "tabs [GRAPH] active GRAPH graph shown [confirm, no note, changed]"), seen);
    }

    /** As a link does: a note clicked in the graph takes its tab; with Cmd/Ctrl, a new tab. */
    @Test
    void aNoteClickedInTheGraphTakesItsTabOrANewOne() throws Exception {
        Assumptions.assumeTrue(FxTestSupport.isFxRuntimeAvailable());

        List<String> seen = onFx(() -> {
            Fixture f = new Fixture();
            f.notes("A.md", "B.md");
            f.views.openGraph();
            f.host.calls.clear();
            f.views.openNoteFromGraph("B.md", false);
            String sameTab = f.host.calls + " " + f.state();
            Fixture g = new Fixture();
            g.notes("A.md", "B.md");
            g.views.openGraph();
            g.host.calls.clear();
            g.views.openNoteFromGraph("B.md", true);
            return List.of(sameTab, g.host.calls + " " + g.state());
        });

        assertEquals(List.of("[open B.md, changed] tabs [A.md, B.md] active B.md graph hidden",
                "[open in new tab B.md, changed] tabs [A.md, GRAPH, B.md] active B.md graph hidden"), seen);
    }

    /** Back from a note opened from the graph returns to the graph, and forward to the note. */
    @Test
    void backReturnsToTheGraph() throws Exception {
        Assumptions.assumeTrue(FxTestSupport.isFxRuntimeAvailable());

        List<String> seen = onFx(() -> {
            Fixture f = new Fixture();
            f.notes("A.md");
            f.views.openGraph();
            f.views.openNoteFromGraph("B.md", false);
            List<String> out = new ArrayList<>();
            f.views.back();
            out.add(f.state());
            f.views.back();
            out.add(f.state());
            f.views.forward();
            out.add(f.state());
            f.views.forward();
            out.add(f.state());
            return out;
        });

        assertEquals(List.of("tabs [GRAPH] active GRAPH graph shown", "tabs [A.md] active A.md graph hidden",
                "tabs [GRAPH] active GRAPH graph shown", "tabs [B.md] active B.md graph hidden"), seen);
    }

    /** The + opens an empty tab at the end, showing what can be opened; picking it again shows that. */
    @Test
    void thePlusOpensAnEmptyTab() throws Exception {
        Assumptions.assumeTrue(FxTestSupport.isFxRuntimeAvailable());

        List<String> seen = onFx(() -> {
            Fixture f = new Fixture();
            f.notes("A.md");
            f.views.openGraph(OpenTarget.NEW_TAB);
            f.views.select("A.md");
            f.host.calls.clear();
            f.views.newTab();
            String opened = f.state() + " " + f.host.calls;
            f.views.select("A.md");
            f.host.calls.clear();
            f.views.select(f.tabs.getOpenIds().get(2));
            return List.of(opened, f.state() + " " + f.host.calls);
        });

        assertEquals(List.of("tabs [A.md, GRAPH, EMPTY] active EMPTY graph hidden [confirm, empty, changed]",
                "tabs [A.md, GRAPH, EMPTY] active EMPTY graph hidden [confirm, empty, changed]"), seen);
    }

    /** What is opened from a new tab fills it, a new note "in a new tab" included; back returns to it. */
    @Test
    void aNewTabIsFilledByWhatIsOpenedFromIt() throws Exception {
        Assumptions.assumeTrue(FxTestSupport.isFxRuntimeAvailable());

        List<String> seen = onFx(() -> {
            Fixture f = new Fixture();
            f.notes("A.md");
            f.views.newTab();
            f.views.openGraph();
            String graphFillsIt = f.state();
            f.views.newTab();
            f.host.host().openNoteInNewTab().accept(notes.getNoteById("B.md").orElseThrow()); // a new note
            String noteFillsIt = f.state();
            f.views.back();
            return List.of(graphFillsIt, noteFillsIt, f.state());
        });

        assertEquals(List.of("tabs [A.md, GRAPH] active GRAPH graph shown",
                "tabs [A.md, GRAPH, B.md] active B.md graph hidden",
                "tabs [A.md, GRAPH, EMPTY] active EMPTY graph hidden"), seen);
    }

    /**
     * A saved workspace reopens its tabs in their order, the graph's among them, brings its
     * active one to the front, and counts the notes that no longer exist. New tabs are not
     * reopened, nor counted as missing.
     */
    @Test
    void aWorkspaceReopensItsTabsWithTheGraph() throws Exception {
        Assumptions.assumeTrue(FxTestSupport.isFxRuntimeAvailable());

        List<String> seen = onFx(() -> {
            Fixture f = new Fixture();
            int missing = f.views.restoreTabs(List.of("A.md", ViewTabs.GRAPH, "Borrada.md",
                    ViewTabs.newEmptyTab(), "B.md"), ViewTabs.GRAPH, List.of());
            String graphInFront = f.state() + " missing " + missing;
            Fixture g = new Fixture();
            g.views.restoreTabs(List.of(ViewTabs.GRAPH, "A.md"), "Borrada.md", List.of()); // its active note is gone
            return List.of(graphInFront, g.state());
        });

        assertEquals(List.of("tabs [A.md, GRAPH, B.md] active GRAPH graph shown missing 1",
                "tabs [GRAPH, A.md] active A.md graph hidden"), seen);
    }

    /**
     * A workspace reopens each tab with its back and forward, without the notes that no longer
     * exist nor new tabs (their ids do not last), and they are read back as they are now.
     */
    @Test
    void aWorkspaceReopensEachTabWithItsBackAndForward() throws Exception {
        Assumptions.assumeTrue(FxTestSupport.isFxRuntimeAvailable());

        List<String> seen = onFx(() -> {
            Fixture f = new Fixture();
            List<String> out = new ArrayList<>();
            f.views.restoreTabs(List.of("A.md", "Borrada.md"), "A.md", List.of(
                    new TabHistory(List.of("Borrada.md", ViewTabs.GRAPH, ViewTabs.newEmptyTab(), "B.md"),
                            List.of("Borrada.md")),
                    new TabHistory(List.of("B.md"), List.of())));
            out.add(f.state() + " " + f.views.histories().toString().replace(ViewTabs.GRAPH, "GRAPH"));
            f.views.back();
            out.add(f.state());
            f.views.back();
            out.add(f.state() + " " + f.views.histories().toString().replace(ViewTabs.GRAPH, "GRAPH"));
            return out;
        });

        assertEquals(List.of(
                "tabs [A.md] active A.md graph hidden [TabHistory[back=[GRAPH, B.md], forward=[], pinned=false]]",
                "tabs [B.md] active B.md graph hidden",
                "tabs [GRAPH] active GRAPH graph shown [TabHistory[back=[], forward=[A.md, B.md], pinned=false]]"), seen);
    }

    /** Pinned tabs are kept with the workspace (and the last session) and come back pinned, first. */
    @Test
    void aWorkspaceReopensItsPinnedTabsPinned() throws Exception {
        Assumptions.assumeTrue(FxTestSupport.isFxRuntimeAvailable());

        List<String> seen = onFx(() -> {
            Fixture f = new Fixture();
            f.views.restoreTabs(List.of("A.md", ViewTabs.GRAPH, "B.md"), "B.md", List.of(
                    new TabHistory(List.of(), List.of(), true),
                    new TabHistory(List.of(), List.of(), true),
                    new TabHistory(List.of(), List.of(), false)));
            return List.of(f.tabs.pinnedIds().toString().replace(ViewTabs.GRAPH, "GRAPH"),
                    f.views.histories().stream().map(TabHistory::pinned).toList().toString(),
                    f.tabs.getActiveId());
        });

        assertEquals(List.of("[A.md, GRAPH]", "[true, true, false]", "B.md"), seen);
    }

    /**
     * Opening another workspace first closes every tab, the graph's too, once the open note's
     * unsaved edits are dealt with; cancelling that keeps them all.
     */
    @Test
    void allTheTabsCloseUnlessTheSavePromptIsCancelled() throws Exception {
        Assumptions.assumeTrue(FxTestSupport.isFxRuntimeAvailable());

        List<String> seen = onFx(() -> {
            Fixture f = new Fixture();
            List<String> out = new ArrayList<>();
            f.notes("A.md", "B.md");
            f.views.openGraph(OpenTarget.NEW_TAB);
            f.host.leave = false;
            out.add(f.views.closeAll() + " " + f.state());
            f.host.leave = true;
            f.host.calls.clear();
            out.add(f.views.closeAll() + " " + f.state() + " " + f.host.calls);
            return out;
        });

        assertEquals(List.of("false tabs [A.md, B.md, GRAPH] active GRAPH graph shown",
                "true tabs [] active null graph hidden [confirm, empty, changed]"), seen);
    }

    /**
     * The Kanban command asks which board to open: the vault's boards, the one changed last
     * first. It opens none by itself.
     */
    @Test
    void theKanbanCommandAsksWhichBoardToOpen() throws Exception {
        Assumptions.assumeTrue(FxTestSupport.isFxRuntimeAvailable());
        String board = "%% jylos-kanban %%\n## Por hacer\n";
        Files.writeString(vault.resolve("Viejo.md"), board, StandardCharsets.UTF_8);
        Files.writeString(vault.resolve("Nuevo.md"), board, StandardCharsets.UTF_8);
        Files.setLastModifiedTime(vault.resolve("Viejo.md"), FileTime.fromMillis(1_000_000_000_000L));
        Files.setLastModifiedTime(vault.resolve("Nuevo.md"), FileTime.fromMillis(1_700_000_000_000L));
        notes.refreshStorageCache();

        List<String> seen = onFx(() -> {
            Fixture f = new Fixture();
            f.notes("A.md");
            f.host.calls.clear();
            f.views.openKanban();
            return List.of(String.valueOf(f.host.calls), f.state());
        });

        assertEquals(List.of("[choose [Nuevo.md, Viejo.md]]", "tabs [A.md] active A.md graph hidden"), seen);
    }

    /** Coming back to the graph's tab finds it as it was left: the view is not opened afresh. */
    @Test
    void comingBackToTheGraphKeepsItsView() throws Exception {
        Assumptions.assumeTrue(FxTestSupport.isFxRuntimeAvailable());
        Fixture f = onFx(Fixture::new);
        onFx(() -> {
            f.notes("A.md", "B.md");
            f.views.openGraph();
            return null;
        });
        f.waitForBuilds(1);
        onFx(() -> {
            f.canvasField("offsetX").set(f.canvas(), 123.0); // the view dragged aside
            f.views.select("A.md");
            f.views.openGraph();
            return null;
        });
        f.waitForBuilds(2);

        assertEquals(123.0, (double) onFx(() -> f.canvasField("offsetX").get(f.canvas())), 1e-9);
    }

    /** What the window does for the tabs, written down, and done as the window does it. */
    private final class FakeHost {
        final List<String> calls = new ArrayList<>();
        final EditorTabs tabs;
        TabsSupport views;
        boolean leave = true;

        FakeHost(EditorTabs tabs) {
            this.tabs = tabs;
        }

        TabsSupport.Host host() {
            return new TabsSupport.Host(
                    () -> {
                        calls.add("confirm");
                        return leave;
                    },
                    noteId -> calls.add("show " + noteId),
                    note -> {
                        calls.add("open in new tab " + note.getId());
                        tabs.open(note.getId(), note.getTitle(), OpenTarget.NEW_TAB);
                        views.noteShown();
                    },
                    note -> {
                        calls.add("open " + note.getId());
                        tabs.open(note.getId(), note.getTitle(), OpenTarget.CURRENT_TAB);
                        views.noteShown();
                    },
                    () -> calls.add("empty"),
                    () -> calls.add("no note"),
                    () -> calls.add("changed"),
                    boards -> calls.add("choose " + boards.stream().map(Note::getId).toList()));
        }
    }

    private final class Fixture {
        final EditorTabs tabs = new EditorTabs(new HBox(), null, null, key -> key);
        final FakeHost host = new FakeHost(tabs);
        final TabsSupport views = new TabsSupport();
        final VBox graphView;
        final GraphController graph;
        final List<Object> builds = new CopyOnWriteArrayList<>();

        Fixture() throws Exception {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/example/jylos/ui/view/GraphView.fxml"),
                    ResourceBundle.getBundle("com.example.jylos.i18n.messages", Locale.forLanguageTag("es")));
            graphView = loader.load();
            graph = loader.getController();
            graph.wire(null, notes, tags, loader.getResources(), (id, newTab) -> { }, () -> { }, () -> "A.md",
                    builds::add);
            views.wire(graphView, graph, notes, () -> false, key -> key, status -> { }, () -> tabs, host.host());
            host.views = views;
        }

        /** Opens these notes in tabs, the last one active. */
        void notes(String... ids) {
            for (String id : ids) {
                tabs.open(id, id, OpenTarget.NEW_TAB);
            }
        }

        GraphCanvas canvas() throws Exception {
            Field field = GraphController.class.getDeclaredField("canvas");
            field.setAccessible(true);
            return (GraphCanvas) field.get(graph);
        }

        Field canvasField(String name) throws Exception {
            Field field = GraphCanvas.class.getDeclaredField(name);
            field.setAccessible(true);
            return field;
        }

        void waitForBuilds(int count) throws Exception {
            long until = System.currentTimeMillis() + 10_000;
            while (builds.size() < count && System.currentTimeMillis() < until) {
                Thread.sleep(25);
            }
            assertEquals(count, builds.size(), "graph builds");
        }

        String state() {
            return ("tabs " + tabs.getOpenIds() + " active " + tabs.getActiveId() + " " + graphShown())
                    .replace(ViewTabs.GRAPH, "GRAPH").replaceAll("jylos:view/empty/\\d+", "EMPTY");
        }

        String graphShown() {
            return graphView.isVisible() ? "graph shown" : "graph hidden";
        }
    }
}
