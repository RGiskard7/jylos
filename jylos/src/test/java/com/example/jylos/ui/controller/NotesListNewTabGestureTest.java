package com.example.jylos.ui.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static com.example.jylos.tests.FxTestSupport.onFx;

import java.lang.reflect.Field;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
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
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.MenuItem;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.stage.Stage;

/**
 * In the notes list, Cmd/Ctrl+click and middle click open the note in a new tab and do not
 * select it (selecting would also open it in the current tab, replacing that note). A plain
 * click still selects, which opens the note in the current tab. The context menu offers
 * "Open in new tab".
 */
class NotesListNewTabGestureTest {

    private static final boolean MAC = System.getProperty("os.name", "").toLowerCase().contains("mac");

    @Test
    void shortcutAndMiddleClickOpenInANewTabWithoutSelectingAndAPlainClickSelects() throws Exception {
        Assumptions.assumeTrue(FxTestSupport.isFxRuntimeAvailable());

        List<String> newTab = new ArrayList<>();
        List<String> selected = new ArrayList<>();
        Fixture f = onFx(() -> new Fixture(newTab, selected));

        onFx(() -> {
            press(f.cell(1), MouseButton.PRIMARY, true);
            press(f.cell(0), MouseButton.MIDDLE, false);
            return null;
        });
        String selectionAfterGestures = onFx(() -> String.valueOf(f.list.getSelectionModel().getSelectedItem()));
        onFx(() -> null); // let any runLater from a selection run

        assertEquals(List.of("b", "a"), newTab);
        assertEquals("null", selectionAfterGestures, "a new-tab gesture must not select the note");
        assertEquals(List.of(), selected, "nothing may open in the current tab");

        onFx(() -> {
            press(f.cell(1), MouseButton.PRIMARY, false);
            return null;
        });
        onFx(() -> null);
        assertEquals(List.of("b"), selected, "a plain click still opens the note in the current tab");
        assertEquals(List.of("b", "a"), newTab);
    }

    @Test
    void theContextMenuOffersOpenInNewTab() throws Exception {
        Assumptions.assumeTrue(FxTestSupport.isFxRuntimeAvailable());

        List<String> newTab = new ArrayList<>();
        List<String> labels = onFx(() -> {
            Fixture f = new Fixture(newTab, new ArrayList<>());
            ListCell<Note> cell = f.cell(0);
            assertNotNull(cell.getContextMenu(), "the note row has no context menu");
            List<String> out = new ArrayList<>();
            for (MenuItem item : cell.getContextMenu().getItems()) {
                out.add(item.getText());
            }
            cell.getContextMenu().getItems().stream()
                    .filter(item -> "Abrir en pestaña nueva".equals(item.getText()))
                    .findFirst().ifPresent(MenuItem::fire);
            return out;
        });

        assertEquals(List.of("Abrir", "Abrir en pestaña nueva"), labels.subList(0, 2));
        assertEquals(List.of("a"), newTab);
    }

    private static final class Fixture {
        final ListView<Note> list;

        @SuppressWarnings("unchecked")
        Fixture(List<String> newTab, List<String> selected) throws Exception {
            URL fxml = NotesListNewTabGestureTest.class.getClassLoader()
                    .getResource("com/example/jylos/ui/view/NotesListView.fxml");
            ResourceBundle bundle = ResourceBundle.getBundle("com.example.jylos.i18n.messages",
                    Locale.forLanguageTag("es"));
            FXMLLoader loader = new FXMLLoader(fxml, bundle);
            Parent root = loader.load();
            NotesListController controller = loader.getController();
            controller.wire(null, null, null, null, bundle,
                    n -> selected.add(n.getId()), n -> { }, n -> { },
                    (notes, msg) -> { }, msg -> { });
            controller.setOpenInNewTabAction(n -> newTab.add(n.getId()));

            Field field = NotesListController.class.getDeclaredField("notesListView");
            field.setAccessible(true);
            list = (ListView<Note>) field.get(controller);
            list.getItems().setAll(note("a"), note("b"));

            Stage stage = new Stage();
            stage.setScene(new Scene(root, 320, 480));
            stage.show();
            root.applyCss();
            root.layout();
        }

        @SuppressWarnings("unchecked")
        ListCell<Note> cell(int index) {
            for (Node node : list.lookupAll(".list-cell")) {
                if (node instanceof ListCell<?> c && c.getIndex() == index && !c.isEmpty()) {
                    return (ListCell<Note>) c;
                }
            }
            throw new AssertionError("no rendered cell for row " + index);
        }
    }

    private static Note note(String id) {
        Note note = new Note(id, "content");
        note.setId(id);
        return note;
    }

    /** Presses and releases at the centre of {@code node} (events carry scene coordinates). */
    private static void press(Node node, MouseButton button, boolean shortcut) {
        javafx.geometry.Point2D at = node.localToScene(node.getBoundsInLocal().getWidth() / 2,
                node.getBoundsInLocal().getHeight() / 2);
        Event.fireEvent(node, new MouseEvent(MouseEvent.MOUSE_PRESSED, at.getX(), at.getY(), at.getX(), at.getY(),
                button, 1, false, shortcut && !MAC, false, shortcut && MAC,
                button == MouseButton.PRIMARY, button == MouseButton.MIDDLE, false,
                false, false, true, null));
        Event.fireEvent(node, new MouseEvent(MouseEvent.MOUSE_RELEASED, at.getX(), at.getY(), at.getX(), at.getY(),
                button, 1, false, shortcut && !MAC, false, shortcut && MAC,
                false, false, false, false, false, true, null));
    }
}
