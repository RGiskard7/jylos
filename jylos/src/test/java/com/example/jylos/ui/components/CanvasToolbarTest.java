package com.example.jylos.ui.components;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import com.example.jylos.tests.FxTestSupport;
import com.example.jylos.util.CanvasModel;

import javafx.application.Platform;
import javafx.geometry.Bounds;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.ButtonBase;
import javafx.scene.layout.StackPane;

/** Every icon of the canvas toolbar sits on the same vertical line, in the middle of the bar. */
class CanvasToolbarTest {

    @Test
    void everyToolbarIconIsCentredInTheBar() throws Exception {
        Assumptions.assumeTrue(FxTestSupport.isFxRuntimeAvailable());
        for (String theme : new String[] {"modern-theme.css", "dark-theme.css"}) {
            List<String> offCentre = offCentreIcons(theme);
            assertEquals(List.of("checked"), offCentre, theme);
        }
    }

    private static List<String> offCentreIcons(String theme) throws Exception {
        AtomicReference<List<String>> result = new AtomicReference<>();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        CountDownLatch done = new CountDownLatch(1);
        Platform.runLater(() -> {
            try {
                CanvasView view = new CanvasView(CanvasModel.Document.parse("{}"), f -> { }, f -> null, f -> f,
                        f -> f, List::of, List::of, s -> { }, () -> { }, k -> k);
                StackPane host = new StackPane(view);
                Scene scene = new Scene(host, 1000, 700);
                scene.getStylesheets().add(CanvasToolbarTest.class
                        .getResource("/com/example/jylos/ui/css/" + theme).toExternalForm());
                host.applyCss();
                host.layout();
                Node bar = view.lookup(".canvas-toolbar");
                Bounds barBounds = bar.localToScene(bar.getLayoutBounds());
                double centre = barBounds.getMinX() + barBounds.getWidth() / 2;
                List<String> out = new ArrayList<>();
                int buttons = 0;
                for (Node node : bar.lookupAll(".toolbar-btn")) {
                    if (!(node instanceof ButtonBase button) || button.getGraphic() == null) {
                        continue;
                    }
                    buttons++;
                    Node icon = button.getGraphic();
                    Bounds b = icon.localToScene(icon.getLayoutBounds());
                    double offset = b.getMinX() + b.getWidth() / 2 - centre;
                    if (Math.abs(offset) > 0.5) {
                        out.add(String.join(",", button.getStyleClass()) + " off by " + offset);
                    }
                }
                out.add(0, buttons >= 10 ? "checked" : "only " + buttons + " buttons found");
                result.set(out);
            } catch (Throwable t) {
                failure.set(t);
            } finally {
                done.countDown();
            }
        });
        assertTrue(done.await(30, TimeUnit.SECONDS), "never completed on the FX thread");
        if (failure.get() != null) {
            throw new AssertionError(failure.get());
        }
        return result.get();
    }
}
