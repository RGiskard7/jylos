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

import com.example.jylos.data.models.Note;
import com.example.jylos.tests.FxTestSupport;

import javafx.event.Event;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.TextField;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Pane;

/**
 * The properties panel edits the note's properties: a changed value marks the note modified
 * and reaches the note, a removed property leaves it, and in the reading view a property's
 * {@code [[link]]} opens the note its path names.
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
            links.set(controller, (EditorController.WikiLinkHandler) opened::add);

            Note note = new Note("a.md", "A", "x");
            Map<String, String> props = new LinkedHashMap<>();
            props.put("estado", "borrador");
            props.put("borrar", "sí");
            props.put("ver", "[[Carpeta/Destino|destino]]");
            note.setCustomProperties(props);
            controller.loadNote(note);

            List<String> out = new ArrayList<>();
            Node body = root.lookup("#propertiesBody");
            out.add("open at first: " + body.isVisible());
            Event.fireEvent(root.lookup(".properties-header"), new MouseEvent(
                    MouseEvent.MOUSE_CLICKED, 1, 1, 1, 1, MouseButton.PRIMARY, 1,
                    false, false, false, false, false, false, false, false, false, true, null));
            out.add("open after a click on the header: " + body.isVisible());
            out.add("modified at first: " + controller.isModified());
            TextField estado = (TextField) row(root, "estado").lookup(".property-value");
            estado.setText("final");
            out.add("modified after editing: " + controller.isModified());
            ((Button) row(root, "borrar").lookup(".property-remove-btn")).fire();
            out.add("rows: " + rowKeys(root));

            controller.setReadOnlyView(true);
            out.add("note: " + note.getCustomProperties());
            ((Hyperlink) row(root, "ver").lookup(".property-wikilink")).fire();
            out.add("link: " + ((Hyperlink) row(root, "ver").lookup(".property-wikilink")).getText());
            out.add("opened: " + opened);
            return out;
        });

        assertEquals(List.of(
                "open at first: false",
                "open after a click on the header: true",
                "modified at first: false",
                "modified after editing: true",
                "rows: [estado, ver]",
                "note: {estado=final, ver=[[Carpeta/Destino|destino]]}",
                // What it shows today: the note's title, not the link's display text.
                "link: Destino",
                "opened: [Carpeta/Destino]"), seen);
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
}
