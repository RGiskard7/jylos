package com.example.jylos.ui.graph;

import static com.example.jylos.tests.FxTestSupport.onFx;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import com.example.jylos.graph.GraphData;
import com.example.jylos.graph.GraphNode;
import com.example.jylos.tests.FxTestSupport;

import javafx.event.EventType;
import javafx.scene.canvas.Canvas;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;

/**
 * A click on a note opens it; with Cmd/Ctrl or the middle button it asks for a new tab, as a
 * link does.
 */
class GraphCanvasClickTest {

    @Test
    void aClickOpensTheNoteAndCmdCtrlOrTheMiddleButtonAskForANewTab() throws Exception {
        Assumptions.assumeTrue(FxTestSupport.isFxRuntimeAvailable());

        List<String> opened = onFx(() -> {
            GraphCanvas graph = new GraphCanvas();
            graph.resize(800, 600);
            graph.setData(new GraphData(List.of(new GraphNode("a.md", "A", GraphNode.Type.NOTE, "", 1)), List.of()));
            graph.pause();
            List<String> out = new ArrayList<>();
            graph.setOnOpenNote((id, newTab) -> out.add(id + (newTab ? " in a new tab" : "")));
            double[] at = screenOf(graph, 0);
            click(graph, at, MouseButton.PRIMARY, false);
            click(graph, at, MouseButton.PRIMARY, true);
            click(graph, at, MouseButton.MIDDLE, false);
            return out;
        });

        assertEquals(List.of("a.md", "a.md in a new tab", "a.md in a new tab"), opened);
    }

    private static double[] screenOf(GraphCanvas graph, int node) throws Exception {
        double[] x = (double[]) field(graph, "x");
        double[] y = (double[]) field(graph, "y");
        Method sx = GraphCanvas.class.getDeclaredMethod("sx", double.class);
        Method sy = GraphCanvas.class.getDeclaredMethod("sy", double.class);
        sx.setAccessible(true);
        sy.setAccessible(true);
        return new double[] {(double) sx.invoke(graph, x[node]), (double) sy.invoke(graph, y[node])};
    }

    private static void click(GraphCanvas graph, double[] at, MouseButton button, boolean shortcut) throws Exception {
        Canvas canvas = (Canvas) field(graph, "canvas");
        boolean primary = button == MouseButton.PRIMARY;
        boolean middle = button == MouseButton.MIDDLE;
        for (EventType<MouseEvent> type : List.of(MouseEvent.MOUSE_PRESSED, MouseEvent.MOUSE_RELEASED)) {
            canvas.fireEvent(new MouseEvent(type, at[0], at[1], at[0], at[1], button, 1,
                    false, shortcut, false, shortcut, primary, middle, false, false, false, true, null));
        }
    }

    private static Object field(GraphCanvas graph, String name) throws Exception {
        Field field = GraphCanvas.class.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(graph);
    }
}
