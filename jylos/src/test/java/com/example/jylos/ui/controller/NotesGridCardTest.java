package com.example.jylos.ui.controller;

import static com.example.jylos.tests.FxTestSupport.onFx;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import com.example.jylos.data.models.Note;
import com.example.jylos.tests.FxTestSupport;

import javafx.event.Event;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

/** A card of the grid shows its note's title, and a click on it opens the note. */
class NotesGridCardTest {

    @Test
    void aCardShowsItsNoteAndAClickOpensIt() throws Exception {
        Assumptions.assumeTrue(FxTestSupport.isFxRuntimeAvailable());
        List<String> opened = new ArrayList<>();
        VBox container = onFx(() -> {
            NotesListController controller = new NotesListController();
            ListView<Note> source = new ListView<>();
            for (String title : List.of("Uno", "Dos", "Tres")) {
                Note note = new Note(title + ".md", title, "texto de " + title);
                source.getItems().add(note);
            }
            VBox box = new VBox(source);
            Stage stage = new Stage();
            stage.setScene(new Scene(box, 640, 480));
            stage.show();
            Field field = NotesListController.class.getDeclaredField("notesListView");
            field.setAccessible(true);
            field.set(controller, source);
            controller.initializeNotesGrid();
            controller.showGridView(null, warning -> { });
            controller.refreshGridView(source, false, key -> key, note -> opened.add(note.getTitle()), status -> { });
            return box;
        });
        TimeUnit.MILLISECONDS.sleep(300);

        List<String> titles = onFx(() -> {
            container.applyCss();
            container.layout();
            List<String> out = new ArrayList<>();
            for (Node card : container.lookupAll(".note-card")) {
                out.add(((Label) card.lookup(".note-card-title")).getText());
            }
            return out;
        });
        assertEquals(List.of("Uno", "Dos", "Tres"), titles);

        onFx(() -> {
            Node card = container.lookupAll(".note-card").stream().skip(1).findFirst().orElseThrow();
            Event.fireEvent(card, new MouseEvent(MouseEvent.MOUSE_CLICKED, 1, 1, 1, 1, MouseButton.PRIMARY, 1,
                    false, false, false, false, true, false, false, false, false, true, null));
            return null;
        });
        assertEquals(List.of("Dos"), opened);
    }
}
