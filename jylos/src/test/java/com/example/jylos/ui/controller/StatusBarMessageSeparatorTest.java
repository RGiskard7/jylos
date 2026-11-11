package com.example.jylos.ui.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import com.example.jylos.tests.FxTestSupport;

import javafx.application.Platform;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;

/**
 * The status bar's message area: opening a note clears the message, and the separator in
 * front of it goes away with it, so no dangling "|" is left after the Git entries. A long
 * message gets its full text as a tooltip.
 */
class StatusBarMessageSeparatorTest {

    @Test
    void theSeparatorBeforeTheMessageOnlyShowsWithAMessage() throws Exception {
        Assumptions.assumeTrue(FxTestSupport.isFxRuntimeAvailable());

        List<Object> states = onFx(() -> {
            Label label = new Label();
            Separator separator = new Separator();
            StatusBarSupport.showMessage(label, separator, "Búsqueda: acta");
            boolean withMessage = separator.isVisible() && separator.isManaged();
            StatusBarSupport.showMessage(label, separator, "");
            boolean empty = separator.isVisible() || separator.isManaged();
            StatusBarSupport.showMessage(label, separator, null);
            boolean nullMessage = separator.isVisible();
            String longText = "x".repeat(60);
            StatusBarSupport.showMessage(label, separator, longText);
            return List.of(withMessage, empty, nullMessage, label.getText(),
                    label.getTooltip() != null ? label.getTooltip().getText() : "no tooltip");
        });

        assertEquals(List.of(true, false, false, "x".repeat(60), "x".repeat(60)), states);
    }

    private static <T> T onFx(Callable<T> work) throws Exception {
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
        assertTrue(done.await(30, TimeUnit.SECONDS), "test never completed on the FX thread");
        if (failure.get() != null) {
            throw new AssertionError(failure.get());
        }
        return result.get();
    }
}
