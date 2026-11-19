package com.example.jylos.ui.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static com.example.jylos.tests.FxTestSupport.onFx;

import java.lang.reflect.Field;
import java.nio.file.Path;
import java.sql.Connection;
import java.util.List;
import java.util.Locale;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BooleanSupplier;
import java.util.prefs.Preferences;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.example.jylos.data.dao.sqlite.FolderDAOSQLite;
import com.example.jylos.data.dao.sqlite.NoteDAOSQLite;
import com.example.jylos.data.dao.sqlite.TagDAOSQLite;
import com.example.jylos.data.models.Folder;
import com.example.jylos.graph.GraphData;
import com.example.jylos.service.FolderService;
import com.example.jylos.service.NoteService;
import com.example.jylos.service.TagService;
import com.example.jylos.tests.FxTestSupport;
import com.example.jylos.tests.SQLiteTestSupport;

import javafx.fxml.FXMLLoader;
import javafx.scene.control.ComboBox;

/**
 * The graph's folder filter names each folder by its path, also with the database, where a
 * folder's id is a generated one nobody would recognise.
 */
class GraphFolderFilterTest {

    @TempDir
    Path dir;
    private Connection connection;
    private Preferences prefs;
    private NoteService notes;
    private TagService tags;

    @BeforeEach
    void setUp() throws Exception {
        prefs = Preferences.userRoot().node("/com/example/jylos/test/" + UUID.randomUUID());
        GraphController.usePreferences(prefs);
        SQLiteTestSupport.configureFreshDatabase(dir.resolve("graph.db"));
        connection = SQLiteTestSupport.openConnection();
        FolderDAOSQLite folderDAO = new FolderDAOSQLite(connection);
        NoteDAOSQLite noteDAO = new NoteDAOSQLite(connection);
        FolderService folders = new FolderService(folderDAO, noteDAO);
        notes = new NoteService(noteDAO, folderDAO);
        tags = new TagService(new TagDAOSQLite(connection), noteDAO);

        Folder trabajo = folders.createFolder("Trabajo");
        Folder reuniones = folders.createSubfolder("Reuniones", trabajo);
        folders.addNoteToFolder(trabajo, notes.createNote("Plan", "[[Acta]]"));
        folders.addNoteToFolder(reuniones, notes.createNote("Acta", "texto"));
        notes.createNote("Suelta", "texto");
    }

    @AfterEach
    void tearDown() throws Exception {
        GraphController.usePreferences(null);
        prefs.removeNode();
        SQLiteTestSupport.closeAndReset(connection);
    }

    @Test
    void theFolderFilterNamesEachFolderByItsPath() throws Exception {
        Assumptions.assumeTrue(FxTestSupport.isFxRuntimeAvailable());
        List<GraphData> built = new CopyOnWriteArrayList<>();
        GraphController controller = onFx(() -> {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/example/jylos/ui/view/GraphView.fxml"),
                    ResourceBundle.getBundle("com.example.jylos.i18n.messages", Locale.forLanguageTag("es")));
            loader.load();
            GraphController graph = loader.getController();
            graph.wire(null, notes, tags, loader.getResources(), (id, newTab) -> { }, () -> { }, () -> null, built::add);
            graph.show(false);
            return graph;
        });
        waitUntil(() -> !built.isEmpty());
        ComboBox<String> combo = combo(controller);

        List<String> shown = onFx(() -> combo.getItems().stream().skip(1)
                .map(item -> combo.getConverter().toString(item)).toList());
        assertEquals(List.of("Trabajo", "Trabajo/Reuniones"), shown, "the folders by their path, not their ids");

        Set<String> filtered = onFx(() -> {
            combo.getSelectionModel().select(2);
            return visibleLabels(controller);
        });
        assertEquals(Set.of("Acta"), filtered, "only the notes of that folder");
    }

    @SuppressWarnings("unchecked")
    private static ComboBox<String> combo(GraphController controller) throws Exception {
        Field field = GraphController.class.getDeclaredField("folderFilterCombo");
        field.setAccessible(true);
        return (ComboBox<String>) field.get(controller);
    }

    /** The names of the notes the graph is drawing. */
    private static Set<String> visibleLabels(GraphController controller) throws Exception {
        Field canvasField = GraphController.class.getDeclaredField("canvas");
        canvasField.setAccessible(true);
        Object canvas = canvasField.get(controller);
        Field labelsField = canvas.getClass().getDeclaredField("labels");
        labelsField.setAccessible(true);
        Field countField = canvas.getClass().getDeclaredField("n");
        countField.setAccessible(true);
        String[] labels = (String[]) labelsField.get(canvas);
        int count = countField.getInt(canvas);
        Set<String> shown = new TreeSet<>();
        for (int i = 0; i < count; i++) {
            shown.add(labels[i]);
        }
        return shown;
    }

    private static void waitUntil(BooleanSupplier condition) throws InterruptedException {
        long end = System.currentTimeMillis() + 10_000;
        while (!condition.getAsBoolean() && System.currentTimeMillis() < end) {
            Thread.sleep(20);
        }
        assertTrue(condition.getAsBoolean(), "timed out");
    }
}
