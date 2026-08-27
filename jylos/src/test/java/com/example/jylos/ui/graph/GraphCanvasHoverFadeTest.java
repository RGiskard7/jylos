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
import com.example.jylos.graph.GraphNode;
import com.example.jylos.tests.FxTestSupport;

import javafx.animation.AnimationTimer;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;

/**
 * The hover highlight (dim everything but the hovered node's relation) used to snap
 * on and off instantly — the very next frame after the mouse moved, no transition.
 * The zoom-based label fade already eased smoothly via {@code scale}; hover didn't
 * ease at all. This drives the actual easing timer directly (not through a real mouse
 * event, which JavaFX doesn't deliver on a schedule this test controls) and checks
 * {@code hoverStrength} lands strictly between its start and end points after a single
 * frame — proof it's actually interpolating, not jumping straight to the target.
 */
class GraphCanvasHoverFadeTest {

    @Test
    void hoverStrengthEasesTowardItsTargetInsteadOfJumpingThereInOneFrame() throws Exception {
        Assumptions.assumeTrue(FxTestSupport.isFxRuntimeAvailable());

        CountDownLatch done = new CountDownLatch(1);
        double[] afterOneFrame = new double[1];
        double[] afterManyFrames = new double[1];
        Platform.runLater(() -> {
            try {
                GraphCanvas gc = new GraphCanvas();
                gc.setPrefSize(600, 400);
                Stage stage = new Stage();
                stage.setScene(new Scene(new StackPane(gc), 600, 400));
                stage.show();
                gc.setData(new GraphData(
                        List.of(new GraphNode("a", "A", GraphNode.Type.NOTE, null, 1)), List.of()));
                gc.pause();

                setDouble(gc, "hoverStrength", 0.0);
                setDouble(gc, "hoverTarget", 1.0);

                invokeHoverTimerHandle(gc);
                afterOneFrame[0] = getDouble(gc, "hoverStrength");

                for (int i = 0; i < 100; i++) {
                    invokeHoverTimerHandle(gc);
                }
                afterManyFrames[0] = getDouble(gc, "hoverStrength");
            } finally {
                done.countDown();
            }
        });

        assertTrue(done.await(30, TimeUnit.SECONDS), "test never completed on the FX thread");
        assertTrue(afterOneFrame[0] > 0.0 && afterOneFrame[0] < 1.0,
                "one animation frame in, hoverStrength must be strictly between its start (0) and "
                        + "target (1) — landing exactly on either end means it isn't easing, it's "
                        + "jumping straight to the target: got " + afterOneFrame[0]);
        assertTrue(afterManyFrames[0] > 0.99,
                "given enough frames the easing must actually converge on its target: got " + afterManyFrames[0]);
    }

    private static void invokeHoverTimerHandle(GraphCanvas gc) {
        try {
            Field timerField = GraphCanvas.class.getDeclaredField("hoverTimer");
            timerField.setAccessible(true);
            AnimationTimer hoverTimer = (AnimationTimer) timerField.get(gc);
            Method handle = AnimationTimer.class.getDeclaredMethod("handle", long.class);
            handle.setAccessible(true);
            handle.invoke(hoverTimer, 0L);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
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
}
