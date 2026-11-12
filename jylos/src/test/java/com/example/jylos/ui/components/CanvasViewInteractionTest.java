package com.example.jylos.ui.components;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.prefs.Preferences;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.example.jylos.tests.FxTestSupport;
import com.example.jylos.util.CanvasModel;
import com.example.jylos.util.CanvasModel.CanvasNode;

import javafx.application.Platform;
import javafx.event.Event;
import javafx.geometry.Bounds;
import javafx.geometry.Point2D;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.CheckMenuItem;
import javafx.scene.control.MenuItem;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.shape.CubicCurve;

/**
 * Using the canvas as in Obsidian: edges are curves, a box dragged on the background selects
 * the cards it touches, the selection moves, is deleted, copied and pasted together, cards
 * snap to the grid and to other cards (with a saved switch), the wheel pans and Cmd/Ctrl +
 * wheel zooms.
 */
class CanvasViewInteractionTest {

    private static final boolean MAC = System.getProperty("os.name", "").toLowerCase().contains("mac");

    /** a and b side by side with an edge a → b; c under a. */
    private static final String DOC = """
            {"nodes":[
              {"id":"a","type":"text","x":0,"y":0,"width":200,"height":100,"text":"A"},
              {"id":"b","type":"text","x":400,"y":0,"width":200,"height":100,"text":"B"},
              {"id":"c","type":"text","x":0,"y":400,"width":200,"height":100,"text":"C"}
            ],"edges":[
              {"id":"ab","fromNode":"a","fromSide":"right","toNode":"b","toSide":"left"}
            ]}""";

    private Preferences prefs;

    @BeforeEach
    void freshPreferences() {
        prefs = Preferences.userRoot().node("/com/example/jylos/test/" + UUID.randomUUID());
    }

    @AfterEach
    void dropPreferences() throws Exception {
        // Back to the user's before removing the node: other canvases built later read them.
        CanvasView.usePreferences(null);
        prefs.removeNode();
    }

    @Test
    void anEdgeIsACurveLeavingAndEnteringTheCardsAtTheirSides() throws Exception {
        assumeFx();
        List<Double> curve = onFx(() -> {
            Fixture f = new Fixture(DOC);
            Node edge = f.view.lookup(".canvas-edge");
            assertTrue(edge instanceof CubicCurve, "edge drawn as " + edge);
            CubicCurve c = (CubicCurve) edge;
            return List.of(c.getStartX(), c.getStartY(), c.getControlX1(), c.getControlX2(), c.getEndX(), c.getEndY());
        });
        // From a's right side (200,50) to b's left side (400,50), bending 100 out of each.
        assertEquals(List.of(200.0, 50.0, 300.0, 300.0, 400.0, 50.0), curve);
    }

    @Test
    void aBoxDraggedOnTheBackgroundSelectsTheCardsItTouches() throws Exception {
        assumeFx();
        List<String> selected = onFx(() -> {
            Fixture f = new Fixture(DOC);
            Point2D from = f.sceneOf("a", -0.1, -0.1);
            Point2D to = f.sceneOf("b", 0.5, 0.5);
            f.dragOnBackground(from, to, MouseButton.PRIMARY);
            return f.selected();
        });
        assertEquals(List.of("a", "b"), selected);
    }

    @Test
    void clickingSelectsOneCardAndShiftClickingAddsAnother() throws Exception {
        assumeFx();
        List<List<String>> steps = onFx(() -> {
            Fixture f = new Fixture(DOC);
            f.click("a", false);
            List<String> one = f.selected();
            f.click("c", true);
            List<String> two = f.selected();
            f.click("b", false);
            return List.of(one, two, f.selected());
        });
        assertEquals(List.of(List.of("a"), List.of("a", "c"), List.of("b")), steps);
    }

    @Test
    void draggingASelectedCardMovesTheWholeSelection() throws Exception {
        assumeFx();
        prefs.putBoolean("canvas.snapToGrid", false);
        prefs.putBoolean("canvas.snapToObjects", false);
        List<Double> moved = onFx(() -> {
            Fixture f = new Fixture(DOC);
            f.click("a", false);
            f.click("b", true);
            double scale = f.scale();
            f.dragCard("a", 100 * scale, 50 * scale);
            assertEquals(List.of("a", "b"), f.selected(), "still both selected after the drag");
            return List.of(f.node("a").x(), f.node("a").y(), f.node("b").x(), f.node("b").y(),
                    f.node("c").x(), f.node("c").y());
        });
        assertEquals(List.of(100.0, 50.0, 500.0, 50.0, 0.0, 400.0), moved);
    }

