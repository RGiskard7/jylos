package com.example.jylos.ui.theme;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.ResourceBundle;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.kordamp.ikonli.javafx.FontIcon;

import com.example.jylos.data.models.Note;
import com.example.jylos.tests.FxTestSupport;
import com.example.jylos.ui.components.SideDock;
import com.example.jylos.ui.controller.EditorController;

import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Bounds;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;

/**
 * The editor's bars line up with the rest of the window: its header is as tall as the side
 * panel's header and the notes list's header next to it, all three compact (little margin
 * above and below their buttons, as Obsidian's; the right panel's row of tabs too, and so is the side panel's filter row under its
 * header), and the format bar rests on the bottom edge
 * with no gap below.
 */
class EditorChromeAlignmentTest {

    @Test
    void theHeadersAreCompactAndEquallyTallAndTheFormatBarReachesTheBottom() throws Exception {
        Assumptions.assumeTrue(FxTestSupport.isFxRuntimeAvailable());
        for (String theme : new String[] {"modern-theme.css", "dark-theme.css"}) {
            List<Double> m = measure(theme);
            double editorHeader = m.get(0), sideHeader = m.get(1), formatBottom = m.get(2), editorBottom = m.get(3);
            double notesHeader = m.get(4);
            assertTrue(sideHeader > 30, theme + ": side panel header not laid out: " + sideHeader);
            assertTrue(sideHeader <= 38, theme + ": side panel header too tall: " + sideHeader);
            assertEquals(sideHeader, editorHeader, theme + ": editor header vs side panel header");
            assertEquals(sideHeader, notesHeader, theme + ": notes list header vs side panel header");
            assertEquals(sideHeader, m.get(6), theme + ": right panel tabs row vs side panel header");
            double filterRow = m.get(5);
            assertTrue(filterRow > 30 && filterRow <= 38, theme + ": side panel filter row not compact: " + filterRow);
            assertTrue(formatBottom > editorBottom - 100, theme + ": format bar not at the bottom: " + formatBottom);
            assertEquals(editorBottom, formatBottom, theme + ": gap under the format bar");
        }
    }

    /** [editor header height, side panel header height, format bar bottom, editor bottom, notes list header height, side panel filter row height, right panel tabs row height]. */
    private static List<Double> measure(String theme) throws Exception {
        AtomicReference<List<Double>> result = new AtomicReference<>();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        CountDownLatch done = new CountDownLatch(1);
        Platform.runLater(() -> {
            try {
                ResourceBundle bundle = ResourceBundle.getBundle("com.example.jylos.i18n.messages",
                        Locale.forLanguageTag("es"));
                FXMLLoader editorLoader = new FXMLLoader(
                        EditorChromeAlignmentTest.class.getResource("/com/example/jylos/ui/view/EditorView.fxml"), bundle);
                Parent editor = editorLoader.load();
                ((EditorController) editorLoader.getController()).loadNote(new Note("Carpeta/Nota.md", "Nota", "texto"));
                Parent sidebar = new FXMLLoader(
                        EditorChromeAlignmentTest.class.getResource("/com/example/jylos/ui/view/SidebarView.fxml"), bundle)
                        .load();
                Parent notes = new FXMLLoader(
                        EditorChromeAlignmentTest.class.getResource("/com/example/jylos/ui/view/NotesListView.fxml"), bundle)
                        .load();
                SideDock right = new SideDock();
                right.addView("links", "Links", new FontIcon("fth-link"), new Label("links"));
                HBox window = new HBox(sidebar, notes, editor, right);
                HBox.setHgrow(editor, Priority.ALWAYS);
                Scene scene = new Scene(window, 1400, 900);
                scene.getStylesheets().add(EditorChromeAlignmentTest.class
                        .getResource("/com/example/jylos/ui/css/" + theme).toExternalForm());
                window.applyCss();
                window.layout();
                List<Double> out = new ArrayList<>();
                out.add(editor.lookup(".editor-header").getLayoutBounds().getHeight());
                out.add(sidebar.lookup(".sidebar-nav-bar").getLayoutBounds().getHeight());
                Node format = editor.lookup(".format-toolbar-container");
                out.add(sceneBounds(format).getMaxY());
                out.add(sceneBounds(editor).getMaxY());
                out.add(notes.lookup(".notes-header").getLayoutBounds().getHeight());
                out.add(sidebar.lookup(".sidebar-toolbar").getLayoutBounds().getHeight());
                out.add(right.lookup(".side-dock-tabs").getLayoutBounds().getHeight());
                result.set(out);
            } catch (Throwable t) {
                failure.set(t);
            } finally {
                done.countDown();
            }
        });
        assertTrue(done.await(30, TimeUnit.SECONDS), "never completed on the FX thread");
        if (failure.get() != null) {
            throw new AssertionError(failure.get());
        }
        return result.get();
    }

    private static Bounds sceneBounds(Node node) {
        return node.localToScene(node.getLayoutBounds());
    }
}
