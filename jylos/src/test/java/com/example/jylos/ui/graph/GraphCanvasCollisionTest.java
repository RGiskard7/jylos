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
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;

/**
 * The physics simulation had many-body repulsion, link springs and center gravity —
 * the classic d3-force trio — but no collision force, so two nodes could still end up
 * with their circles overlapping, unlike Obsidian's graph.
 *
 * <p>An <em>unconnected</em> pair is the wrong scenario to prove this with: plain
 * charge repulsion alone, given enough ticks, already pushes two free-floating nodes
 * apart past their radius sum with no help from collision — a test built that way
 * would pass whether or not the collision code runs at all. Even a <em>linked</em>
 * pair isn't automatically enough: charge and the spring settle any linked pair at a
 * fixed ~45.5px apart regardless of node size (confirmed by measurement — that
 * equilibrium is set by the charge/spring balance alone), so a radius sum only
 * slightly above that would also pass by coincidence with collision removed. Degree
 * 400 (radius sum ~65px) leaves a clear, non-coincidental margin past that ~45.5px
 * figure — only the collision force reaches it.</p>
 */
class GraphCanvasCollisionTest {

    @Test
    void linkedOverlappingNodesSeparateAfterTheSimulationSettles() throws Exception {
        Assumptions.assumeTrue(FxTestSupport.isFxRuntimeAvailable());

        CountDownLatch done = new CountDownLatch(1);
        double[] finalDistance = new double[1];
        double[] requiredDistance = new double[1];
        Platform.runLater(() -> {
            try {
                GraphCanvas gc = new GraphCanvas();
                gc.setPrefSize(600, 400);
                Stage stage = new Stage();
                stage.setScene(new Scene(new StackPane(gc), 600, 400));
                stage.show();

                // Linked pair, high degree so radius[a]+radius[b] (~65px) clears the
                // ~45.5px charge/spring equilibrium by a real margin — only collision
                // can force them apart that much further.
                GraphNode a = new GraphNode("a", "A", GraphNode.Type.NOTE, null, 400);
                GraphNode b = new GraphNode("b", "B", GraphNode.Type.NOTE, null, 400);
                GraphEdge edge = new GraphEdge(a.id(), b.id(), GraphEdge.Type.LINK);
                gc.setData(new GraphData(List.of(a, b), List.of(edge)));
                gc.pause(); // drive tick() ourselves, deterministically, instead of the animation timer

                // Force them to start on top of each other — the worst-case overlap.
                setDoubleArrayElement(gc, "x", 0, 0.0);
                setDoubleArrayElement(gc, "y", 0, 0.0);
                setDoubleArrayElement(gc, "x", 1, 0.5);
                setDoubleArrayElement(gc, "y", 1, 0.0);

                for (int i = 0; i < 300; i++) {
                    invokeNoArg(gc, "tick");
                }

                double[] x = getDoubleArray(gc, "x");
                double[] y = getDoubleArray(gc, "y");
                double[] radius = getDoubleArray(gc, "radius");
                finalDistance[0] = Math.hypot(x[1] - x[0], y[1] - y[0]);
                requiredDistance[0] = radius[0] + radius[1];
            } finally {
                done.countDown();
            }
        });

        assertTrue(done.await(30, TimeUnit.SECONDS), "test never completed on the FX thread");
        assertTrue(finalDistance[0] >= requiredDistance[0] - 0.5,
                "node circles must not overlap once the simulation settles: distance="
                        + finalDistance[0] + " but circles need at least " + requiredDistance[0]);
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

    private static void setDoubleArrayElement(GraphCanvas gc, String field, int index, double value) {
        getDoubleArray(gc, field)[index] = value;
    }

    private static void invokeNoArg(GraphCanvas gc, String methodName) {
        try {
            Method m = GraphCanvas.class.getDeclaredMethod(methodName);
            m.setAccessible(true);
            m.invoke(gc);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }
}