    @Test
    void aMovedCardSnapsToTheCardsAroundItUnlessSnappingIsSwitchedOff() throws Exception {
        assumeFx();
        // 7 right and 5 down of where it was: c's left edge and b's top edge pull it back.
        assertEquals(List.of(0.0, 0.0), nudgeA(7, 5));
        onFx(() -> {
            Fixture f = new Fixture(DOC);
            f.setting("canvas.snap_to_objects").setSelected(false);
            f.setting("canvas.snap_to_grid").setSelected(false);
            return null;
        });
        assertFalse(prefs.getBoolean("canvas.snapToObjects", true), "the switch is saved");
        assertEquals(List.of(7.0, 5.0), nudgeA(7, 5), "a new canvas reads the saved switch");
    }

    private List<Double> nudgeA(double dx, double dy) throws Exception {
        return onFx(() -> {
            Fixture f = new Fixture(DOC);
            f.click("a", false);
            f.dragCard("a", dx * f.scale(), dy * f.scale());
            return List.of(f.node("a").x(), f.node("a").y());
        });
    }

    @Test
    void deleteRemovesEveryCardSelected() throws Exception {
        assumeFx();
        List<String> left = onFx(() -> {
            Fixture f = new Fixture(DOC);
            f.click("a", false);
            f.click("b", true);
            f.key(KeyCode.DELETE, false);
            return f.document.nodes().stream().map(CanvasNode::id).toList();
        });
        assertEquals(List.of("c"), left);
    }

    @Test
    void copyAndPasteDuplicatesTheSelectionWithItsEdgesAndSelectsTheCopy() throws Exception {
        assumeFx();
        List<Object> result = onFx(() -> {
            Fixture f = new Fixture(DOC);
            f.click("a", false);
            f.click("b", true);
            f.key(KeyCode.C, true);
            f.key(KeyCode.V, true);
            List<String> copies = f.selected();
            return List.of(f.document.nodes().size(), f.document.edges().size(), copies,
                    copies.stream().map(id -> f.node(id).x()).sorted().toList());
        });
        assertEquals(5, result.get(0));
        assertEquals(2, result.get(1), "the edge between them is copied too");
        @SuppressWarnings("unchecked")
        List<String> copies = (List<String>) result.get(2);
        assertEquals(2, copies.size());
        assertFalse(copies.contains("a") || copies.contains("b"), "the copies are selected: " + copies);
        assertEquals(List.of(30.0, 430.0), result.get(3));
    }

    @Test
    void theWheelPansAndCmdOrCtrlWheelZooms() throws Exception {
        assumeFx();
        List<Double> seen = onFx(() -> {
            Fixture f = new Fixture(DOC);
            Bounds before = f.boxInScene("a");
            f.scroll(0, -100, false);
            Bounds panned = f.boxInScene("a");
            f.scroll(0, 100, true);
            Bounds zoomed = f.boxInScene("a");
            return List.of(panned.getMinY() - before.getMinY(), panned.getWidth() - before.getWidth(),
                    zoomed.getWidth() - panned.getWidth());
        });
        assertTrue(seen.get(0) < -50, "scrolling down moves the canvas up: " + seen);
        assertEquals(0.0, seen.get(1), 1e-6, "without zooming");
        assertNotEquals(0.0, seen.get(2), "Cmd/Ctrl + wheel zooms");
    }

    @Test
    void theMiddleButtonPansWhileTheLeftButtonSelects() throws Exception {
        assumeFx();
        List<Object> seen = onFx(() -> {
            Fixture f = new Fixture(DOC);
            Bounds before = f.boxInScene("c");
            Point2D empty = f.sceneOf("b", 0.5, 4.0);
            f.dragOnBackground(empty, empty.add(0, -150), MouseButton.MIDDLE);
            double middle = f.boxInScene("c").getMinY() - before.getMinY();
            Bounds after = f.boxInScene("c");
            f.dragOnBackground(empty, empty.add(0, -150), MouseButton.PRIMARY);
            return List.of(middle, f.boxInScene("c").getMinY() - after.getMinY());
        });
        assertEquals(-150.0, (Double) seen.get(0), 1e-6, "middle drag pans");
        assertEquals(0.0, (Double) seen.get(1), 1e-6, "left drag draws a selection box instead");
    }

    // ── Fixture ────────────────────────────────────────────────────────────

    private final class Fixture {
        final CanvasModel.Document document;
        final CanvasView view;
        final Node viewport;

        Fixture(String json) {
            document = CanvasModel.Document.parse(json);
            CanvasView.usePreferences(prefs);
            view = new CanvasView(document, f -> { }, f -> null, f -> f, f -> f, List::of, List::of, s -> { },
                    () -> { }, k -> k);
            StackPane host = new StackPane(view);
            new Scene(host, 1000, 700);
            host.applyCss();
            host.layout();
            viewport = view.lookup(".canvas-viewport");
        }

        Region box(String id) {
            for (Node node : view.lookupAll("*")) {
                if (id.equals(node.getProperties().get(CanvasView.NODE_ID_KEY))) {
                    return (Region) node;
                }
            }
            throw new AssertionError("no card " + id);
        }

