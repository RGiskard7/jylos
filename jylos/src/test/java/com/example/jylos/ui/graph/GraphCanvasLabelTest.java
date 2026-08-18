package com.example.jylos.ui.graph;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import com.example.jylos.graph.GraphData;
import com.example.jylos.graph.GraphEdge;
import com.example.jylos.graph.GraphNode;
import com.example.jylos.tests.FxTestSupport;

import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.SnapshotParameters;
import javafx.scene.image.PixelReader;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.stage.Stage;

/**
 * Covers two real gaps in how the graph reveals node labels. Renders to a real
 * {@link javafx.scene.canvas.Canvas} and inspects actual pixels — the label pass is
 * hand-drawn text with no JavaFX node per label to query, so there is no state to
 * assert on other than what actually got painted.
 *
 * <ol>
 * <li>Hovering used to unconditionally show only the hovered node's own label —
 * both when zoomed in far enough that every node's label is normally showing
 * anyway (the connection highlight then had no name to go with for any neighbour),
 * and when zoomed out below the label threshold (exactly when hovering to see what
 * a note connects to is most useful, and exactly when it used to reveal the
 * least). Hovering now always shows the hovered node and its direct neighbours,
 * regardless of zoom.</li>
 * <li>Label visibility used to be one flat zoom number for every node regardless
 * of size. A node's label now reveals once that node's own on-screen circle
 * crosses a legible size, so a big hub's name shows at a lower zoom than a small
 * note's.</li>
 * </ol>
 */
class GraphCanvasLabelTest {

    @Test
    void hoveringAboveLabelThresholdStillShowsNeighbourLabels() throws Exception {
        Assumptions.assumeTrue(FxTestSupport.isFxRuntimeAvailable());

        CountDownLatch done = new CountDownLatch(1);
        boolean[] centerVisible = new boolean[1];
        boolean[] neighbourVisible = new boolean[1];
        Platform.runLater(() -> {
            try {
                GraphCanvas gc = buildThreeNodeGraph();

                setScale(gc, 2.0); // well above the default labelThreshold (0.4)
                setHoverIndex(gc, 0); // hover the center node
                invokeDraw(gc);

                WritableImage image = gc.snapshot(new SnapshotParameters(), null);
                centerVisible[0] = hasNonBackgroundPixelsNear(gc, image, 0);
                neighbourVisible[0] = hasNonBackgroundPixelsNear(gc, image, 1);
            } finally {
                done.countDown();
            }
        });

        assertTrue(done.await(30, TimeUnit.SECONDS), "test never completed on the FX thread");
        assertTrue(centerVisible[0], "hovered node's own label should still be visible");
        assertTrue(neighbourVisible[0],
                "neighbour's label must also be visible once zoomed past the label threshold "
                        + "— hovering must not hide labels that would otherwise be showing");
    }

    @Test
    void hoveringBelowLabelThresholdStillRevealsTheHoveredNodesNeighbour() throws Exception {
        Assumptions.assumeTrue(FxTestSupport.isFxRuntimeAvailable());

        CountDownLatch done = new CountDownLatch(1);
        boolean[] centerVisible = new boolean[1];
        boolean[] neighbourVisible = new boolean[1];
        Platform.runLater(() -> {
            try {
                GraphCanvas gc = buildThreeNodeGraph();
                // Pushed far apart in world space so their two labels land nowhere
                // near each other on screen even at this tiny scale — otherwise the
                // seeded-at-random positions could land close enough that the
                // overlap-avoidance in the label pass (correctly) drops one of them,
                // which isn't what this test is about.
                setDoubleArrayElement(gc, "x", 0, 0.0);
                setDoubleArrayElement(gc, "y", 0, 0.0);
                setDoubleArrayElement(gc, "x", 1, 2000.0);
                setDoubleArrayElement(gc, "y", 1, 0.0);

                setScale(gc, 0.1); // well below the default labelThreshold (0.4)
                setHoverIndex(gc, 0);
                invokeDraw(gc);

                WritableImage image = gc.snapshot(new SnapshotParameters(), null);
                centerVisible[0] = hasNonBackgroundPixelsNear(gc, image, 0);
                neighbourVisible[0] = hasNonBackgroundPixelsNear(gc, image, 1);
            } finally {
                done.countDown();
            }
        });

        assertTrue(done.await(30, TimeUnit.SECONDS), "test never completed on the FX thread");
        assertTrue(centerVisible[0], "hovered node's own label should still be visible");
        assertTrue(neighbourVisible[0],
                "hovering must reveal the neighbour's name even zoomed out below the label "
                        + "threshold — that's exactly when knowing what a note connects to is most "
                        + "useful, and exactly when the normal zoom-based reveal shows nothing at all");
    }

