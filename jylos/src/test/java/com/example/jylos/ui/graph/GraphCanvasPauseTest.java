package com.example.jylos.ui.graph;

import static com.example.jylos.tests.FxTestSupport.onFx;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import com.example.jylos.graph.GraphData;
import com.example.jylos.graph.GraphEdge;
import com.example.jylos.graph.GraphNode;
import com.example.jylos.tests.FxTestSupport;

/**
 * A hidden graph does not move. The layout runs on the interface thread, so with the whole
 * vault on the graph it slowed down everything else for seconds: a build that finished after
 * the graph's tab was closed, or a resize, started it again behind the scenes.
 */
class GraphCanvasPauseTest {

    @Test
    void aPausedGraphOnlyMovesAgainWhenResumed() throws Exception {
        Assumptions.assumeTrue(FxTestSupport.isFxRuntimeAvailable());

        List<String> seen = onFx(() -> {
            GraphCanvas canvas = new GraphCanvas();
            canvas.resize(800, 600);
            List<String> out = new ArrayList<>();
            canvas.setData(graph(200));
            out.add("shown " + running(canvas));
            canvas.pause();
            out.add("paused " + running(canvas));
            canvas.setData(graph(200)); // a build that finished after the graph was hidden
            out.add("new data while paused " + running(canvas));
            canvas.resize(900, 700);
            out.add("resized while paused " + running(canvas));
            canvas.resume();
            out.add("resumed " + running(canvas));
            canvas.pause();
            return out;
        });

        assertEquals(List.of("shown true", "paused false", "new data while paused false",
                "resized while paused false", "resumed true"), seen);
    }

    private static boolean running(GraphCanvas canvas) throws Exception {
        Field field = GraphCanvas.class.getDeclaredField("timerRunning");
        field.setAccessible(true);
        return (boolean) field.get(canvas);
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
