package com.example.jylos.ui.graph;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static com.example.jylos.tests.FxTestSupport.onFx;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import com.example.jylos.graph.GraphData;
import com.example.jylos.graph.GraphEdge;
import com.example.jylos.graph.GraphNode;
import com.example.jylos.tests.FxTestSupport;

import javafx.scene.Scene;
import javafx.scene.SnapshotParameters;
import javafx.scene.image.PixelReader;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.stage.Stage;

/**
 * The graph is drawn as Obsidian draws it: a link is one pixel wide on screen at any zoom (times
 * the line thickness), and a note that does not exist yet is a faint filled dot the size of a
 * note, not a ring.
 */
class GraphCanvasDrawingTest {

    private static final int W = 600;
    private static final int H = 400;

    @Test
    void aLinkIsAsVisibleZoomedOutAsZoomedIn() throws Exception {
        Assumptions.assumeTrue(FxTestSupport.isFxRuntimeAvailable());
        double[] ink = onFx(() -> {
            GraphCanvas gc = canvas(List.of(note("a", 1), note("b", 1)),
                    List.of(new GraphEdge("a", "b", GraphEdge.Type.LINK)));
            place(gc, 0, -3000, 0);
            place(gc, 1, 3000, 0);
            return new double[] {inkAcrossTheLink(gc, 0.1), inkAcrossTheLink(gc, 1.0)};
        });
        double ratio = ink[0] / ink[1];
        assertTrue(ink[1] > 0, "the link is drawn");
        assertTrue(ratio > 0.9 && ratio < 1.1, "zoomed out / zoomed in, ink across the link: " + ratio);
    }

    @Test
    void aNoteThatDoesNotExistIsAFaintFilledDot() throws Exception {
        Assumptions.assumeTrue(FxTestSupport.isFxRuntimeAvailable());
        double[] centre = onFx(() -> {
            GraphCanvas gc = canvas(List.of(note("a", 0),
                    new GraphNode("ghost", "Ghost", GraphNode.Type.GHOST, "", 0)), List.of());
            place(gc, 0, -150, 0);
            place(gc, 1, 150, 0);
            setDouble(gc, "scale", 1.0);
            invoke(gc, "draw");
            WritableImage image = gc.snapshot(new SnapshotParameters(), null);
            PixelReader reader = image.getPixelReader();
            Color background = reader.getColor(1, 1);
            return new double[] {
                    distance(reader.getColor(W / 2 - 150, H / 2), background),
                    distance(reader.getColor(W / 2 + 150, H / 2), background),
                    distance(ghostColour(gc), background)};
        });
        assertTrue(centre[1] > 0.02, "the middle of the dot is filled: " + centre[1]);
        assertTrue(centre[1] < centre[0] * 0.8, "fainter than a note: " + centre[1] + " vs " + centre[0]);
        // At half opacity: half as far from the background as its colour itself.
        double share = centre[1] / centre[2];
        assertTrue(share > 0.4 && share < 0.6, "share of its colour over the background: " + share);
    }

    /** The colour the theme gives a note that does not exist yet. */
    private static Color ghostColour(GraphCanvas gc) throws ReflectiveOperationException {
        Field f = GraphCanvas.class.getDeclaredField("palette");
        f.setAccessible(true);
        Object palette = f.get(gc);
        Method ghost = palette.getClass().getDeclaredMethod("ghost");
        ghost.setAccessible(true);
        return (Color) ghost.invoke(palette);
    }

    private static GraphNode note(String id, int degree) {
        return new GraphNode(id, id, GraphNode.Type.NOTE, "", degree);
    }

    private static GraphCanvas canvas(List<GraphNode> nodes, List<GraphEdge> edges) {
        GraphCanvas gc = new GraphCanvas();
        gc.setPrefSize(W, H);
        Stage stage = new Stage();
        stage.setScene(new Scene(new StackPane(gc), W, H));
        stage.show();
        gc.setData(new GraphData(nodes, edges));
        gc.pause();
        setDouble(gc, "offsetX", W / 2.0);
        setDouble(gc, "offsetY", H / 2.0);
        return gc;
    }

    /** How much the column through the middle of the view differs from the background. */
    private static double inkAcrossTheLink(GraphCanvas gc, double scale) {
        setDouble(gc, "scale", scale);
        invoke(gc, "draw");
        WritableImage image = gc.snapshot(new SnapshotParameters(), null);
        PixelReader reader = image.getPixelReader();
        Color background = reader.getColor(1, 1);
        double ink = 0;
        for (int y = 0; y < H; y++) {
            ink += distance(reader.getColor(W / 2, y), background);
        }
        return ink;
    }

    private static double distance(Color a, Color b) {
        return Math.abs(a.getRed() - b.getRed()) + Math.abs(a.getGreen() - b.getGreen())
                + Math.abs(a.getBlue() - b.getBlue());
    }

    private static void place(GraphCanvas gc, int index, double x, double y) {
        array(gc, "x")[index] = x;
        array(gc, "y")[index] = y;
    }

    private static double[] array(GraphCanvas gc, String name) {
        try {
            Field f = GraphCanvas.class.getDeclaredField(name);
            f.setAccessible(true);
            return (double[]) f.get(gc);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void setDouble(GraphCanvas gc, String name, double value) {
        try {
            Field f = GraphCanvas.class.getDeclaredField(name);
            f.setAccessible(true);
            f.setDouble(gc, value);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void invoke(GraphCanvas gc, String name) {
        try {
            Method m = GraphCanvas.class.getDeclaredMethod(name);
            m.setAccessible(true);
            m.invoke(gc);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }
}
