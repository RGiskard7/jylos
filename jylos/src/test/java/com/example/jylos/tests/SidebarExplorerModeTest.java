package com.example.jylos.tests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.net.URL;
import java.nio.file.Path;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.ResourceBundle;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.example.jylos.data.dao.sqlite.FolderDAOSQLite;
import com.example.jylos.data.dao.sqlite.NoteDAOSQLite;
import com.example.jylos.data.models.Folder;
import com.example.jylos.data.models.interfaces.Component;
import com.example.jylos.service.FolderService;
import com.example.jylos.service.NoteService;
import com.example.jylos.ui.controller.SidebarController;

import javafx.application.Platform;
import javafx.event.Event;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.TextField;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.stage.Stage;

/**
 * The sidebar in the Explorer layout: its folders tab shows the Explorer tree (folders and
 * notes together) instead of the classic folder tree, opening a note goes to the editor,
 * and a vault change reloads what the tree shows. Switching back restores the classic tree.
 */
class SidebarExplorerModeTest {

    @TempDir
    Path tempDir;

    private Connection connection;
    private FolderService folders;
    private NoteService notes;

    @BeforeEach
    void setUp() throws Exception {
        SQLiteTestSupport.configureFreshDatabase(tempDir.resolve("sidebar.db"));
        connection = SQLiteTestSupport.openConnection();
        FolderDAOSQLite folderDAO = new FolderDAOSQLite(connection);
        NoteDAOSQLite noteDAO = new NoteDAOSQLite(connection);
        folders = new FolderService(folderDAO, noteDAO);
        notes = new NoteService(noteDAO, folderDAO);
        Folder trabajo = folders.createFolder("Trabajo");
        folders.addNoteToFolder(trabajo, notes.createNote("Acta", "x"));
        notes.createNote("Inicio", "x");
    }

    @AfterEach
    void tearDown() throws Exception {
        SQLiteTestSupport.closeAndReset(connection);
    }

    @Test
    void theFoldersTabSwapsToTheExplorerTreeAndBack() throws Exception {
        Assumptions.assumeTrue(FxTestSupport.isFxRuntimeAvailable());
        List<String> opened = new ArrayList<>();
        Fixture f = onFx(() -> new Fixture(folders, notes, opened));

        onFx(() -> {
            f.sidebar.setExplorerMode(true);
            return null;
        });
        TreeView<Component> explorer = onFx(() -> f.sidebar.getExplorerTree().getView());
        waitUntil(() -> titles(explorer.getRoot()).equals(List.of("Trabajo", "Inicio")));

        assertTrue(onFx(() -> explorer.getParent() != null && f.classicTree().getParent() == null),
                "the Explorer tree takes the classic folder tree's place");
        assertEquals("Filtrar notas y carpetas…", onFx(() -> f.filterField().getPromptText()),
                "the header filter now filters the Explorer tree");

        onFx(() -> {
            explorer.getSelectionModel().select(explorer.getRoot().getChildren().get(1));
            Event.fireEvent(explorer, new KeyEvent(KeyEvent.KEY_PRESSED, "", "", KeyCode.ENTER,
                    false, false, false, false));
            return null;
        });
        assertEquals(List.of("Inicio CURRENT_TAB"), opened);

        onFx(() -> {
            f.sidebar.setExplorerMode(false);
            return null;
        });
        assertTrue(onFx(() -> explorer.getParent() == null && f.classicTree().getParent() != null));
        assertEquals("Filtrar carpetas...", onFx(() -> f.filterField().getPromptText()));
    }

    @Test
    void aVaultChangeReloadsTheExplorerTree() throws Exception {
        Assumptions.assumeTrue(FxTestSupport.isFxRuntimeAvailable());
        Fixture f = onFx(() -> new Fixture(folders, notes, new ArrayList<>()));
        onFx(() -> {
            f.sidebar.setExplorerMode(true);
            return null;
        });
        TreeView<Component> explorer = onFx(() -> f.sidebar.getExplorerTree().getView());
        waitUntil(() -> titles(explorer.getRoot()).equals(List.of("Trabajo", "Inicio")));
        TreeItem<Component> trabajo = onFx(() -> explorer.getRoot().getChildren().get(0));

        notes.createNote("Borrador", "x");
        onFx(() -> {
            f.sidebar.loadFolders();
            return null;
        });
        waitUntil(() -> titles(explorer.getRoot()).equals(List.of("Trabajo", "Borrador", "Inicio")));

        assertSame(trabajo, onFx(() -> explorer.getRoot().getChildren().get(0)));
    }

