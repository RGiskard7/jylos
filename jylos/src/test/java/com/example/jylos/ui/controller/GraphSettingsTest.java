package com.example.jylos.ui.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import java.util.prefs.Preferences;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.example.jylos.data.dao.filesystem.FolderDAOFileSystem;
import com.example.jylos.data.dao.filesystem.NoteDAOFileSystem;
import com.example.jylos.data.dao.filesystem.TagDAOFileSystem;
import com.example.jylos.graph.GraphData;
import com.example.jylos.graph.GraphEdge;
import com.example.jylos.graph.GraphNode;
import com.example.jylos.service.NoteService;
import com.example.jylos.service.TagService;
import com.example.jylos.tests.FxTestSupport;
import com.example.jylos.ui.graph.GraphCanvas;

import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ColorPicker;
import javafx.scene.control.Slider;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;

/**
 * The graph's settings as in Obsidian: the local graph follows the depth and the link
 * switches, colour groups paint the nodes they match, and every setting is kept for next time.
 *
 * <pre>A → B, C → A, B → C, D alone</pre>
 */
class GraphSettingsTest {

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
        Files.writeString(vault.resolve("B.md"), "[[C]]", StandardCharsets.UTF_8);
        Files.writeString(vault.resolve("C.md"), "[[A]]", StandardCharsets.UTF_8);
        Files.writeString(vault.resolve("D.md"), "sola", StandardCharsets.UTF_8);
        NoteDAOFileSystem dao = new NoteDAOFileSystem(vault.toString());
        notes = new NoteService(dao, new FolderDAOFileSystem(vault.toString()));
        tags = new TagService(new TagDAOFileSystem(dao), dao);
    }

    @AfterEach
    void tearDown() throws Exception {
        GraphController.usePreferences(null);
        prefs.removeNode();
    }

    @Test
    void theLocalGraphFollowsItsSwitches() throws Exception {
        Assumptions.assumeTrue(FxTestSupport.isFxRuntimeAvailable());
        Fixture f = onFx(Fixture::new);
        onFx(() -> {
            f.<ToggleButton>field("localGraphBtn").setSelected(true);
            f.controller.show(false);
            return null;
        });
        f.waitForBuild();
        String withoutNeighbours = f.last();
        onFx(() -> {
            f.<CheckBox>field("localNeighborCheck").fire();
            return null;
        });
        f.waitForBuild();
        String withNeighbours = f.last();
        onFx(() -> {
            f.<CheckBox>field("localIncomingCheck").fire();
            return null;
        });
        f.waitForBuild();

        assertEquals("[A, B, C] [A->B, C->A]", withoutNeighbours, "depth 1, both ways, no links between neighbours");
        assertEquals("[A, B, C] [A->B, B->C, C->A]", withNeighbours);
        assertEquals("[A, B] [A->B]", f.last(), "outgoing links only");
    }

    @Test
    void theSettingsAndColourGroupsAreKeptForNextTime() throws Exception {
        Assumptions.assumeTrue(FxTestSupport.isFxRuntimeAvailable());
        Fixture f = onFx(Fixture::new);
        onFx(() -> {
            f.<Slider>field("localDepthSlider").setValue(3);
            f.<CheckBox>field("arrowsCheck").fire();
            f.<Slider>field("repulsionSlider").setValue(321);
            f.<Button>field("addColorGroupBtn").fire();
            Pane row = (Pane) f.<Pane>field("colorGroupsBox").getChildren().get(0);
            ((TextField) row.lookup(".graph-color-group-query")).setText("file:b");
            ((ColorPicker) row.lookup(".graph-color-group-color")).setValue(Color.web("#ff0000"));
            return null;
        });
        Thread.sleep(600);

        Fixture next = onFx(Fixture::new);
        List<Object> restored = onFx(() -> {
            Pane row = (Pane) next.<Pane>field("colorGroupsBox").getChildren().get(0);
            return List.of(next.<Slider>field("localDepthSlider").getValue(),
                    next.<CheckBox>field("arrowsCheck").isSelected(),
                    next.<Slider>field("repulsionSlider").getValue(),
                    next.<Pane>field("colorGroupsBox").getChildren().size(),
                    ((TextField) row.lookup(".graph-color-group-query")).getText(),
                    ((ColorPicker) row.lookup(".graph-color-group-color")).getValue());
        });
        assertEquals(List.of(3.0, true, 321.0, 1, "file:b", Color.web("#ff0000")), restored);
    }

    @Test
    void aColourGroupPaintsTheNodesItMatches() throws Exception {
        Assumptions.assumeTrue(FxTestSupport.isFxRuntimeAvailable());
        Fixture f = onFx(Fixture::new);
        onFx(() -> {
            f.controller.show(false);
            return null;
        });
        f.waitForBuild();
        onFx(() -> {
            f.<Button>field("addColorGroupBtn").fire();
            Pane row = (Pane) f.<Pane>field("colorGroupsBox").getChildren().get(0);
            ((TextField) row.lookup(".graph-color-group-query")).setText("file:b");
            ((ColorPicker) row.lookup(".graph-color-group-color")).setValue(Color.web("#ff0000"));
            return null;
        });
        GraphCanvas canvas = f.canvas();
        waitUntil(() -> Color.web("#ff0000").equals(canvas.fillOf("B.md")));
        assertTrue(onFx(() -> !Color.web("#ff0000").equals(canvas.fillOf("A.md"))), "only the match");
    }

    private final class Fixture {
        final GraphController controller;
        final List<GraphData> built = new CopyOnWriteArrayList<>();
        private int seen;

        Fixture() throws Exception {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/example/jylos/ui/view/GraphView.fxml"),
                    ResourceBundle.getBundle("com.example.jylos.i18n.messages", Locale.forLanguageTag("es")));
            loader.load();
            controller = loader.getController();
            controller.wire(null, notes, tags, loader.getResources(), id -> { }, () -> { }, () -> "A.md", built::add);
        }

        @SuppressWarnings("unchecked")
        <T> T field(String name) throws Exception {
            Field field = GraphController.class.getDeclaredField(name);
            field.setAccessible(true);
            return (T) field.get(controller);
        }

        GraphCanvas canvas() throws Exception {
            return field("canvas");
        }

        void waitForBuild() throws Exception {
            int before = seen;
            waitUntil(() -> built.size() > before);
            seen = built.size();
        }

        String last() {
            GraphData data = built.get(built.size() - 1);
            Set<String> nodes = new TreeSet<>();
            for (GraphNode node : data.nodes()) {
                nodes.add(node.label());
            }
            Set<String> edges = new TreeSet<>();
            for (GraphEdge edge : data.edges()) {
                edges.add(edge.source().replace(".md", "") + "->" + edge.target().replace(".md", ""));
            }
            return nodes + " " + edges;
        }
    }

    private static void waitUntil(BooleanSupplier condition) throws Exception {
        long deadline = System.currentTimeMillis() + 10_000;
        while (System.currentTimeMillis() < deadline) {
            if (onFx(condition::getAsBoolean)) {
                return;
            }
            Thread.sleep(50);
        }
        throw new AssertionError("condition not reached in time");
    }

    private static <T> T onFx(Callable<T> work) throws Exception {
        AtomicReference<T> result = new AtomicReference<>();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        CountDownLatch done = new CountDownLatch(1);
        Platform.runLater(() -> {
            try {
                result.set(work.call());
            } catch (Throwable t) {
                failure.set(t);
            } finally {
                done.countDown();
            }
        });
        assertTrue(done.await(30, TimeUnit.SECONDS), "never completed on the FX thread");
        if (failure.get() != null) {
            throw new AssertionError(failure.get());
        }
        return result.get();
    }
}
