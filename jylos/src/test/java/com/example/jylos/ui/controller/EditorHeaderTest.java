package com.example.jylos.ui.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import com.example.jylos.data.models.Note;
import com.example.jylos.tests.FxTestSupport;

import javafx.application.Platform;
import javafx.event.Event;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Pane;

/**
 * The editor header takes one row: the note's folders appear in the title row (no separate
 * path row), the save state lives in the status bar and the tab (no dot next to the title),
 * the properties section only shows when the note has properties, and the format bar can be
 * turned off.
 */
class EditorHeaderTest {

    @Test
    void theFolderPathSitsInTheTitleRowAndThereIsNoSeparatePathRow() throws Exception {
        Assumptions.assumeTrue(FxTestSupport.isFxRuntimeAvailable());

        List<String> seen = onFx(() -> {
            Fixture f = new Fixture();
            List<String> out = new ArrayList<>();
            f.controller.loadNote(new Note("000 - Gastos/Sub/Nota.md", "Nota", "x"));
            f.layout();
            Label path = f.headerPath();
            out.add(path.isVisible() && path.isManaged() ? path.getText() : "hidden");
            out.add(String.valueOf(f.shown("editor-path-bar")));
            out.add(String.valueOf(f.shown("save-indicator-dot")));
            f.controller.loadNote(new Note("Raiz.md", "Raiz", "x"));
            f.layout();
            out.add(f.headerPath().isVisible() ? f.headerPath().getText() : "hidden");
            return out;
        });

        assertEquals(List.of("000 - Gastos / Sub /", "false", "false", "hidden"), seen);
    }

    @Test
    void clickingThePathRevealsTheNoteOnlyWhenThereIsSomewhereToRevealIt() throws Exception {
        Assumptions.assumeTrue(FxTestSupport.isFxRuntimeAvailable());
        List<String> calls = new ArrayList<>();

        List<Boolean> clickable = onFx(() -> {
            Fixture f = new Fixture();
            f.controller.loadNote(new Note("000 - Gastos/Nota.md", "Nota", "x"));
            f.layout();
            boolean before = f.headerPath().getStyleClass().contains("note-path-link");
            click(f.headerPath());
            f.controller.setPathRevealAction(() -> calls.add("reveal"));
            boolean after = f.headerPath().getStyleClass().contains("note-path-link");
            click(f.headerPath());
            f.controller.setPathRevealAction(null);
            click(f.headerPath());
            return List.of(before, after);
        });

        assertEquals(List.of(false, true), clickable);
        assertEquals(List.of("reveal"), calls);
    }

    @Test
    void propertiesShowOnlyWhenTheNoteHasSomeAndCanStillBeAdded() throws Exception {
        Assumptions.assumeTrue(FxTestSupport.isFxRuntimeAvailable());

        List<String> seen = onFx(() -> {
            Fixture f = new Fixture();
            List<String> out = new ArrayList<>();
            f.controller.loadNote(new Note("a.md", "A", "x"));
            f.layout();
            out.add("empty note: section " + f.shown("properties-panel") + ", add " + f.shownById("addPropertyHeaderBtn"));
            Note withProps = new Note("b.md", "B", "x");
            withProps.setCustomProperties(Map.of("estado", "borrador"));
            f.controller.loadNote(withProps);
            f.layout();
            out.add("with properties: section " + f.shown("properties-panel"));
            return out;
        });

        assertEquals(List.of("empty note: section false, add true", "with properties: section true"), seen);
    }

    @Test
    void theFormatBarCanBeTurnedOff() throws Exception {
        Assumptions.assumeTrue(FxTestSupport.isFxRuntimeAvailable());

        List<Boolean> seen = onFx(() -> {
            Fixture f = new Fixture();
            f.controller.loadNote(new Note("a.md", "A", "x"));
            f.layout();
            boolean byDefault = f.shown("format-toolbar-container");
            f.controller.setFormatToolbarVisible(false);
            f.layout();
            boolean off = f.shown("format-toolbar-container");
            f.controller.loadNote(new Note("b.md", "B", "x"));
            f.layout();
            boolean stillOff = f.shown("format-toolbar-container");
            f.controller.setFormatToolbarVisible(true);
            f.layout();
            return List.of(byDefault, off, stillOff, f.shown("format-toolbar-container"));
        });

        assertEquals(List.of(true, false, false, true), seen);
    }

    private static final class Fixture {
        final EditorController controller;
        final Parent root;
        final Pane host;

        Fixture() throws Exception {
            URL fxml = EditorHeaderTest.class.getResource("/com/example/jylos/ui/view/EditorView.fxml");
            ResourceBundle bundle = ResourceBundle.getBundle("com.example.jylos.i18n.messages",
                    Locale.forLanguageTag("es"));
            FXMLLoader loader = new FXMLLoader(fxml, bundle);
            root = loader.load();
            controller = loader.getController();
            host = new Pane(root);
            host.resize(1000, 700);
            new Scene(host, 1000, 700);
        }

        void layout() {
            host.applyCss();
            host.layout();
        }

        Label headerPath() {
            Node header = root.lookup(".editor-header");
            for (Node node : header.lookupAll(".note-path")) {
                if (node instanceof Label label) {
                    return label;
                }
            }
            throw new AssertionError("no folder path in the title row");
        }

        boolean shown(String styleClass) {
            for (Node node : root.lookupAll("." + styleClass)) {
                if (node.isVisible() && node.isManaged() && ancestorsShown(node)) {
                    return true;
                }
            }
            return false;
        }

        boolean shownById(String id) {
            Node node = root.lookup("#" + id);
            return node != null && node.isVisible() && node.isManaged() && ancestorsShown(node);
        }

        private boolean ancestorsShown(Node node) {
            for (Node n = node.getParent(); n != null && n != host; n = n.getParent()) {
                if (!n.isVisible() || !n.isManaged()) {
                    return false;
                }
            }
            return true;
        }
    }

    private static void click(Node node) {
        Event.fireEvent(node, new MouseEvent(MouseEvent.MOUSE_CLICKED, 1, 1, 1, 1, MouseButton.PRIMARY, 1,
                false, false, false, false, false, false, false, false, false, true, null));
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
        assertTrue(done.await(60, TimeUnit.SECONDS), "test never completed on the FX thread");
        if (failure.get() != null) {
            throw new AssertionError(failure.get());
        }
        return result.get();
    }
}
