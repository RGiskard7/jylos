package com.example.jylos.ui.components;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import com.example.jylos.tests.FxTestSupport;

import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;

/**
 * Live Preview draws math with the bundled KaTeX, loaded into the editor only once a note
 * has math, and a tag clicked in the editor reaches the app.
 */
class LivePreviewMathAndTagsTest {

    @Test
    void mathIsDrawnOnceANoteHasMathAndKatexIsNotLoadedBefore() throws Exception {
        Assumptions.assumeTrue(FxTestSupport.isFxRuntimeAvailable());
        AtomicReference<MarkdownEditorView> editor = new AtomicReference<>();
        AtomicReference<Stage> stage = new AtomicReference<>();
        onFx(() -> {
            MarkdownEditorView view = new MarkdownEditorView();
            Stage window = new Stage();
            window.setScene(new Scene(new StackPane(view), 800, 600));
            window.show();
            view.setText("Sin fórmulas");
            editor.set(view);
            stage.set(window);
            return null;
        });
        try {
            waitFor(() -> js(editor.get(), "(document.querySelector('.cm-content') || {}).textContent || ''")
                    .contains("Sin fórmulas"));
            assertEquals("undefined", js(editor.get(), "typeof window.katex"), "no KaTeX for a note without math");

            onFx(() -> {
                editor.get().setText("Área $a^2$ aquí\n\n$$\n\\\\frac{1}{2}\n$$\n");
                return null;
            });
            waitFor(() -> "2".equals(js(editor.get(), "String(document.querySelectorAll('.cm-live-math .katex').length)")));
        } finally {
            onFx(() -> {
                stage.get().close();
                return null;
            });
        }
    }

    @Test
    void aTagFromTheEditorReachesTheHandler() throws Exception {
        Assumptions.assumeTrue(FxTestSupport.isFxRuntimeAvailable());
        List<String> tags = new ArrayList<>();
        AtomicReference<MarkdownEditorView> editor = new AtomicReference<>();
        AtomicReference<Stage> stage = new AtomicReference<>();
        onFx(() -> {
            MarkdownEditorView view = new MarkdownEditorView();
            view.setTagHandler(tags::add);
            Stage window = new Stage();
            window.setScene(new Scene(new StackPane(view), 800, 600));
            window.show();
            view.setText("Con #proyecto");
            editor.set(view);
            stage.set(window);
            return null;
        });
        try {
            waitFor(() -> "1".equals(js(editor.get(), "String(document.querySelectorAll('.cm-live-tag').length)")));
            js(editor.get(), "document.querySelector('.cm-live-tag').dispatchEvent("
                    + "new MouseEvent('mousedown', {bubbles: true, metaKey: true}))");
            waitFor(() -> tags.equals(List.of("proyecto")));
        } finally {
            onFx(() -> {
                stage.get().close();
                return null;
            });
        }
    }

    /**
     * Bold text and headings must really be bold in the JavaFX WebView: its WebKit drops a
     * font weight that is not a multiple of 100 (750, 725) and falls back to normal text.
     */
    @Test
    void boldTextAndHeadingsAreBoldInTheRealWebView() throws Exception {
        Assumptions.assumeTrue(FxTestSupport.isFxRuntimeAvailable());
        AtomicReference<MarkdownEditorView> editor = new AtomicReference<>();
        AtomicReference<Stage> stage = new AtomicReference<>();
        onFx(() -> {
            MarkdownEditorView view = new MarkdownEditorView();
            Stage window = new Stage();
            window.setScene(new Scene(new StackPane(view), 800, 600));
            window.show();
            view.setText("Texto **negrita** fin\n\n# Título uno\n\n## Título dos");
            editor.set(view);
            stage.set(window);
            return null;
        });
        try {
            waitFor(() -> js(editor.get(), "(document.querySelector('.cm-content') || {}).textContent || ''")
                    .contains("Título dos"));
            String weights = js(editor.get(), "["
                    + "Array.from(document.querySelectorAll('.cm-line span')).find(e => e.textContent === 'negrita'),"
                    + "document.querySelector('.cm-live-atxheading1'),"
                    + "document.querySelector('.cm-live-atxheading2')"
                    + "].map(e => e ? getComputedStyle(e).fontWeight : 'missing').join(',')");
            for (String weight : weights.split(",")) {
                assertTrue(weight.matches("\\d+") && Integer.parseInt(weight) >= 600,
                        "bold, heading 1, heading 2 weights: " + weights);
            }
        } finally {
            onFx(() -> {
                stage.get().close();
                return null;
            });
        }
    }