    @Test
    void explorerRowsGetTheFolderAndNoteMenus() throws Exception {
        Assumptions.assumeTrue(FxTestSupport.isFxRuntimeAvailable());
        Fixture f = onFx(() -> new Fixture(folders, notes, new ArrayList<>()));
        onFx(() -> {
            f.sidebar.setExplorerNoteMenu(n -> new javafx.scene.control.ContextMenu(
                    new javafx.scene.control.MenuItem("nota " + n.getTitle())));
            f.sidebar.setExplorerMode(true);
            return null;
        });
        TreeView<Component> explorer = onFx(() -> f.sidebar.getExplorerTree().getView());
        waitUntil(() -> titles(explorer.getRoot()).equals(List.of("Trabajo", "Inicio")));

        // Rows exist only once the scene has been laid out with the tree in it.
        AtomicReference<List<String>> menus = new AtomicReference<>(List.of());
        try {
            waitUntil(() -> {
                explorer.getScene().getRoot().applyCss();
                explorer.getScene().getRoot().layout();
                List<String> out = new ArrayList<>();
                for (Node node : explorer.lookupAll(".tree-cell")) {
                    if (node instanceof javafx.scene.control.TreeCell<?> cell && !cell.isEmpty()
                            && cell.getContextMenu() != null) {
                        out.add(cell.getContextMenu().getItems().get(0).getText());
                    }
                }
                menus.set(out.stream().sorted().toList());
                return out.size() >= 2;
            });
        } catch (AssertionError timeout) {
            // fall through to the assertion below, which shows what the rows had
        }

        // lookupAll returns rows in no particular order
        assertEquals(List.of("Nueva Nota", "nota Inicio"), menus.get());
    }

    @Test
    void theHeaderFiltersTheExplorerAndOffersRevealOnlyThere() throws Exception {
        Assumptions.assumeTrue(FxTestSupport.isFxRuntimeAvailable());
        Fixture f = onFx(() -> new Fixture(folders, notes, new ArrayList<>()));
        boolean revealBeforeExplorer = onFx(() -> f.revealButton().isVisible());
        onFx(() -> {
            f.sidebar.setExplorerMode(true);
            return null;
        });
        TreeView<Component> explorer = onFx(() -> f.sidebar.getExplorerTree().getView());
        waitUntil(() -> titles(explorer.getRoot()).equals(List.of("Trabajo", "Inicio")));

        boolean shownInExplorer = onFx(() -> f.revealButton().isVisible() && f.filterField().isVisible());
        onFx(() -> {
            f.filterField().setText("act");
            return null;
        });
        waitUntil(() -> titles(explorer.getRoot()).equals(List.of("Trabajo"))
                && titles(explorer.getRoot().getChildren().get(0)).equals(List.of("Acta")));

        onFx(() -> {
            f.sidebar.setExplorerMode(false);
            return null;
        });

        assertFalse(revealBeforeExplorer, "reveal only makes sense in the Explorer");
        assertTrue(shownInExplorer, "the Explorer header has the filter and the reveal button");
        assertEquals("", onFx(() -> f.filterField().getText()), "a filter does not carry over to the other tree");
        assertFalse(onFx(() -> f.revealButton().isVisible()));
    }

    private static final class Fixture {
        final SidebarController sidebar;

        Fixture(FolderService folders, NoteService notes, List<String> opened) throws Exception {
            URL fxml = SidebarExplorerModeTest.class.getClassLoader()
                    .getResource("com/example/jylos/ui/view/SidebarView.fxml");
            ResourceBundle bundle = ResourceBundle.getBundle("com.example.jylos.i18n.messages",
                    Locale.forLanguageTag("es"));
            FXMLLoader loader = new FXMLLoader(fxml, bundle);
            Parent root = loader.load();
            sidebar = loader.getController();
            sidebar.wire(null, notes, null, folders, bundle, x -> { }, x -> { }, x -> { }, x -> { }, x -> { },
                    x -> { }, x -> { });
            sidebar.setExplorerOpenAction((note, target) -> opened.add(note.getTitle() + " " + target));
            Stage stage = new Stage();
            stage.setScene(new Scene(root, 320, 480));
            stage.show();
        }

        TreeView<?> classicTree() throws Exception {
            return (TreeView<?>) field("folderTreeView");
        }

        javafx.scene.control.Button revealButton() throws Exception {
            return (javafx.scene.control.Button) field("revealActiveNoteBtn");
        }

        TextField filterField() throws Exception {
            return (TextField) field("filterFoldersField");
        }

        private Object field(String name) throws Exception {
            Field field = SidebarController.class.getDeclaredField(name);
            field.setAccessible(true);
            return field.get(sidebar);
        }
    }

    private static List<String> titles(TreeItem<Component> parent) {
        List<String> out = new ArrayList<>();
        if (parent != null) {
            for (TreeItem<Component> child : parent.getChildren()) {
                out.add(child.getValue() != null ? child.getValue().getTitle() : "(loading)");
            }
        }
        return out;
    }

    private static void waitUntil(BooleanSupplier condition) throws Exception {
        long deadline = System.currentTimeMillis() + 10_000;
        while (System.currentTimeMillis() < deadline) {
            if (onFx(condition::getAsBoolean)) {
                return;
            }
            Thread.sleep(50);
        }
        assertNotNull(null, "condition not reached in time");
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
        assertTrue(done.await(30, TimeUnit.SECONDS), "test never completed on the FX thread");
        if (failure.get() != null) {
            throw new AssertionError(failure.get());
        }
        return result.get();
    }
}
