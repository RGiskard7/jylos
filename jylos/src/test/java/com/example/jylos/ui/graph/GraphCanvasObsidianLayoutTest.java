package com.example.jylos.ui.graph;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import com.example.jylos.graph.GraphData;
import com.example.jylos.graph.GraphEdge;
import com.example.jylos.graph.GraphNode;
import com.example.jylos.tests.FxTestSupport;

import javafx.application.Platform;

/**
 * The graph is laid out as Obsidian lays it out: the same node sizes, and its forces, which mix
 * the connected notes with the loose ones over the whole disc instead of packing the connected
 * ones in the middle with a ring of loose notes around them.
 */
class GraphCanvasObsidianLayoutTest {

    @Test
    void aNodeIsAsBigAsInObsidian() {
        assertEquals(8.0, GraphCanvas.nodeSize(0), 1e-9, "the smallest");
        assertEquals(8.0, GraphCanvas.nodeSize(5), 1e-9, "3·√6 is still under 8");
        assertEquals(12.0, GraphCanvas.nodeSize(15), 1e-9, "3·√16");
        assertEquals(30.0, GraphCanvas.nodeSize(500), 1e-9, "the largest");
    }

    @Test
    void theDefaultForcesAreObsidians() throws Exception {
        Assumptions.assumeTrue(FxTestSupport.isFxRuntimeAvailable());
        List<Double> forces = onFx(() -> {
            GraphCanvas canvas = new GraphCanvas();
            return List.of(canvas.getRepulsion(), canvas.getLinkDistance(), canvas.getCenterGravity(),
                    canvas.getLinkForce());
        });
        assertEquals(List.of(1000.0, 250.0, 0.1, 1.0), forces);
    }

    @Test
    void connectedNotesAreSpreadOverTheDiscNotPackedInTheMiddle() throws Exception {
        Assumptions.assumeTrue(FxTestSupport.isFxRuntimeAvailable());
        List<GraphNode> nodes = new ArrayList<>();
        List<GraphEdge> edges = new ArrayList<>();
        for (int i = 0; i < 1500; i++) {
            nodes.add(new GraphNode("loose" + i, "loose" + i, GraphNode.Type.NOTE, "", 0));
        }
        for (int s = 0; s < 30; s++) {
            nodes.add(new GraphNode("hub" + s, "hub" + s, GraphNode.Type.NOTE, "", 15));
            for (int l = 0; l < 15; l++) {
                String leaf = "leaf" + s + "-" + l;
                nodes.add(new GraphNode(leaf, leaf, GraphNode.Type.NOTE, "", 1));
                edges.add(new GraphEdge("hub" + s, leaf, GraphEdge.Type.LINK));
            }
        }
        double[] ratio = onFx(() -> {
            GraphCanvas canvas = new GraphCanvas();
            canvas.setData(new GraphData(nodes, edges));
            canvas.pause();
            canvas.runTicks(400);
            double hubs = 0;
            for (int s = 0; s < 30; s++) {
                hubs += distanceFromCentre(canvas.positionOf("hub" + s));
            }
            double loose = 0;
            for (int i = 0; i < 1500; i++) {
                loose += distanceFromCentre(canvas.positionOf("loose" + i));
            }
            return new double[] {(hubs / 30) / (loose / 1500), loose / 1500};
        });
        // Obsidian (its graph worker, started the way its graph view places the notes: each one
        // beside the notes it links to) leaves the hubs at 0.85-1.04 of the loose notes' distance
        // from the centre, and the loose notes about 2950-3090 from it. Started at random over the
        // disc instead, the hubs end at 0.57-0.61: packed in the middle.
        assertTrue(ratio[0] > 0.78 && ratio[0] < 1.15, "hubs / loose mean distance from the centre: " + ratio[0]);
        assertTrue(ratio[1] > 2700 && ratio[1] < 3400, "loose notes' mean distance from the centre: " + ratio[1]);
    }

    @Test
    void theNotesAlreadyOnTheGraphStayWhereTheyAreWhenItChanges() throws Exception {
        Assumptions.assumeTrue(FxTestSupport.isFxRuntimeAvailable());
        GraphNode a = new GraphNode("a", "a", GraphNode.Type.NOTE, "", 1);
        GraphNode b = new GraphNode("b", "b", GraphNode.Type.NOTE, "", 1);
        GraphNode c = new GraphNode("c", "c", GraphNode.Type.NOTE, "", 0);
        GraphNode d = new GraphNode("d", "d", GraphNode.Type.NOTE, "", 1);
        double[][] seen = onFx(() -> {
            GraphCanvas canvas = new GraphCanvas();
            canvas.setData(new GraphData(List.of(a, b, c), List.of(new GraphEdge("a", "b", GraphEdge.Type.LINK))));
            canvas.pause();
            canvas.runTicks(300);
            double[] before = canvas.positionOf("a");
            canvas.setData(new GraphData(List.of(a, b, c, d), List.of(new GraphEdge("a", "b", GraphEdge.Type.LINK),
                    new GraphEdge("d", "a", GraphEdge.Type.LINK))));
            canvas.pause();
            return new double[][] {before, canvas.positionOf("a"), canvas.positionOf("d")};
        });
        assertEquals(seen[0][0], seen[1][0], "a's x");
        assertEquals(seen[0][1], seen[1][1], "a's y");
        // As in Obsidian, a new note starts beside the notes it links to: within half of
        // sqrt(60·60·new notes) of them on each axis.
        double gap = Math.hypot(seen[2][0] - seen[1][0], seen[2][1] - seen[1][1]);
        assertTrue(gap <= 30 * Math.sqrt(2) + 1e-9, "d starts this far from a: " + gap);
    }

    @Test
    void everyNoteKeepsObsidiansRoomAroundItEvenWhenNothingPushesItAway() throws Exception {
        Assumptions.assumeTrue(FxTestSupport.isFxRuntimeAvailable());
        double distance = onFx(() -> {
            GraphCanvas canvas = new GraphCanvas();
            canvas.setRepulsion(0);
            canvas.setData(new GraphData(List.of(
                    new GraphNode("a", "a", GraphNode.Type.NOTE, "", 0),
                    new GraphNode("b", "b", GraphNode.Type.NOTE, "", 0)), List.of()));
            canvas.pause();
            canvas.runTicks(400);
            double[] a = canvas.positionOf("a");
            double[] b = canvas.positionOf("b");
            return Math.hypot(a[0] - b[0], a[1] - b[1]);
        });
        // The centre pulls them together; Obsidian's collision keeps each 60 around it.
        assertTrue(distance > 105 && distance < 135, "distance between the two notes: " + distance);
    }

    private static double distanceFromCentre(double[] p) {
        return Math.hypot(p[0], p[1]);
    }

    private static <T> T onFx(java.util.concurrent.Callable<T> work) throws Exception {
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
        assertTrue(done.await(120, TimeUnit.SECONDS), "never completed on the FX thread");
        if (failure.get() != null) {
            throw new AssertionError(failure.get());
        }
        return result.get();
    }
}