        CanvasNode node(String id) {
            return document.nodes().stream().filter(n -> n.id().equals(id)).findFirst()
                    .orElseThrow(() -> new AssertionError("no node " + id));
        }

        Bounds boxInScene(String id) {
            Region box = box(id);
            return box.localToScene(box.getLayoutBounds());
        }

        double scale() {
            return boxInScene("a").getWidth() / box("a").getWidth();
        }

        /** A point relative to a card, in its own size (0..1 inside it). */
        Point2D sceneOf(String id, double fx, double fy) {
            Bounds b = boxInScene(id);
            return new Point2D(b.getMinX() + b.getWidth() * fx, b.getMinY() + b.getHeight() * fy);
        }

        List<String> selected() {
            return document.nodes().stream().map(CanvasNode::id)
                    .filter(id -> box(id).getStyleClass().contains("canvas-selected")).toList();
        }

        void click(String id, boolean shift) {
            Point2D p = sceneOf(id, 0.5, 0.5);
            Region box = box(id);
            Event.fireEvent(box, mouse(MouseEvent.MOUSE_PRESSED, p, MouseButton.PRIMARY, shift));
            Event.fireEvent(box, mouse(MouseEvent.MOUSE_RELEASED, p, MouseButton.PRIMARY, shift));
            Event.fireEvent(box, mouse(MouseEvent.MOUSE_CLICKED, p, MouseButton.PRIMARY, shift));
        }

        void dragCard(String id, double dx, double dy) {
            Point2D p = sceneOf(id, 0.5, 0.5);
            Point2D q = p.add(dx, dy);
            Region box = box(id);
            Event.fireEvent(box, mouse(MouseEvent.MOUSE_PRESSED, p, MouseButton.PRIMARY, false));
            Event.fireEvent(box, mouse(MouseEvent.MOUSE_DRAGGED, p.midpoint(q), MouseButton.PRIMARY, false));
            Event.fireEvent(box, mouse(MouseEvent.MOUSE_DRAGGED, q, MouseButton.PRIMARY, false));
            Event.fireEvent(box, mouse(MouseEvent.MOUSE_RELEASED, q, MouseButton.PRIMARY, false));
            // JavaFX also sends a click when the button is released over the card it was pressed on.
            Event.fireEvent(box, mouse(MouseEvent.MOUSE_CLICKED, q, MouseButton.PRIMARY, false));
        }

        void dragOnBackground(Point2D from, Point2D to, MouseButton button) {
            Event.fireEvent(viewport, mouse(MouseEvent.MOUSE_PRESSED, from, button, false));
            Event.fireEvent(viewport, mouse(MouseEvent.MOUSE_DRAGGED, from.midpoint(to), button, false));
            Event.fireEvent(viewport, mouse(MouseEvent.MOUSE_DRAGGED, to, button, false));
            Event.fireEvent(viewport, mouse(MouseEvent.MOUSE_RELEASED, to, button, false));
        }

        void key(KeyCode code, boolean shortcut) {
            Event.fireEvent(view, new KeyEvent(KeyEvent.KEY_PRESSED, "", "", code, false,
                    shortcut && !MAC, false, shortcut && MAC));
        }

        void scroll(double dx, double dy, boolean shortcut) {
            Point2D p = viewport.localToScene(500, 350);
            Event.fireEvent(viewport, new ScrollEvent(ScrollEvent.SCROLL, p.getX(), p.getY(), p.getX(), p.getY(),
                    false, shortcut && !MAC, false, shortcut && MAC, false, false, dx, dy, dx, dy,
                    ScrollEvent.HorizontalTextScrollUnits.NONE, 0, ScrollEvent.VerticalTextScrollUnits.NONE, 0, 0,
                    null));
        }

        CheckMenuItem setting(String key) {
            javafx.scene.control.Button settings = (javafx.scene.control.Button) view.lookup(".canvas-settings");
            for (MenuItem item : settings.getContextMenu().getItems()) {
                if (key.equals(item.getUserData()) && item instanceof CheckMenuItem check) {
                    return check;
                }
            }
            throw new AssertionError("no setting " + key);
        }

        private MouseEvent mouse(javafx.event.EventType<MouseEvent> type, Point2D p, MouseButton button,
                boolean shift) {
            boolean down = type != MouseEvent.MOUSE_RELEASED && type != MouseEvent.MOUSE_CLICKED;
            return new MouseEvent(type, p.getX(), p.getY(), p.getX(), p.getY(), button, 1, shift, false, false,
                    false, down && button == MouseButton.PRIMARY, down && button == MouseButton.MIDDLE,
                    down && button == MouseButton.SECONDARY, false, false, false, null);
        }
    }

    private static void assumeFx() {
        Assumptions.assumeTrue(FxTestSupport.isFxRuntimeAvailable());
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
            if (failure.get() instanceof AssertionError error) {
                throw error;
            }
            throw new AssertionError(failure.get());
        }
        return result.get();
    }
}
