package com.example.jylos.ui.controller;

import static com.example.jylos.tests.FxTestSupport.onFx;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import com.example.jylos.tests.FxTestSupport;
import com.example.jylos.ui.web.HtmlView;

import javafx.scene.Scene;
import javafx.scene.layout.StackPane;

/** A link in the release notes shown before an update opens in the browser. */
class ReleaseNotesLinksTest {

    @Test
    void aLinkInTheReleaseNotesOpensInTheBrowser() throws Exception {
        Assumptions.assumeTrue(FxTestSupport.isFxRuntimeAvailable());
        List<String> opened = new CopyOnWriteArrayList<>();
        UpdateInstallSupport updates = new UpdateInstallSupport();
        updates.wire(key -> key, status -> { }, opened::add, () -> true, () -> { }, () -> false);

        HtmlView notes = onFx(() -> {
            HtmlView view = updates.releaseNotesView("Ver [el cambio](https://example.org/cambio).");
            new Scene(new StackPane(view.node()), 400, 300);
            return view;
        });
        long until = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (System.nanoTime() < until && !onFx(notes::isLoaded)) {
            Thread.sleep(50);
        }
        onFx(() -> notes.evaluate("document.querySelector('a').click()"));
        until = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (System.nanoTime() < until && opened.isEmpty()) {
            Thread.sleep(50);
        }

        assertEquals(List.of("https://example.org/cambio"), opened);
    }
}
