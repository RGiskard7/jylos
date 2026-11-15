package com.example.jylos.ui.web;

import static com.example.jylos.tests.FxTestSupport.onFx;
import static com.example.jylos.tests.FxTestSupport.runFx;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import com.example.jylos.tests.FxTestSupport;

/**
 * Work asked for while a page is loading waits for that page: it runs against it, not against
 * the page being replaced, and only once.
 */
class JavaFxHtmlViewTest {

    @Test
    void workAskedForWhileLoadingRunsOnceThePageHasLoaded() throws Exception {
        Assumptions.assumeTrue(FxTestSupport.isFxRuntimeAvailable());
        List<Object> seen = new CopyOnWriteArrayList<>();
        JavaFxHtmlView view = onFx(JavaFxHtmlView::new);

        runFx(() -> {
            view.loadHtml("<p>primera</p>");
            view.whenLoaded(() -> seen.add(view.evaluate("document.body.innerText")));
        });
        waitFor(() -> seen.size() == 1);
        runFx(() -> {
            view.loadHtml("<p>segunda</p>");
            view.whenLoaded(() -> seen.add(view.evaluate("document.body.innerText")));
        });
        waitFor(() -> seen.size() == 2);
        runFx(() -> view.loadHtml("<p>tercera</p>"));
        Thread.sleep(500);

        assertEquals(List.of("primera", "segunda"), seen);
    }

    private static void waitFor(BooleanSupplier done) throws InterruptedException {
        long until = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (System.nanoTime() < until && !done.getAsBoolean()) {
            Thread.sleep(50);
        }
    }
}
