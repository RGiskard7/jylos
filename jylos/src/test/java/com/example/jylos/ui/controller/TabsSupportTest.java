package com.example.jylos.ui.controller;

import static com.example.jylos.tests.FxTestSupport.onFx;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
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
import com.example.jylos.service.NoteService;
import com.example.jylos.service.TagService;
import com.example.jylos.tests.FxTestSupport;
import com.example.jylos.ui.components.EditorTabs;
import com.example.jylos.ui.components.EditorTabsModel.OpenTarget;
import com.example.jylos.ui.components.ViewTabs;
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

        assertEquals(List.of("tabs [GRAPH, B.md] active GRAPH graph shown [confirm, changed]",
                "tabs [A.md, GRAPH, B.md] active GRAPH graph shown"), seen);
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
                "tabs [A.md, GRAPH] active GRAPH graph shown", "[confirm, changed]"), seen);
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
                "tabs [GRAPH] active GRAPH graph shown [confirm, changed]"), seen);
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
                    ViewTabs.newEmptyTab(), "B.md"), ViewTabs.GRAPH);
            String graphInFront = f.state() + " missing " + missing;
            Fixture g = new Fixture();
            g.views.restoreTabs(List.of(ViewTabs.GRAPH, "A.md"), "Borrada.md"); // its active note is gone
            return List.of(graphInFront, g.state());
        });

        assertEquals(List.of("tabs [A.md, GRAPH, B.md] active GRAPH graph shown missing 1",
                "tabs [GRAPH, A.md] active A.md graph hidden"), seen);
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
                    () -> calls.add("changed"));
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
            views.wire(null, graphView, graph, notes, () -> false, key -> key, status -> { }, () -> tabs, host.host(),
                    note -> { }, note -> { });
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