    @Test
    void hoveringDimsLabelsOfNodesOutsideTheRelationTheSameAsTheirNodeCircles() throws Exception {
        Assumptions.assumeTrue(FxTestSupport.isFxRuntimeAvailable());

        CountDownLatch done = new CountDownLatch(1);
        double[] neighbourStrength = new double[1];
        double[] unrelatedStrength = new double[1];
        Platform.runLater(() -> {
            try {
                // center -- a (connected); b is present but NOT connected to center.
                GraphCanvas gc = buildGraph(
                        new GraphNode("center", "Center Note", GraphNode.Type.NOTE, null, 1),
                        new GraphNode("a", "Neighbour Note", GraphNode.Type.NOTE, null, 1),
                        new GraphNode("b", "Unrelated Note", GraphNode.Type.NOTE, null, 1));

                setScale(gc, 2.0); // well above the default labelThreshold (0.4)
                setHoverIndex(gc, 0); // hover the center node; "a" is its neighbour, "b" is not
                invokeDraw(gc);

                WritableImage image = gc.snapshot(new SnapshotParameters(), null);
                neighbourStrength[0] = maxColorDeviationNear(gc, image, 1);
                unrelatedStrength[0] = maxColorDeviationNear(gc, image, 2);
            } finally {
                done.countDown();
            }
        });

        assertTrue(done.await(30, TimeUnit.SECONDS), "test never completed on the FX thread");
        assertTrue(neighbourStrength[0] > 0, "neighbour's label should be visible at full strength");
        assertTrue(unrelatedStrength[0] > 0, "unrelated node's label should still be visible, just dimmer");
        assertTrue(unrelatedStrength[0] < neighbourStrength[0] * 0.6,
                "the unrelated node's label must be noticeably dimmer than the neighbour's — "
                        + "same treatment its node circle already gets while hovering — "
                        + "neighbour strength=" + neighbourStrength[0] + " unrelated strength=" + unrelatedStrength[0]);
    }

    @Test
    void aBiggerNodesLabelRevealsAtALowerZoomThanASmallOnes() throws Exception {
        Assumptions.assumeTrue(FxTestSupport.isFxRuntimeAvailable());

        CountDownLatch done = new CountDownLatch(1);
        boolean[] hubVisible = new boolean[1];
        boolean[] leafVisible = new boolean[1];
        Platform.runLater(() -> {
            try {
                // Unconnected on purpose — this is about label reveal by node size
                // alone, nothing to do with hover/relations.
                GraphNode hub = new GraphNode("hub", "Hub Note", GraphNode.Type.NOTE, null, 400);
                GraphNode leaf = new GraphNode("leaf", "Leaf Note", GraphNode.Type.NOTE, null, 0);
                GraphCanvas gc = new GraphCanvas();
                gc.setPrefSize(600, 400);
                Stage stage = new Stage();
                stage.setScene(new Scene(new StackPane(gc), 600, 400));
                stage.show();
                gc.setData(new GraphData(List.of(hub, leaf), List.of()));
                gc.pause();

                // A scale between the two nodes' own reveal thresholds: past the
                // hub's (big radius → low threshold), short of the leaf's (small
                // radius → the default labelThreshold itself, 0.4).
                setScale(gc, 0.2);
                invokeDraw(gc);

                WritableImage image = gc.snapshot(new SnapshotParameters(), null);
                hubVisible[0] = hasNonBackgroundPixelsNear(gc, image, 0);
                leafVisible[0] = hasNonBackgroundPixelsNear(gc, image, 1);
            } finally {
                done.countDown();
            }
        });

        assertTrue(done.await(30, TimeUnit.SECONDS), "test never completed on the FX thread");
        assertTrue(hubVisible[0],
                "a high-degree node's label must already be visible at a zoom its own "
                        + "on-screen size clears, even though the reference (small-node) threshold hasn't");
        assertTrue(!leafVisible[0],
                "a degree-0 node's label must not appear before the zoom its own (small) "
                        + "on-screen size actually clears");
    }

