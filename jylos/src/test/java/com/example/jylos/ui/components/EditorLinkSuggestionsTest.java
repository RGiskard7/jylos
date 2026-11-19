package com.example.jylos.ui.components;

import static com.example.jylos.tests.FxTestSupport.onFx;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import com.example.jylos.service.LinkSuggestion;
import com.example.jylos.tests.FxTestSupport;

import javafx.scene.Scene;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import javafx.stage.Window;

/**
 * The [[ suggestions in the real editor page, with what Java gives it: typing a link lists the
 * notes that match (each one with its folder), and the page asks Java for the headings of the
 * note a link names.
 */
class EditorLinkSuggestionsTest {

    @AfterEach
    void closeWindows() throws Exception {
        if (FxTestSupport.isFxRuntimeAvailable()) {
            onFx(() -> {
                new ArrayList<>(Window.getWindows()).forEach(Window::hide);
                return null;
            });
        }
    }

    @Test
    void typingALinkListsTheNotesJavaGaveAndAsksJavaForHeadings() throws Exception {
        Assumptions.assumeTrue(FxTestSupport.isFxRuntimeAvailable());
        CountDownLatch ready = new CountDownLatch(1);
        MarkdownEditorView editor = onFx(() -> {
            MarkdownEditorView view = new MarkdownEditorView();
            view.setEditable(true);
            Stage stage = new Stage();
            stage.setScene(new Scene(new StackPane(view), 800, 600));
            stage.show();
            view.whenReady(ready::countDown);
            return view;
        });
        assertTrue(ready.await(30, TimeUnit.SECONDS), "the editor never loaded");

        String headings = onFx(() -> {
            editor.setLinkSuggestions(List.of(new LinkSuggestion("Reunión", "Trabajo", "Trabajo/Reunión", List.of()),
                    new LinkSuggestion("Reunión", "Personal", "Personal/Reunión", List.of()),
                    new LinkSuggestion("Plan", "", "Plan", List.of("Planificación"))));
            editor.setLinkHeadingsProvider(link -> "Plan".equals(link) ? List.of("Fase 1", "Fase 2") : List.of());
            editor.setText("");
            editor.requestFocus();
            editor.page().evaluate("document.querySelector('.cm-content').focus()");
            editor.page().evaluate("document.execCommand('insertText', false, '[[reunion')");
            return String.valueOf(editor.page().evaluate("window.javaEditor.headingsOf('Plan')"));
        });
        assertEquals("[\"Fase 1\",\"Fase 2\"]", headings);

        String listed = "";
        for (int i = 0; i < 50 && listed.isEmpty(); i++) {
            Thread.sleep(100);
            listed = onFx(() -> String.valueOf(editor.page().evaluate(
                    "Array.from(document.querySelectorAll('.cm-tooltip-autocomplete li')).map(li =>"
                            + " li.querySelector('.cm-completionLabel').textContent + ' ('"
                            + " + (li.querySelector('.cm-completionDetail') || { textContent: '' }).textContent + ')')"
                            + ".join(', ')")));
        }
        assertEquals("Reunión (Personal), Reunión (Trabajo)", listed);
    }
}
