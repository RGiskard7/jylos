package com.example.jylos.ui.graph;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static com.example.jylos.tests.FxTestSupport.onFx;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import com.example.jylos.graph.GraphData;
import com.example.jylos.graph.GraphEdge;
import com.example.jylos.graph.GraphNode;
import com.example.jylos.tests.FxTestSupport;

/**
 * How the graph opens, as in Obsidian: from close up it zooms out to the zoom chosen last time,
 * centred on the middle of the graph; with no zoom chosen yet it frames the whole graph while it
 * spreads out, until the user zooms or drags the view, and the zoom they choose is reported to
 * be kept.
 */
class GraphCanvasViewTest {

    private static final double W = 800;
    private static final double H = 600;

    @Test
    void itOpensCloseUpAndZoomsOutToTheZoomChosenLastTime() throws Exception {
        Assumptions.assumeTrue(FxTestSupport.isFxRuntimeAvailable());
        double[][] seen = onFx(() -> {
            GraphCanvas canvas = sized();
            canvas.openView(0.2);
            canvas.setData(graph(200));
            canvas.pause();
            double[] first = canvas.view();
            canvas.runFrames(60);
            return new double[][] {first, canvas.view()};
        });
        assertEquals(1.0, seen[0][0], 1e-9, "starts close up");
        assertEquals(0.2, seen[1][0], 0.002, "then at the zoom chosen last time");
        assertEquals(W / 2, seen[1][1], 1e-6, "the centre of the graph in the middle of the view (x)");
        assertEquals(H / 2, seen[1][2], 1e-6, "the centre of the graph in the middle of the view (y)");
    }

    @Test
    void withNoZoomChosenTheWholeGraphIsFramedOnceItSettles() throws Exception {
        Assumptions.assumeTrue(FxTestSupport.isFxRuntimeAvailable());
        double[] box = onFx(() -> {
            GraphCanvas canvas = sized();
            canvas.openView(null);
            canvas.setData(graph(200));
            canvas.pause();
            canvas.runFrames(500);
            return canvas.screenBounds();
        });
        // Inside the view, and filling a good part of it: not a speck in the middle.
        assertTrue(box[0] >= 0 && box[1] >= 0 && box[2] <= W && box[3] <= H, "on screen: " + Arrays.toString(box));
        assertTrue(Math.max((box[2] - box[0]) / W, (box[3] - box[1]) / H) > 0.7,
                "fills the view: " + Arrays.toString(box));
    }

    @Test
    void zoomingByHandStopsTheFramingAndIsReported() throws Exception {
        Assumptions.assumeTrue(FxTestSupport.isFxRuntimeAvailable());
        List<Double> reported = new ArrayList<>();
        double[] scales = onFx(() -> {
            GraphCanvas canvas = sized();
            canvas.setOnZoomChosen(reported::add);
            canvas.openView(null);
            canvas.setData(graph(200));
            canvas.pause();
            canvas.runFrames(10);
            canvas.zoomIn();
            double chosen = canvas.view()[0];
            canvas.runFrames(200);
            double later = canvas.view()[0];
            canvas.resetView();
            return new double[] {chosen, later};
        });
        assertEquals(scales[0], scales[1], 1e-12, "the graph no longer reframes itself");
        assertEquals(2, reported.size(), "reported: " + reported);
        assertEquals(scales[0], reported.get(0), 1e-12);
        assertNull(reported.get(1), "resetting the view goes back to framing the graph");
    }

    private static GraphCanvas sized() {
        GraphCanvas canvas = new GraphCanvas();
        canvas.resize(W, H);
        return canvas;
    }

    /** {@code n} notes, a third of them linked in a chain. */
    private static GraphData graph(int n) {
        List<GraphNode> nodes = new ArrayList<>();
        List<GraphEdge> edges = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            nodes.add(new GraphNode("n" + i, "n" + i, GraphNode.Type.NOTE, "", 1));
            if (i > 0 && i % 3 == 0) {
                edges.add(new GraphEdge("n" + (i - 3), "n" + i, GraphEdge.Type.LINK));
            }
        }
        return new GraphData(nodes, edges);
    }
}