    private static GraphCanvas buildThreeNodeGraph() {
        return buildGraph(
                new GraphNode("center", "Center Note", GraphNode.Type.NOTE, null, 1),
                new GraphNode("a", "Neighbour Note", GraphNode.Type.NOTE, null, 1));
    }

    /** Builds a star graph: the first node connected to every later node, none of the later nodes to each other. */
    private static GraphCanvas buildGraph(GraphNode center, GraphNode... rest) {
        GraphCanvas gc = new GraphCanvas();
        gc.setPrefSize(600, 400);
        Stage stage = new Stage();
        stage.setScene(new Scene(new StackPane(gc), 600, 400));
        stage.show();

        List<GraphNode> nodes = new java.util.ArrayList<>();
        nodes.add(center);
        nodes.addAll(List.of(rest));
        // Only the first "rest" node is actually linked to center, so the test graph
        // above can exercise an unrelated third node — see buildGraph's single call site.
        List<GraphEdge> edges = rest.length > 0
                ? List.of(new GraphEdge(center.id(), rest[0].id(), GraphEdge.Type.LINK))
                : List.of();
        gc.setData(new GraphData(nodes, edges));
        gc.pause(); // stop the physics/animation timer so nothing redraws between our draw() and snapshot()
        return gc;
    }

    private static void setScale(GraphCanvas gc, double value) {
        setDouble(gc, "scale", value);
    }

