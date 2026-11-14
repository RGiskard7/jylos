package com.example.jylos.tests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static com.example.jylos.tests.FxTestSupport.runFx;

import java.lang.reflect.Field;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import com.example.jylos.data.models.Note;
import com.example.jylos.plugin.PreviewContext;
import com.example.jylos.plugin.PreviewEnhancer;
import com.example.jylos.ui.controller.EditorController;

import javafx.scene.Scene;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

/**
 * When a plugin adds or removes something from the reading view, the open note is drawn again
 * in the theme it was in, not in the light one.
 */
class PreviewThemeOnPluginChangeTest {

    @Test
    void aPluginChangeKeepsTheDarkTheme() throws Exception {
        Assumptions.assumeTrue(FxTestSupport.isFxRuntimeAvailable());
        List<Boolean> renders = new CopyOnWriteArrayList<>();
        PreviewEnhancer recorder = new PreviewEnhancer() {
            @Override
            public String transformHtml(PreviewContext context, String html) {
                renders.add(context.darkTheme());
                return html;
            }
        };
        AtomicReference<EditorController> controller = new AtomicReference<>();
        AtomicReference<com.example.jylos.ui.web.JavaFxHtmlView> webView = new AtomicReference<>();
        AtomicReference<Stage> stage = new AtomicReference<>();
        runFx(() -> {
            EditorController editor = new EditorController();
            com.example.jylos.ui.web.JavaFxHtmlView view = new com.example.jylos.ui.web.JavaFxHtmlView();
            VBox pane = new VBox(view);
            set(editor, "previewWebView", view);
            set(editor, "previewPane", pane);
            Stage window = new Stage();
            window.setScene(new Scene(pane, 600, 400));
            window.show();
            Note note = new Note("A", "Texto de A");
            note.setId("a.md");
            set(editor, "currentNote", note);
            editor.registerPreviewEnhancer("recorder", recorder);
            controller.set(editor);
            webView.set(view);
            stage.set(window);
        });
        try {
            runFx(() -> controller.get().refreshPreview(true));
            assertTrue(waitFor(() -> renders.contains(true)), "drawn dark: " + renders);
            // The render is done before the page is loaded: wait for the page's own background.
            assertTrue(waitFor(() -> painted(background(webView.get()))), "the dark page is shown");
            String dark = background(webView.get());

            int before = renders.size();
            runFx(() -> controller.get().registerPreviewEnhancer("otro", new PreviewEnhancer() { }));
            assertTrue(waitFor(() -> renders.size() > before), "drawn again");
            assertTrue(waitFor(() -> dark.equals(background(webView.get()))),
                    "the page keeps the dark background: " + dark + " vs " + background(webView.get()));
            assertEquals(Boolean.TRUE, renders.get(renders.size() - 1), "renders: " + renders);
        } finally {
            runFx(() -> {
                controller.get().teardown();
                stage.get().close();
            });
        }
    }

    private static String background(com.example.jylos.ui.web.JavaFxHtmlView view) {
        AtomicReference<String> color = new AtomicReference<>("");
        try {
            runFx(() -> {
                Object value = view.evaluate(
                        "document.body ? getComputedStyle(document.body).backgroundColor : ''");
                color.set(String.valueOf(value));
            });
        } catch (Exception e) {
            return "<unreadable>";
        }
        return color.get();
    }

    private static boolean painted(String color) {
        return color.startsWith("rgb(");
    }

    private static boolean waitFor(java.util.function.BooleanSupplier condition) throws InterruptedException {
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
