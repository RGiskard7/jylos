package com.example.jylos.ui.controller;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static com.example.jylos.tests.FxTestSupport.onFx;

import java.net.URL;
import java.util.Locale;
import java.util.ResourceBundle;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import com.example.jylos.data.models.Note;
import com.example.jylos.tests.FxTestSupport;
import com.example.jylos.ui.web.HtmlView;

import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

/**
 * Following a link to a heading of another note in the reading view lands on that heading,
 * even though the note's page is still being rendered when the jump is asked for: the jump
 * waits for that note's page instead of running on the previous one and being lost.
 */
class PreviewAnchorNavigationTest {

    @Test
    void aJumpAskedBeforeTheNewPageLoadsLandsOnTheHeading() throws Exception {
        Assumptions.assumeTrue(FxTestSupport.isFxRuntimeAvailable());
        StringBuilder longNote = new StringBuilder("# Larga\n");
        for (int i = 1; i <= 80; i++) {
            longNote.append("\nRelleno ").append(i).append('\n');
        }
        longNote.append("\n## Detalle\n\nAquí.\n");
        for (int i = 1; i <= 40; i++) {   // room below, so the heading can reach the top
            longNote.append("\nDespués ").append(i).append('\n');
        }

        AtomicReference<EditorController> controller = new AtomicReference<>();
        AtomicReference<Stage> stage = new AtomicReference<>();
        onFx(() -> {
            URL fxml = getClass().getResource("/com/example/jylos/ui/view/EditorView.fxml");
            FXMLLoader loader = new FXMLLoader(fxml, ResourceBundle.getBundle("com.example.jylos.i18n.messages",
                    Locale.forLanguageTag("es")));
            Parent root = loader.load();
            EditorController editor = loader.getController();
            editor.getPreviewPane().setVisible(true);
            editor.getPreviewPane().setManaged(true);
            Stage window = new Stage();
            window.setScene(new Scene(root, 900, 600));
            window.show();
            editor.loadNote(new Note("Corta.md", "Corta", "Hola corta"));
            editor.refreshPreview(false);
            controller.set(editor);
            stage.set(window);
            return null;
        });
        waitFor(() -> bodyText(controller.get()).contains("Hola corta"));

        onFx(() -> {
            EditorController editor = controller.get();
            editor.loadNote(new Note("Larga.md", "Larga", longNote.toString()));
            editor.refreshPreview(false);
            editor.navigateToHeading(longNote.indexOf("## Detalle"), "detalle");   // page not loaded yet
            return null;
        });
        waitFor(() -> bodyText(controller.get()).contains("Después 40"));
        Thread.sleep(400);

        double top = onFx(() -> ((Number) webView(controller.get()).evaluate(
                "document.getElementById('detalle').getBoundingClientRect().top")).doubleValue());
        onFx(() -> {
            stage.get().close();
            return null;
        });
        assertTrue(Math.abs(top) < 5, "heading should be at the top of the view, is at " + top);
    }

    private static HtmlView webView(EditorController editor) {
        return editor.getPreviewWebView();
    }

    private static String bodyText(EditorController editor) {
        try {
            return onFx(() -> {
                Object text = webView(editor).evaluate(
                        "document.body ? document.body.textContent : ''");
                return text != null ? text.toString() : "";
            });
        } catch (Exception e) {
            return "";
        }
    }

    private static void waitFor(BooleanSupplier condition) throws Exception {
        long deadline = System.currentTimeMillis() + 20_000;
        while (!condition.getAsBoolean()) {
            if (System.currentTimeMillis() > deadline) {
                throw new AssertionError("condition not reached in time");
            }
            Thread.sleep(50);
        }
    }
}
