package com.example.jylos.ui.controller;

import static com.example.jylos.tests.FxTestSupport.onFx;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.reflect.Field;
import java.net.URL;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.ResourceBundle;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import com.example.jylos.data.dao.filesystem.FrontmatterHandler;
import com.example.jylos.data.models.Note;
import com.example.jylos.tests.FxTestSupport;

import javafx.event.Event;
import javafx.event.EventType;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Bounds;
import javafx.geometry.Point2D;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Pane;

/**
 * The properties panel edits the note's properties: a changed value marks the note modified
 * and reaches the note, a removed property leaves it, and in the reading view a property's
 * {@code [[link]]} reads as in the note (its alias, or its note and heading) and opens the note
 * its path names, at that heading.
 */
class PropertiesPanelTest {

    @Test
    void valuesAreEditedRemovedAndTheirLinksOpen() throws Exception {
        Assumptions.assumeTrue(FxTestSupport.isFxRuntimeAvailable());

        List<String> seen = onFx(() -> {
            URL fxml = PropertiesPanelTest.class.getResource("/com/example/jylos/ui/view/EditorView.fxml");
            FXMLLoader loader = new FXMLLoader(fxml,
                    ResourceBundle.getBundle("com.example.jylos.i18n.messages", Locale.forLanguageTag("es")));
            Parent root = loader.load();
            EditorController controller = loader.getController();
            new Scene(new Pane(root), 1000, 700);
            List<String> opened = new ArrayList<>();
            Field links = EditorController.class.getDeclaredField("wikiLinkHandler");
            links.setAccessible(true);
            links.set(controller, (EditorController.WikiLinkHandler) (title, newTab) -> opened.add(title));

            Note note = new Note("a.md", "A", "x");
            Map<String, String> props = new LinkedHashMap<>();
            props.put("estado", "borrador");
            props.put("borrar", "sí");
            props.put("ver", "[[Carpeta/Destino|destino]]");
            props.put("tema", "Ver [[Destino#Uso]] y más");
            props.put("aqui", "[[Destino#Uso|aquí]]");
            note.setCustomProperties(props);
            controller.loadNote(note);

            List<String> out = new ArrayList<>();
            Node section = root.lookup("#propertiesSection");
            out.add("open at first: " + section.isVisible());
            ((ToggleButton) root.lookup("#togglePropertiesBtn")).fire();
            out.add("open after the button: " + section.isVisible());
            out.add("modified at first: " + controller.isModified());
            TextField estado = (TextField) row(root, "estado").lookup(".property-value");
            estado.setText("final");
            out.add("modified after editing: " + controller.isModified());
            ((Button) row(root, "borrar").lookup(".property-remove-btn")).fire();
            out.add("rows: " + rowKeys(root));

            controller.setReadOnlyView(true);
            out.add("note: " + note.getCustomProperties());
            for (String key : List.of("ver", "tema", "aqui")) {
                Hyperlink link = (Hyperlink) row(root, key).lookup(".property-wikilink");
                link.fire();
                out.add("link: " + link.getText());
            }
            out.add("opened: " + opened);
            return out;
        });

        assertEquals(List.of(
                "open at first: false",
                "open after the button: true",
                "modified at first: false",
                "modified after editing: true",
                "rows: [estado, ver, tema, aqui]",
                "note: {estado=final, ver=[[Carpeta/Destino|destino]], tema=Ver [[Destino#Uso]] y más,"
                        + " aqui=[[Destino#Uso|aquí]]}",
                // As in the reading view: the alias, or the note and its heading, or the note.
                "link: destino",
                "link: Destino › Uso",
                "link: aquí",
                "opened: [Carpeta/Destino, Destino#Uso, Destino#Uso]"), seen);
    }

