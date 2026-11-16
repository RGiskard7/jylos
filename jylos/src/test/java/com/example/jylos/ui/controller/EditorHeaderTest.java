package com.example.jylos.ui.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static com.example.jylos.tests.FxTestSupport.onFx;

import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.ResourceBundle;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import com.example.jylos.data.models.Note;
import com.example.jylos.tests.FxTestSupport;

import javafx.event.Event;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ToggleButton;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Background;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;

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
    void thePropertiesButtonShowsForEveryNoteAndThePanelWhenOpened() throws Exception {
        Assumptions.assumeTrue(FxTestSupport.isFxRuntimeAvailable());

        List<String> seen = onFx(() -> {
            Fixture f = new Fixture();
            List<String> out = new ArrayList<>();
            f.controller.loadNote(new Note("a.md", "A", "x"));
            f.layout();
            out.add("empty note: button " + f.shownById("togglePropertiesBtn") + ", panel " + f.shown("properties-panel"));
            ((ToggleButton) f.root.lookup("#togglePropertiesBtn")).fire();
            f.layout();
            out.add("opened: panel " + f.shown("properties-panel") + ", add " + f.shownById("addPropertyBtn"));
            Note withProps = new Note("b.md", "B", "x");
            withProps.setCustomProperties(Map.of("estado", "borrador"));
            f.controller.loadNote(withProps);
            f.layout();
            out.add("with properties: panel " + f.shown("properties-panel") + ", add below the rows " + f.addBelowRows());
            return out;
        });

        // One way to add: the panel's own "+ Add property" line, under the rows.
        assertEquals(List.of("empty note: button true, panel false",
                "opened: panel true, add true",
                "with properties: panel true, add below the rows true"), seen);
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

    @Test
    void thePropertiesPanelLooksLikeTheRestOfTheEditor() throws Exception {
        Assumptions.assumeTrue(FxTestSupport.isFxRuntimeAvailable());

        for (String theme : List.of("modern-theme.css", "dark-theme.css")) {
            List<String> offenders = onFx(() -> {
                Fixture f = new Fixture();
                f.host.getScene().getStylesheets().add(
                        EditorHeaderTest.class.getResource("/com/example/jylos/ui/css/" + theme).toExternalForm());
                Note withProps = new Note("b.md", "B", "x");
                withProps.setCustomProperties(Map.of("estado", "borrador"));
                f.controller.loadNote(withProps);
                f.layout();
                List<String> out = new ArrayList<>();
                Region panel = (Region) f.root.lookup(".properties-panel");
                if (painted(panel.getBackground())) {
                    out.add("panel has its own background band");
                }
                if (panel.getBorder() != null && !panel.getBorder().getStrokes().isEmpty()
                        && panel.getBorder().getStrokes().stream().anyMatch(st -> st.getWidths().getBottom() > 0
                                && !(st.getBottomStroke() instanceof Color c && c.getOpacity() == 0))) {
                    out.add("panel has a border line");
                }
                Button add = (Button) f.root.lookup("#addPropertyBtn");
                if (add.getBorder() != null && add.getBorder().getStrokes().stream()
                        .anyMatch(st -> st.getWidths().getTop() > 0
                                && !(st.getTopStroke() instanceof Color c && c.getOpacity() == 0))) {
                    out.add("add button has a border");
                }
                for (Node node : f.root.lookupAll(".property-value")) {
                    if (node instanceof Region r && painted(r.getBackground())) {
                        out.add("value field is a filled box at rest");
                        break;
                    }
                }
                return out;
            });
            assertEquals(List.of(), offenders, theme);
        }
    }

    private static boolean painted(Background background) {
        return background != null && background.getFills().stream()
                .anyMatch(fill -> !(fill.getFill() instanceof Color c && c.getOpacity() == 0));
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

        /** The panel's add button is shown and comes after the property rows. */
        boolean addBelowRows() {
            Node add = root.lookup("#addPropertyBtn");
            Node rows = root.lookup(".properties-content");
            if (add == null || rows == null || !add.isVisible() || !add.isManaged() || !ancestorsShown(add)) {
                return false;
            }
            return add.localToScene(add.getBoundsInLocal()).getMinY()
                    >= rows.localToScene(rows.getBoundsInLocal()).getMaxY() - 0.5;
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
}
