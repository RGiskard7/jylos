package com.example.jylos.ui.controller;

import static com.example.jylos.tests.FxTestSupport.onFx;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URL;
import java.util.Locale;
import java.util.ResourceBundle;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.example.jylos.data.models.Note;
import com.example.jylos.tests.FxTestSupport;
import com.example.jylos.ui.components.NotePosition;
import com.example.jylos.ui.web.HtmlView;

import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

/**
 * Where a note is in the editor and in the reading view, read and shown again: a note shown
 * where it was left goes back there, one shown with no position starts at its top, and a
 * link to a heading still lands on the heading.
 */
class NotePositionTest {

    private static final String LONG = long_("Larga");
    private static final String OTHER = long_("Otra");

    private final AtomicReference<EditorController> controller = new AtomicReference<>();
    private final AtomicReference<Stage> stage = new AtomicReference<>();

    private static String long_(String name) {
        StringBuilder text = new StringBuilder("# " + name + "\n");
        for (int i = 1; i <= 80; i++) {
            text.append("\n").append(name).append(" relleno ").append(i).append('\n');
        }
        text.append("\n## Detalle\n\nAquí.\n");
        for (int i = 1; i <= 40; i++) {
            text.append("\n").append(name).append(" después ").append(i).append('\n');
        }
        return text.toString();
    }

    @BeforeEach
    void showEditor() throws Exception {
        Assumptions.assumeTrue(FxTestSupport.isFxRuntimeAvailable());
        onFx(() -> {
            URL fxml = getClass().getResource("/com/example/jylos/ui/view/EditorView.fxml");
            FXMLLoader loader = new FXMLLoader(fxml, ResourceBundle.getBundle("com.example.jylos.i18n.messages",
                    Locale.forLanguageTag("es")));
            Parent root = loader.load();
            EditorController editor = loader.getController();
            editor.setViewMode(EditorController.ViewMode.SPLIT); // the editor and the reading view
            Stage window = new Stage();
            window.setScene(new Scene(root, 900, 600));
            window.show();
            controller.set(editor);
            stage.set(window);
            return null;
        });
    }

    @AfterEach
    void close() throws Exception {
        if (stage.get() != null) {
            onFx(() -> {
                stage.get().close();
                return null;
            });
        }
    }

    @Test
    void aNoteShownWhereItWasLeftGoesBackThereAndOneWithNoPositionStartsAtItsTop() throws Exception {
        NotePosition none = onFx(() -> controller.get().positions().where());
        show("Larga.md", LONG, null);
        onFx(() -> webView().evaluate("window.scrollTo(0, 1500)"));
        Thread.sleep(200);
        NotePosition left = onFx(() -> controller.get().positions().where());

        show("Otra.md", OTHER, null);
        double otherAt = readingY();
        show("Larga.md", LONG, left);
        double backAt = readingY();

        assertNull(none, "no note, no position");
        assertNotNull(left);
        assertEquals("Larga.md", left.noteId());
        assertEquals(1500, left.readingY(), 5);
        assertEquals(0, otherAt, 0.5);
        assertEquals(1500, backAt, 5);
    }

    @Test
    void aLinkToAHeadingLandsOnTheHeadingEvenWithAPosition() throws Exception {
        show("Larga.md", LONG, null);
        onFx(() -> webView().evaluate("window.scrollTo(0, 1500)"));
        Thread.sleep(200);
        NotePosition left = onFx(() -> controller.get().positions().where());
        show("Otra.md", OTHER, null);

        onFx(() -> {
            EditorController editor = controller.get();
            editor.loadNote(new Note("Larga.md", "Larga", LONG));
            editor.positions().showAt(left);
            editor.refreshPreview(false);
            editor.navigateToHeading(LONG.indexOf("## Detalle"), "detalle");
            return null;
        });
        waitFor(() -> bodyText().contains("Larga después 40"));
        Thread.sleep(400);
        double heading = onFx(() -> ((Number) webView().evaluate(
                "document.getElementById('detalle').getBoundingClientRect().top")).doubleValue());

        assertTrue(Math.abs(heading) < 5, "the heading should be at the top of the view, is at " + heading);
    }

    /** Loads the note and shows it at {@code position}, as opening it in its tab does, and waits for its page. */
    private void show(String id, String content, NotePosition position) throws Exception {
        String title = id.replace(".md", "");
        onFx(() -> {
            EditorController editor = controller.get();
            editor.loadNote(new Note(id, title, content));
            editor.positions().showAt(position);
            editor.refreshPreview(false);
            return null;
        });
        waitFor(() -> bodyText().contains(title + " después 40"));
        Thread.sleep(400);
    }

    private double readingY() throws Exception {
        return onFx(() -> ((Number) webView().evaluate("window.scrollY || 0")).doubleValue());
    }

    private HtmlView webView() {
        return controller.get().getPreviewWebView();
    }

    private String bodyText() {
        try {
            return onFx(() -> {
                Object text = webView().evaluate("document.body ? document.body.textContent : ''");
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