    /** Sets the hover state as it looks once the real mouse-driven hover
     *  transition has fully settled — {@code hoverIndex} alone isn't enough since
     *  what actually gets drawn is {@code displayHoverIndex}/{@code
     *  hoverStrength}, which only the mouse handlers (or a running hover
     *  animation) update. */
    private static void setHoverIndex(GraphCanvas gc, int index) {
        try {
            setIntField(gc, "hoverIndex", index);
            setIntField(gc, "displayHoverIndex", index);
            Field strength = GraphCanvas.class.getDeclaredField("hoverStrength");
            strength.setAccessible(true);
            strength.setDouble(gc, index >= 0 ? 1.0 : 0.0);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void setIntField(GraphCanvas gc, String field, int value) throws ReflectiveOperationException {
        Field f = GraphCanvas.class.getDeclaredField(field);
        f.setAccessible(true);
        f.setInt(gc, value);
    }

    private static void setDouble(GraphCanvas gc, String field, double value) {
        try {
            Field f = GraphCanvas.class.getDeclaredField(field);
            f.setAccessible(true);
            f.setDouble(gc, value);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private static double getDouble(GraphCanvas gc, String field) {
        try {
            Field f = GraphCanvas.class.getDeclaredField(field);
            f.setAccessible(true);
            return f.getDouble(gc);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void setDoubleArrayElement(GraphCanvas gc, String field, int index, double value) {
        getDoubleArray(gc, field)[index] = value;
    }

    private static double[] getDoubleArray(GraphCanvas gc, String field) {
        try {
            Field f = GraphCanvas.class.getDeclaredField(field);
            f.setAccessible(true);
            return (double[]) f.get(gc);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void invokeDraw(GraphCanvas gc) {
        try {
            Method m = GraphCanvas.class.getDeclaredMethod("draw");
            m.setAccessible(true);
            m.invoke(gc);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * Scans a box below-and-around where the label for node {@code index} is drawn
     * (same {@code sx(x)}, {@code sy(y) + r*scale + 12} anchor {@code draw()} itself
     * uses) for any pixel that isn't the plain background colour.
     *
     * <p>The box half-extent is capped well under half the on-screen distance to the
     * <em>other</em> node's own label anchor, so at a tiny scale — where a 2-node
     * seeded layout can place both nodes just a handful of screen pixels apart — a
     * generously wide fixed box can't accidentally reach across and pick up the
     * other node's label, which would falsely read as "this node's label is
     * showing." A real fixed-size box is fine at any scale a user would actually be
     * zoomed to; it only needs shrinking here because the test deliberately exercises
     * an extreme low-zoom corner the label logic itself doesn't care about.</p>
     */
    private static boolean hasNonBackgroundPixelsNear(GraphCanvas gc, WritableImage image, int index) {
        return maxColorDeviationNear(gc, image, index) > 0;
    }

    /**
     * Scans a box below-and-around where the label for node {@code index} is drawn
     * (same {@code sx(x)}, {@code sy(y) + r*scale + 12} anchor {@code draw()} itself
     * uses) and returns the strongest squared colour distance from the plain
     * background found there — {@code 0} if the label isn't showing at all, larger
     * for a label drawn at higher opacity.
     *
     * <p>The box half-extent is capped well under half the on-screen distance to the
     * <em>closest other</em> node's own label anchor, so at a tiny scale — where a
     * handful of seeded nodes can land just a few screen pixels apart — a generously
     * wide fixed box can't accidentally reach across and pick up a different node's
     * label. A real fixed-size box is fine at any scale a user would actually be
     * zoomed to; it only needs shrinking here because the tests deliberately exercise
     * corners (tiny scale, several nodes close together) the label logic itself
     * doesn't care about.</p>
     */
    private static double maxColorDeviationNear(GraphCanvas gc, WritableImage image, int index) {
        double[] x = getDoubleArray(gc, "x");
        double[] y = getDoubleArray(gc, "y");
        double[] radius = getDoubleArray(gc, "radius");
        double scale = getDouble(gc, "scale");
        double offsetX = getDouble(gc, "offsetX");
        double offsetY = getDouble(gc, "offsetY");

        double labelCenterX = x[index] * scale + offsetX;
        double labelCenterY = y[index] * scale + offsetY + radius[index] * scale + 12;
        double closestOther = Double.MAX_VALUE;
        for (int other = 0; other < x.length; other++) {
            if (other == index) {
                continue;
            }
            double otherX = x[other] * scale + offsetX;
            double otherY = y[other] * scale + offsetY + radius[other] * scale + 12;
            closestOther = Math.min(closestOther, Math.hypot(labelCenterX - otherX, labelCenterY - otherY));
        }
        double halfExtent = Math.max(2, Math.min(60, closestOther / 2 - 2));

        PixelReader reader = image.getPixelReader();
        int w = (int) image.getWidth();
        int h = (int) image.getHeight();
        int minX = Math.max(0, (int) (labelCenterX - halfExtent));
        int maxX = Math.min(w - 1, (int) (labelCenterX + halfExtent));
        int minY = Math.max(0, (int) (labelCenterY - Math.min(10, halfExtent)));
        int maxY = Math.min(h - 1, (int) (labelCenterY + Math.min(10, halfExtent)));

        Color background = reader.getColor(1, 1); // top-left corner: always background, never a node/label
        double maxDeviation = 0;
        for (int py = minY; py <= maxY; py++) {
            for (int px = minX; px <= maxX; px++) {
                maxDeviation = Math.max(maxDeviation, colorDistanceSquared(reader.getColor(px, py), background));
            }
        }
        return maxDeviation;
    }

    private static double colorDistanceSquared(Color a, Color b) {
        double dr = a.getRed() - b.getRed();
        double dg = a.getGreen() - b.getGreen();
        double db = a.getBlue() - b.getBlue();
        return dr * dr + dg * dg + db * db;
    }
}
