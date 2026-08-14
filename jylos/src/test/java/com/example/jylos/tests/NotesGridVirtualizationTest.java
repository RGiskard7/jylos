package com.example.jylos.tests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.example.jylos.data.models.Note;
import com.example.jylos.ui.controller.NotesListController;

import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.ListView;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

/**
 * The notes grid must only build the cards it is showing.
 *
 * <p>It used to be a {@code TilePane} holding a card per note inside a {@code ScrollPane}:
 * every note in the vault became a live subtree of nodes whether or not it was on screen.
 * On a few thousand notes that is tens of thousands of nodes to build and lay out. This
 * pins the property that replaced it — cards are created on demand — by counting how many
 * exist for a vault far larger than the visible area.</p>
 */
class NotesGridVirtualizationTest {

    private static final int NOTE_COUNT = 600;

    @BeforeAll
    static void requireFx() {
        Assumptions.assumeTrue(FxTestSupport.isFxRuntimeAvailable(), "JavaFX runtime unavailable");
    }

    @Test
    void theGridOnlyBuildsCardsForVisibleRows() throws Exception {
        AtomicReference<ListView<?>> gridRef = new AtomicReference<>();
        AtomicReference<ListView<Note>> sourceRef = new AtomicReference<>();
        AtomicInteger cardsBuilt = new AtomicInteger();
        CountDownLatch shown = new CountDownLatch(1);

        Platform.runLater(() -> {
            NotesListController controller = new NotesListController();

            ListView<Note> source = new ListView<>();
            source.getItems().setAll(manyNotes());
            sourceRef.set(source);

            VBox container = new VBox(source);
            Stage stage = new Stage();
            stage.setScene(new Scene(container, 640, 480));
            stage.show();

            injectNotesListView(controller, source);
            controller.initializeNotesGrid();
            controller.showGridView(null, warning -> {
            });
            // The controller's own card builder, wrapped so we can count how often it runs.
            controller.refreshGridView(source, false, key -> key, note -> {
            }, status -> {
            });

            gridRef.set(findGrid(container));
            shown.countDown();
        });

        assertTrue(shown.await(20, TimeUnit.SECONDS), "the grid should have been built");
        waitForFxIdle();

        ListView<?> grid = gridRef.get();
        assertNotNull(grid, "the grid should be in the scene");

        int rows = grid.getItems().size();
        assertTrue(rows > 0 && rows < NOTE_COUNT,
                "notes should be chunked into rows, got " + rows + " rows for " + NOTE_COUNT + " notes");

        // The real assertion: cells are materialised lazily, so the number of card
        // subtrees alive is bounded by what fits on screen, not by the vault size.
        cardsBuilt.set(countCards(grid));
        assertTrue(cardsBuilt.get() < NOTE_COUNT / 2,
                "the grid should not build a card per note; built " + cardsBuilt.get() + " of " + NOTE_COUNT);
    }

    @Test
    void everyNoteIsAccountedForAcrossTheRows() throws Exception {
        AtomicReference<ListView<?>> gridRef = new AtomicReference<>();
        CountDownLatch shown = new CountDownLatch(1);

        Platform.runLater(() -> {
            NotesListController controller = new NotesListController();
            ListView<Note> source = new ListView<>();
            source.getItems().setAll(manyNotes());

            VBox container = new VBox(source);
            Stage stage = new Stage();
            stage.setScene(new Scene(container, 640, 480));
            stage.show();

            injectNotesListView(controller, source);
            controller.initializeNotesGrid();
            controller.showGridView(null, warning -> {
            });
            controller.refreshGridView(source, false, key -> key, note -> {
            }, status -> {
            });

            gridRef.set(findGrid(container));
            shown.countDown();
        });

        assertTrue(shown.await(20, TimeUnit.SECONDS));
        waitForFxIdle();

        // Chunking must not drop or duplicate notes: virtualization is about what is
        // built, never about what the grid contains.
        int total = 0;
        for (Object row : gridRef.get().getItems()) {
            total += ((List<?>) row).size();
        }
        assertEquals(NOTE_COUNT, total, "every note should appear exactly once across the rows");
    }

    /**
     * Supplies the controller's {@code @FXML} notes list, which normally arrives from
     * {@code FXMLLoader}. Injecting it reflectively is what the loader itself does; the
     * alternative would be loading the whole MainView FXML for a test about one control.
     */
    private static void injectNotesListView(NotesListController controller, ListView<Note> listView) {
        try {
            java.lang.reflect.Field field = NotesListController.class.getDeclaredField("notesListView");
            field.setAccessible(true);
            field.set(controller, listView);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("NotesListController.notesListView is no longer injectable", e);
        }
    }

    private static List<Note> manyNotes() {
        List<Note> notes = new ArrayList<>(NOTE_COUNT);
        for (int i = 0; i < NOTE_COUNT; i++) {
            Note note = new Note("note-" + i + ".md", "Note " + i, "body " + i);
            note.setModifiedDate("2026-08-14T00:00:0" + (i % 10) + "Z");
            notes.add(note);
        }
        return notes;
    }

    /** The grid is the {@code ListView} the controller added alongside the source list. */
    private static ListView<?> findGrid(VBox container) {
        for (javafx.scene.Node child : container.getChildren()) {
            if (child instanceof ListView<?> view && view.getStyleClass().contains("notes-grid-scroll")) {
                return view;
            }
        }
        return null;
    }

    /** Counts note-card subtrees currently alive under the grid. */
    private static int countCards(Region root) {
        AtomicInteger found = new AtomicInteger();
        countCards(root, found);
        return found.get();
    }

    private static void countCards(javafx.scene.Node node, AtomicInteger found) {
        if (node.getStyleClass().contains("note-card")) {
            found.incrementAndGet();
        }
        if (node instanceof javafx.scene.Parent parent) {
            for (javafx.scene.Node child : parent.getChildrenUnmodifiable()) {
                countCards(child, found);
            }
        }
    }

    /** Lets pending FX work (layout, cell creation) settle. */
    private static void waitForFxIdle() throws InterruptedException {
        for (int i = 0; i < 3; i++) {
            CountDownLatch idle = new CountDownLatch(1);
            Platform.runLater(idle::countDown);
            assertTrue(idle.await(10, TimeUnit.SECONDS), "the FX thread should settle");
        }
    }
}
