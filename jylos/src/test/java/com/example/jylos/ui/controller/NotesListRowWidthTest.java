package com.example.jylos.ui.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static com.example.jylos.tests.FxTestSupport.onFx;

import java.lang.reflect.Field;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.ResourceBundle;
import java.util.TreeSet;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import com.example.jylos.data.models.Note;
import com.example.jylos.tests.FxTestSupport;

import javafx.fxml.FXMLLoader;
import javafx.geometry.Bounds;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.layout.Region;
import javafx.stage.Stage;

/**
 * Every row of the classic notes list ends where the row does: the note's icon and its
 * "Created" date line up on one edge in all rows, the same edge after the panel is resized
 * and brought back to the same width. They used to end where a guess of the scroll bar's
 * width put them, so rows laid out at different moments ended at different places.
 */
class NotesListRowWidthTest {

    private static final String[] DATES = {"2026-04-08", "2025-12-11", "2024-10-19", "2026-11-18", "2025-12-26"};

    @Test
    void theIconAndTheCreatedDateEndOnTheRowsEdgeInEveryRow() throws Exception {
        Assumptions.assumeTrue(FxTestSupport.isFxRuntimeAvailable());
        List<String> edges = onFx(() -> {
            URL fxml = NotesListRowWidthTest.class.getClassLoader()
                    .getResource("com/example/jylos/ui/view/NotesListView.fxml");
            ResourceBundle bundle = ResourceBundle.getBundle("com.example.jylos.i18n.messages",
                    Locale.forLanguageTag("es"));
            FXMLLoader loader = new FXMLLoader(fxml, bundle);
            Parent root = loader.load();
            NotesListController notes = loader.getController();
            notes.wire(null, null, null, null, bundle, n -> { }, n -> { }, n -> { }, (n, m) -> { }, m -> { });
            Field field = NotesListController.class.getDeclaredField("notesListView");
            field.setAccessible(true);
            @SuppressWarnings("unchecked")
            ListView<Note> list = (ListView<Note>) field.get(notes);
            for (int i = 0; i < 80; i++) {
                Note note = new Note("00 - Nota " + i, "x");
                note.setId("n" + i);
                note.setCreatedDate(DATES[i % DATES.length]);
                note.setModifiedDate(DATES[(i + 2) % DATES.length]);
                list.getItems().add(note);
            }
            Stage stage = new Stage();
            Scene scene = new Scene(root, 360, 900);
            scene.getStylesheets().add(NotesListRowWidthTest.class
                    .getResource("/com/example/jylos/ui/css/dark-theme.css").toExternalForm());
            stage.setScene(scene);
            stage.show();
            root.applyCss();
            root.layout();

            List<String> out = new ArrayList<>();
            out.add(edgesOf(list));
            stage.setWidth(460);
            root.layout();
            stage.setWidth(360);
            root.layout();
            out.add(edgesOf(list));
            list.scrollTo(37);
            root.layout();
            out.add(edgesOf(list));
            stage.close();
            return out;
        });

        assertEquals(List.of("rows end on their edge", "rows end on their edge", "rows end on their edge"),
                edges);
    }

    /**
     * How far each shown row's icon and "Created" date end from the row's right edge (its
     * padding and the container's taken off), as one line per distinct gap.
     */
    private static String edgesOf(ListView<Note> list) {
        TreeSet<String> gaps = new TreeSet<>();
        int rows = 0;
        for (Node node : list.lookupAll(".list-cell")) {
            if (!(node instanceof ListCell<?> cell) || cell.isEmpty() || !cell.isVisible()
                    || !(cell.getGraphic() instanceof Region box)) {
                continue;
            }
            rows++;
            double rowEdge = cell.localToScene(cell.getLayoutBounds()).getMaxX() - cell.snappedRightInset()
                    - box.snappedRightInset();
            Bounds icon = sceneBounds(box.lookup(".note-cell-icon").getParent());
            Bounds created = sceneBounds(box.lookup(".note-cell-date-created"));
            gaps.add(String.format(Locale.ROOT, "icon %.1f, created %.1f", rowEdge - icon.getMaxX(),
                    rowEdge - created.getMaxX()));
        }
        assertTrue(rows >= 8, "rows shown: " + rows);
        return gaps.equals(new TreeSet<>(List.of("icon 0.0, created 0.0"))) ? "rows end on their edge"
                : rows + " rows: " + gaps;
    }

    private static Bounds sceneBounds(Node node) {
        return node.localToScene(node.getLayoutBounds());
    }
}
