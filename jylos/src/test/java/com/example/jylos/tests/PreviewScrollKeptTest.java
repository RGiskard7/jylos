package com.example.jylos.tests;

import static com.example.jylos.tests.FxTestSupport.onFx;
import static com.example.jylos.tests.FxTestSupport.runFx;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import com.example.jylos.data.models.Note;
import com.example.jylos.ui.controller.EditorController;

import javafx.scene.Scene;
import javafx.scene.layout.VBox;
import javafx.scene.web.WebView;
import javafx.stage.Stage;

/**
 * Drawing the reading view again for the same note (after an edit, a theme or plugin change)
 * keeps where the reader was; another note starts at the top.
 */
class PreviewScrollKeptTest {

    @Test
    void theSameNoteKeepsItsScrollAnotherStartsAtTheTop() throws Exception {
        Assumptions.assumeTrue(FxTestSupport.isFxRuntimeAvailable());
        Note longNote = note("larga.md", "Larga", "Párrafo\n\n".repeat(400));
        Note other = note("otra.md", "Otra", "Párrafo\n\n".repeat(400));
        AtomicReference<EditorController> controller = new AtomicReference<>();
        AtomicReference<WebView> webView = new AtomicReference<>();
        AtomicReference<Stage> stage = new AtomicReference<>();
        runFx(() -> {
            EditorController editor = new EditorController();
            WebView view = new WebView();
            VBox pane = new VBox(view);
            set(editor, "previewWebView", view);
            set(editor, "previewPane", pane);
            set(editor, "currentNote", longNote);
            Stage window = new Stage();
            window.setScene(new Scene(pane, 600, 400));
            window.show();
            editor.refreshPreview(false);
            controller.set(editor);
            webView.set(view);
            stage.set(window);
        });
        try {
            assertTrue(waitFor(() -> scrollHeight(webView.get()) > 2000), "the long note is shown");
            runFx(() -> webView.get().getEngine().executeScript("window.scrollTo(0, 1500)"));
            assertTrue(waitFor(() -> scrollY(webView.get()) == 1500), "scrolled");

            runFx(() -> controller.get().refreshPreview(false));
            Thread.sleep(500);
            assertTrue(waitFor(() -> scrollY(webView.get()) == 1500), "drawn again where it was: " + scrollY(webView.get()));

            runFx(() -> {
                set(controller.get(), "currentNote", other);
                controller.get().refreshPreview(false);
            });
            assertTrue(waitFor(() -> String.valueOf(body(webView.get())).startsWith("Párrafo")
                    && scrollY(webView.get()) == 0), "another note starts at the top");
            assertEquals(0, scrollY(webView.get()));
        } finally {
            runFx(() -> {
                controller.get().teardown();
                stage.get().close();
            });
        }
    }

    private static Note note(String id, String title, String content) {
        Note note = new Note(title, content);
        note.setId(id);
        return note;
    }

    private static int scrollY(WebView view) {
        return number(view, "window.scrollY || 0");
    }

    private static int scrollHeight(WebView view) {
        return number(view, "document.body ? document.body.scrollHeight : 0");
    }

    private static Object body(WebView view) {
        try {
            return onFx(() -> view.getEngine().executeScript("document.body ? document.body.innerText : ''"));
        } catch (Exception e) {
            return "";
        }
    }

    private static int number(WebView view, String script) {
        try {
            return ((Number) onFx(() -> view.getEngine().executeScript(script))).intValue();
        } catch (Exception e) {
            return -1;
        }
    }

    private static boolean waitFor(BooleanSupplier condition) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (System.nanoTime() < deadline) {
            if (condition.getAsBoolean()) {
                return true;
            }
            Thread.sleep(50);
        }
        return false;
    }

    private static void set(Object target, String name, Object value) throws Exception {
        Field field = EditorController.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }
}
