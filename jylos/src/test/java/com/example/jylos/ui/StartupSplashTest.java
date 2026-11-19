package com.example.jylos.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static com.example.jylos.tests.FxTestSupport.onFx;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import com.example.jylos.tests.FxTestSupport;

import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.control.Labeled;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;

/**
 * The window shown while the main window is built: in the theme the app opens in, painted
 * before the (blocking) build starts, and gone once it has run.
 */
class StartupSplashTest {

    @Test
    void showsTheAppNameInTheThemeTheAppOpensIn() throws Exception {
        Assumptions.assumeTrue(FxTestSupport.isFxRuntimeAvailable());
        for (boolean dark : new boolean[] {true, false}) {
            AtomicReference<StartupSplash> splash = new AtomicReference<>();
            String state = onFx(() -> {
                splash.set(StartupSplash.show(dark, "Cargando…"));
                Region root = (Region) splash.get().stage().getScene().getRoot();
                root.applyCss();
                Color fill = (Color) root.getBackground().getFills().get(0).getFill();
                List<String> texts = new ArrayList<>();
                for (Node node : root.lookupAll(".label")) {
                    texts.add(((Labeled) node).getText());
                }
                return splash.get().stage().isShowing() + " " + (fill.getBrightness() < 0.5 ? "dark" : "light")
                        + " " + texts;
            });
            assertEquals("true " + (dark ? "dark" : "light") + " [Jylos, Cargando…]", state);
            onFx(() -> {
                splash.get().close();
                return null;
            });
            assertFalse(onFx(() -> splash.get().stage().isShowing()));
        }
    }

    /** The build blocks the UI thread: it must start only once the splash is on screen, run once, and be able to show a dialog. */
    @Test
    void runsTheBuildOnceAfterTheSplashHasBeenPainted() throws Exception {
        Assumptions.assumeTrue(FxTestSupport.isFxRuntimeAvailable());
        List<String> events = new CopyOnWriteArrayList<>();
        CountDownLatch built = new CountDownLatch(1);
        AtomicReference<StartupSplash> splash = new AtomicReference<>();
        AtomicLong shownAt = new AtomicLong();
        AtomicLong builtAt = new AtomicLong();
        onFx(() -> {
            splash.set(StartupSplash.show(true, "Cargando…"));
            shownAt.set(System.nanoTime());
            splash.get().stage().getScene().addPostLayoutPulseListener(() -> events.add("painted"));
            splash.get().thenRun(() -> {
                // A dialog shown while building (showAndWait) runs a nested event loop, which
                // JavaFX refuses during a pulse.
                String nested;
                try {
                    Object key = new Object();
                    Platform.runLater(() -> Platform.exitNestedEventLoop(key, "+dialog"));
                    nested = (String) Platform.enterNestedEventLoop(key);
                } catch (IllegalStateException e) {
                    nested = "+no-dialog: " + e.getMessage();
                }
                builtAt.compareAndSet(0, System.nanoTime());
                events.add("build" + (splash.get().stage().isShowing() ? "+splash" : "") + nested);
                built.countDown();
            });
            events.add("scheduled");
            return null;
        });
        assertTrue(built.await(10, TimeUnit.SECONDS), "the build never ran");
        Thread.sleep(300);
        // The splash is still open and keeps adding "painted": check a fixed copy.
        List<String> seen = List.copyOf(events);
        assertEquals("scheduled", seen.get(0));
        int build = seen.indexOf("build+splash+dialog");
        // The listener runs before its pulse is rendered; a pulse only starts once the previous
        // one has been rendered. Two pulses before the build mean the splash has been drawn.
        assertTrue(build > 0 && seen.subList(0, build).stream().filter("painted"::equals).count() >= 2,
                "the build ran before the splash was painted, without it, or where it cannot show a dialog: " + seen);
        assertEquals(1, seen.stream().filter(e -> e.startsWith("build")).count(), "ran once: " + seen);
        // On macOS a new window reaches the screen only some time after JavaFX has drawn it
        // (150 ms was not always enough when measured); blocking sooner keeps it hidden.
        long waitedMs = (builtAt.get() - shownAt.get()) / 1_000_000;
        assertTrue(waitedMs >= 300 && waitedMs < 1500, "the build started " + waitedMs + " ms after the splash");
        onFx(() -> {
            splash.get().close();
            return null;
        });
    }
}