    /**
     * As a link in the note: Cmd/Ctrl+click or a middle click on a property's link opens its
     * note in a new tab; a plain click, in the active tab.
     */
    @Test
    void aPropertysLinkOpensInANewTabWithCmdOrCtrlOrTheMiddleButton() throws Exception {
        Assumptions.assumeTrue(FxTestSupport.isFxRuntimeAvailable());

        List<String> opened = onFx(() -> {
            Parent root = loadEditor();
            EditorController controller = controller(root);
            List<String> out = new ArrayList<>();
            Field links = EditorController.class.getDeclaredField("wikiLinkHandler");
            links.setAccessible(true);
            links.set(controller, (EditorController.WikiLinkHandler) (title, newTab) ->
                    out.add(title + (newTab ? " in a new tab" : " in the active tab")));
            controller.loadNote(noteWith("a.md", Map.of("ver", "[[Destino#Uso]]")));
            ((ToggleButton) root.lookup("#togglePropertiesBtn")).fire();
            controller.setReadOnlyView(true);
            root.applyCss();
            root.layout();
            Hyperlink link = (Hyperlink) row(root, "ver").lookup(".property-wikilink");
            click(link, MouseButton.PRIMARY, true);
            click(link, MouseButton.MIDDLE, false);
            click(link, MouseButton.PRIMARY, false);
            return out;
        });

        assertEquals(List.of("Destino#Uso in a new tab", "Destino#Uso in a new tab", "Destino#Uso in the active tab"),
                opened);
    }

    /**
     * Like the tags bar: a button opens and closes the panel, marks a note that has
     * properties and says how many, and the panel stays open from one note to the next.
     */
    @Test
    void theButtonOpensThePanelAndMarksANoteWithProperties() throws Exception {
        Assumptions.assumeTrue(FxTestSupport.isFxRuntimeAvailable());

        List<String> seen = onFx(() -> {
            Parent root = loadEditor();
            EditorController controller = controller(root);
            ToggleButton button = (ToggleButton) root.lookup("#togglePropertiesBtn");
            Node section = root.lookup("#propertiesSection");
            Node dot = root.lookup("#propertiesDot");
            List<String> out = new ArrayList<>();

            controller.loadNote(noteWith("a.md", Map.of("estado", "borrador")));
            out.add(panel(button, section, dot));
            button.fire();
            out.add(panel(button, section, dot));
            controller.loadNote(noteWith("b.md", Map.of()));
            out.add(panel(button, section, dot));
            button.fire();
            out.add(panel(button, section, dot));
            return out;
        });

        assertEquals(List.of(
                "button false panel false dot true tip Propiedades (1)",
                "button true panel true dot true tip Propiedades (1)",
                "button true panel true dot false tip Propiedades",
                "button false panel false dot false tip Propiedades"), seen);
    }

    /** {@code author}, {@code status} and {@code source_url} are properties like any other, as in Obsidian. */
    @Test
    void authorStatusAndSourceUrlAreShownAsProperties() throws Exception {
        Assumptions.assumeTrue(FxTestSupport.isFxRuntimeAvailable());

        List<String> rows = onFx(() -> {
            Parent root = loadEditor();
            Note note = FrontmatterHandler.parse("---\nauthor: Frank Herbert\nstatus: leyendo\n"
                    + "source_url: https://example.org\nprivate: false\ntags:\n  - libro\n---\ntexto\n");
            note.setId("Dune.md");
            controller(root).loadNote(note);
            ((ToggleButton) root.lookup("#togglePropertiesBtn")).fire();
            return rowKeys(root);
        });

        assertEquals(List.of("author", "status", "source_url"), rows);
    }

    /** A property is added in the panel itself: a new row whose name and value are typed in place. */
    @Test
    void aPropertyIsAddedInPlace() throws Exception {
        Assumptions.assumeTrue(FxTestSupport.isFxRuntimeAvailable());

        List<String> seen = onFx(() -> {
            Parent root = loadEditor();
            EditorController controller = controller(root);
            Note note = noteWith("a.md", Map.of());
            controller.loadNote(note);
            ((ToggleButton) root.lookup("#togglePropertiesBtn")).fire();
            List<String> out = new ArrayList<>();
            Button add = (Button) root.lookup("#addPropertyBtn");
            out.add("add shown: " + shown(add));
            add.fire();
            TextField key = (TextField) root.lookup(".property-key-field");
            out.add("modified with an empty row: " + controller.isModified());
            key.setText("prioridad");
            ((TextField) key.getParent().lookup(".property-value")).setText("alta");
            out.add("modified after typing: " + controller.isModified());
            controller.setReadOnlyView(true);
            out.add("note: " + note.getCustomProperties());
            return out;
        });

        assertEquals(List.of(
                "add shown: true",
                "modified with an empty row: false",
                "modified after typing: true",
                "note: {prioridad=alta}"), seen);
    }

