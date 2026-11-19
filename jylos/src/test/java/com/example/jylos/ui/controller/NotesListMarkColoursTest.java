package com.example.jylos.ui.controller;

import static com.example.jylos.tests.FxTestSupport.onFx;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.ResourceBundle;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.kordamp.ikonli.javafx.FontIcon;

import com.example.jylos.data.models.Note;
import com.example.jylos.tests.FxTestSupport;

import javafx.collections.FXCollections;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.paint.Color;

/**
 * In the notes list, a selected note's pinned, favourite and private marks stay visible in
 * both themes: none is painted in the colour of the selected row.
 */
class NotesListMarkColoursTest {

    @Test
    void theMarksOfASelectedNoteStayVisible() throws Exception {
        Assumptions.assumeTrue(FxTestSupport.isFxRuntimeAvailable());
        for (String theme : List.of("modern-theme.css", "dark-theme.css")) {
            List<String> seen = onFx(() -> {
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/example/jylos/ui/view/NotesListView.fxml"),
                        ResourceBundle.getBundle("com.example.jylos.i18n.messages", Locale.forLanguageTag("es")));
                Parent root = loader.load();
                Scene scene = new Scene(root, 400, 300);
                scene.getStylesheets().setAll(getClass().getResource("/com/example/jylos/ui/css/" + theme).toExternalForm());
                Field field = NotesListController.class.getDeclaredField("notesListView");
                field.setAccessible(true);
                @SuppressWarnings("unchecked")
                ListView<Note> list = (ListView<Note>) field.get(loader.getController());
                Note note = new Note("Marcada", "texto");
                note.setId("Marcada.md");
                note.setPinned(true);
                note.setFavorite(true);
                note.setPrivate(true);
                list.setItems(FXCollections.observableArrayList(note));
                list.getSelectionModel().select(0);
                root.applyCss();
                root.layout();

                List<String> out = new ArrayList<>();
                for (Node node : list.lookupAll(".list-cell")) {
                    ListCell<?> cell = (ListCell<?>) node;
                    if (cell.getItem() == null) {
                        continue;
                    }
                    Color row = cell.getBackground() != null && !cell.getBackground().getFills().isEmpty()
                            ? (Color) cell.getBackground().getFills().get(cell.getBackground().getFills().size() - 1).getFill()
                            : null;
                    for (Node icon : cell.lookupAll(".ikonli-font-icon")) {
                        FontIcon font = (FontIcon) icon;
                        if (List.of("bi-pin-angle", "fth-star", "fth-lock").contains(font.getIconLiteral())) {
                            out.add(font.getIconLiteral() + (font.getIconColor().equals(row) ? " invisible" : " visible"));
                        }
                    }
                }
                return out;
            });
            assertEquals(List.of("bi-pin-angle visible", "fth-star visible", "fth-lock visible"), seen, theme);
        }
    }
}