    /**
     * With the cursor in an element, that element shows its Markdown. CodeMirror's own focus
     * tracking asks document.hasFocus(), which the JavaFX WebView does not answer as a browser
     * does, so the editor must notice its focus from the focus events themselves.
     */
    @Test
    void theElementUnderTheCursorShowsItsMarkdownInTheRealWebView() throws Exception {
        Assumptions.assumeTrue(FxTestSupport.isFxRuntimeAvailable());
        AtomicReference<MarkdownEditorView> editor = new AtomicReference<>();
        AtomicReference<Stage> stage = new AtomicReference<>();
        onFx(() -> {
            MarkdownEditorView view = new MarkdownEditorView();
            view.setEditable(true);
            Stage window = new Stage();
            window.setScene(new Scene(new StackPane(view), 800, 600));
            window.show();
            view.setText("Texto **negrita** fin y *cursiva*");
            editor.set(view);
            stage.set(window);
            return null;
        });
        try {
            String line = "(document.querySelector('.cm-line') || {}).textContent || ''";
            waitFor(() -> js(editor.get(), line).contains("negrita"));
            assertEquals("Texto negrita fin y cursiva", js(editor.get(), line), "everything rendered before editing");

            js(editor.get(), "window.JylosEditor.scrollToOffset(9)");
            waitFor(() -> js(editor.get(), line).contains("**negrita**"));
            assertEquals("Texto **negrita** fin y cursiva", js(editor.get(), line),
                    "only the element under the cursor shows its marks");
        } finally {
            onFx(() -> {
                stage.get().close();
                return null;
            });
        }
    }

    /**
     * A click below a rendered formula lands on the text that is there. A formula grows once
     * its fonts load; if the editor kept the height it measured before, every click below it
     * would land on the wrong line.
     */
    @Test
    void aClickBelowAFormulaLandsWhereItIsAimed() throws Exception {
        Assumptions.assumeTrue(FxTestSupport.isFxRuntimeAvailable());
        String doc = "$$\n\\int_0^1 x^2\\,dx = \\frac{1}{3}\n$$\n\nTexto **negrita** fin";
        AtomicReference<MarkdownEditorView> editor = new AtomicReference<>();
        AtomicReference<Stage> stage = new AtomicReference<>();
        onFx(() -> {
            MarkdownEditorView view = new MarkdownEditorView();
            Stage window = new Stage();
            window.setScene(new Scene(new StackPane(view), 800, 600));
            window.show();
            view.setText(doc);
            editor.set(view);
            stage.set(window);
            return null;
        });
        try {
            waitFor(() -> "1".equals(js(editor.get(), "String(document.querySelectorAll('.cm-live-math-block .katex').length)")));
            Thread.sleep(1500);   // fonts load, the formula reaches its final height
            String position = js(editor.get(), "(function(){"
                    + "var span = Array.from(document.querySelectorAll('.cm-line span')).find(e => e.textContent === 'negrita');"
                    + "var r = span.getBoundingClientRect();"
                    + "return String(window.JylosEditor.positionAt(r.left + r.width / 2, r.top + r.height / 2));})()");
            int start = doc.indexOf("negrita");
            int at = Integer.parseInt(position);
            assertTrue(at >= start && at <= start + "negrita".length(),
                    "a click on 'negrita' (" + start + "-" + (start + 7) + ") maps to " + at);
        } finally {
            onFx(() -> {
                stage.get().close();
                return null;
            });
        }
    }

    private static String js(MarkdownEditorView view, String script) {
        try {
            return onFx(() -> {
                Object value = view.getWebView().getEngine().executeScript(script);
                return value != null ? value.toString() : "null";
            });
        } catch (Exception e) {
            return "error";
        }
    }

    private static void waitFor(BooleanSupplier condition) throws Exception {
        long deadline = System.currentTimeMillis() + 30_000;
        while (!condition.getAsBoolean()) {
            if (System.currentTimeMillis() > deadline) {
                throw new AssertionError("condition not reached in time");
            }
            Thread.sleep(50);
        }
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