    /** The reading view shows the panel read-only, and says so when the note has no properties. */
    @Test
    void theReadingViewSaysWhenThereAreNoProperties() throws Exception {
        Assumptions.assumeTrue(FxTestSupport.isFxRuntimeAvailable());

        List<String> seen = onFx(() -> {
            Parent root = loadEditor();
            EditorController controller = controller(root);
            controller.loadNote(noteWith("a.md", Map.of()));
            ((ToggleButton) root.lookup("#togglePropertiesBtn")).fire();
            controller.setReadOnlyView(true);
            Label empty = (Label) root.lookup("#propertiesEmptyLabel");
            Node add = root.lookup("#addPropertyBtn");
            return List.of("panel " + shown(root.lookup("#propertiesSection")),
                    "empty " + shown(empty) + " " + empty.getText(),
                    "add " + shown(add));
        });

        assertEquals(List.of("panel true", "empty true Sin propiedades", "add false"), seen);
    }

    private static Parent loadEditor() throws Exception {
        URL fxml = PropertiesPanelTest.class.getResource("/com/example/jylos/ui/view/EditorView.fxml");
        FXMLLoader loader = new FXMLLoader(fxml,
                ResourceBundle.getBundle("com.example.jylos.i18n.messages", Locale.forLanguageTag("es")));
        Parent root = loader.load();
        root.getProperties().put("controller", loader.getController());
        Field bundle = EditorController.class.getDeclaredField("bundle");
        bundle.setAccessible(true);
        bundle.set(loader.getController(), loader.getResources());
        new Scene(new Pane(root), 1000, 700);
        // Skins (and so the button's graphic, with its dot) exist after a CSS pass.
        root.applyCss();
        return root;
    }

    private static EditorController controller(Parent root) {
        return (EditorController) root.getProperties().get("controller");
    }

    private static Note noteWith(String id, Map<String, String> props) {
        Note note = new Note(id, id, "x");
        note.setCustomProperties(new LinkedHashMap<>(props));
        return note;
    }

    /** Visible, with every container up to the editor visible too. */
    private static boolean shown(Node node) {
        for (Node n = node; n != null; n = n.getParent()) {
            if (!n.isVisible()) {
                return false;
            }
        }
        return true;
    }

    private static String panel(ToggleButton button, Node section, Node dot) {
        return "button " + button.isSelected() + " panel " + section.isVisible() + " dot " + dot.isVisible()
                + " tip " + button.getTooltip().getText();
    }

    private static Node row(Parent root, String key) {
        for (Node node : root.lookupAll(".property-row")) {
            if (key.equals(node.getUserData())) {
                return node;
            }
        }
        throw new AssertionError("no row " + key);
    }

    private static List<String> rowKeys(Parent root) {
        List<String> keys = new ArrayList<>();
        for (Node node : root.lookupAll(".property-row")) {
            keys.add(String.valueOf(node.getUserData()));
        }
        return keys;
    }

    private static final boolean MAC = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("mac");

    /** A real click: pressed and let go over the middle of {@code node}, with Cmd/Ctrl when {@code shortcut}. */
    private static void click(Node node, MouseButton which, boolean shortcut) {
        Bounds bounds = node.getLayoutBounds();
        Point2D centre = node.localToScene(bounds.getCenterX(), bounds.getCenterY());
        Event.fireEvent(node, mouse(MouseEvent.MOUSE_PRESSED, which, shortcut, centre, true));
        Event.fireEvent(node, mouse(MouseEvent.MOUSE_RELEASED, which, shortcut, centre, false));
        Event.fireEvent(node, mouse(MouseEvent.MOUSE_CLICKED, which, shortcut, centre, false));
    }

    private static MouseEvent mouse(EventType<MouseEvent> type, MouseButton button, boolean shortcut, Point2D at,
            boolean down) {
        return new MouseEvent(type, at.getX(), at.getY(), at.getX(), at.getY(), button, 1, false, shortcut && !MAC,
                false, shortcut && MAC, down && button == MouseButton.PRIMARY, down && button == MouseButton.MIDDLE,
                false, false, false, true, null);
    }
}
