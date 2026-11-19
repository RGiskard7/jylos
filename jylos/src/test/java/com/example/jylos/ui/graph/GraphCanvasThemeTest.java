package com.example.jylos.ui.graph;

import static com.example.jylos.tests.FxTestSupport.onFx;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import com.example.jylos.graph.GraphData;
import com.example.jylos.graph.GraphNode;
import com.example.jylos.tests.FxTestSupport;
import com.example.jylos.util.ThemePalette;

import javafx.scene.Scene;
import javafx.scene.SnapshotParameters;
import javafx.scene.image.PixelReader;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.stage.Stage;

/** The graph takes an external theme's colours; without one, its own dark and light. */
class GraphCanvasThemeTest {

    private static final int W = 300;
    private static final int H = 200;
    private static final ThemePalette GRUVBOX = new ThemePalette("#282828", "#32302f", "#3c3836", "#ebdbb2",
            "#bdae93", "#928374", "#504945", "#45403d", "#d79921", "#d79921");

    @Test
    void anExternalThemePaintsTheBackgroundAndTheNotes() throws Exception {
        Assumptions.assumeTrue(FxTestSupport.isFxRuntimeAvailable());
        Color[] seen = onFx(() -> {
            GraphCanvas gc = canvas();
            gc.setTheme(true, GRUVBOX);
            PixelReader reader = snapshot(gc);
            return new Color[] {reader.getColor(1, 1), reader.getColor(W / 2, H / 2)};
        });

        assertEquals(Color.web("#282828"), opaque(seen[0]), "the background");
        assertEquals(Color.web("#bdae93"), opaque(seen[1]), "a note, in the theme's muted text colour");
    }

    @Test
    void withoutAPaletteTheGraphKeepsItsOwnColours() throws Exception {
        Assumptions.assumeTrue(FxTestSupport.isFxRuntimeAvailable());
        Color[] seen = onFx(() -> {
            GraphCanvas gc = canvas();
            gc.setTheme(true, GRUVBOX);
            gc.setTheme(true, null);
            Color dark = snapshot(gc).getColor(1, 1);
            gc.setTheme(false, null);
            return new Color[] {dark, snapshot(gc).getColor(1, 1)};
        });

        assertEquals(Color.web("#1a1a1a"), opaque(seen[0]));
        assertEquals(Color.web("#f4f4f6"), opaque(seen[1]));
        assertTrue(!seen[0].equals(seen[1]));
    }

    private static Color opaque(Color c) {
        return Color.color(round(c.getRed()), round(c.getGreen()), round(c.getBlue()));
    }

    private static double round(double channel) {
        return Math.round(channel * 255) / 255.0;
    }

    private static GraphCanvas canvas() {
        GraphCanvas gc = new GraphCanvas();
        gc.setPrefSize(W, H);
        Stage stage = new Stage();
        stage.setScene(new Scene(new StackPane(gc), W, H));
        stage.show();
        gc.setData(new GraphData(List.of(new GraphNode("a", "a", GraphNode.Type.NOTE, "", 0)), List.of()));
        gc.pause();
        set(gc, "offsetX", W / 2.0);
        set(gc, "offsetY", H / 2.0);
        set(gc, "scale", 1.0);
        return gc;
    }

    private static PixelReader snapshot(GraphCanvas gc) {
        try {
            Field x = GraphCanvas.class.getDeclaredField("x");
            x.setAccessible(true);
            ((double[]) x.get(gc))[0] = 0;
            Field y = GraphCanvas.class.getDeclaredField("y");
            y.setAccessible(true);
            ((double[]) y.get(gc))[0] = 0;
            Method draw = GraphCanvas.class.getDeclaredMethod("draw");
            draw.setAccessible(true);
            draw.invoke(gc);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
        WritableImage image = gc.snapshot(new SnapshotParameters(), null);
        return image.getPixelReader();
    }

    private static void set(GraphCanvas gc, String name, double value) {
        try {
            Field f = GraphCanvas.class.getDeclaredField(name);
            f.setAccessible(true);
            f.setDouble(gc, value);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }
}
