package com.example.jylos.tests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static com.example.jylos.tests.FxTestSupport.onFx;

import java.lang.reflect.Field;
import java.net.URL;
import java.nio.file.Path;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.ResourceBundle;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.example.jylos.data.dao.sqlite.FolderDAOSQLite;
import com.example.jylos.data.dao.sqlite.NoteDAOSQLite;
import com.example.jylos.data.models.Folder;
import com.example.jylos.service.FolderService;
import com.example.jylos.service.NoteService;
import com.example.jylos.ui.controller.SidebarController;

import javafx.fxml.FXMLLoader;
import javafx.scene.control.TreeItem;

/**
 * The classic folder tree orders folders as the Explorer does: accents and case do not push
 * a name to the end ("Árbol" used to come after "zeta"), and numbers compare by value
 * ("Capítulo 9" before "Capítulo 10"). The Z-A toggle reverses that order.
 */
class SidebarClassicFolderOrderTest {

    @TempDir
    Path tempDir;

    private Connection connection;
    private FolderService folders;
    private NoteService notes;

    @BeforeEach
    void setUp() throws Exception {
        SQLiteTestSupport.configureFreshDatabase(tempDir.resolve("order.db"));
        connection = SQLiteTestSupport.openConnection();
        FolderDAOSQLite folderDAO = new FolderDAOSQLite(connection);
        NoteDAOSQLite noteDAO = new NoteDAOSQLite(connection);
        folders = new FolderService(folderDAO, noteDAO);
        notes = new NoteService(noteDAO, folderDAO);
        for (String name : List.of("zeta", "Árbol genealógico", "Capítulo 10", "audio", "Capítulo 9")) {
            folders.createFolder(name);
        }
    }

    @AfterEach
    void tearDown() throws Exception {
        SQLiteTestSupport.closeAndReset(connection);
    }

    @Test
    void foldersAreInNaturalOrderBothWays() throws Exception {
        Assumptions.assumeTrue(FxTestSupport.isFxRuntimeAvailable());
        AtomicReference<SidebarController> sidebar = new AtomicReference<>();
        AtomicReference<TreeItem<Folder>> vaultRoot = new AtomicReference<>();
        onFx(() -> {
            URL fxml = getClass().getClassLoader().getResource("com/example/jylos/ui/view/SidebarView.fxml");
            ResourceBundle bundle = ResourceBundle.getBundle("com.example.jylos.i18n.messages",
                    Locale.forLanguageTag("es"));
            FXMLLoader loader = new FXMLLoader(fxml, bundle);
            loader.load();
            SidebarController controller = loader.getController();
            controller.wire(null, notes, null, folders, bundle, x -> { }, x -> { }, x -> { }, x -> { }, x -> { },
                    x -> { }, x -> { });
            Field f = SidebarController.class.getDeclaredField("vaultRootItem");
            f.setAccessible(true);
            @SuppressWarnings("unchecked")
            TreeItem<Folder> root = (TreeItem<Folder>) f.get(controller);
            vaultRoot.set(root);
            sidebar.set(controller);
            controller.loadFolders();
            return null;
        });

        List<String> ascending = List.of("Árbol genealógico", "audio", "Capítulo 9", "Capítulo 10", "zeta");
        assertEquals(ascending, waitForTitles(vaultRoot.get(), ascending));

        onFx(() -> {
            sidebar.get().handleSortFolders(null);
            return null;
        });
        List<String> descending = new ArrayList<>(ascending);
        Collections.reverse(descending);
        assertEquals(descending, waitForTitles(vaultRoot.get(), descending));
    }

    private static List<String> waitForTitles(TreeItem<Folder> root, List<String> expected) throws Exception {
        long deadline = System.currentTimeMillis() + 10_000;
        List<String> titles = List.of();
        while (System.currentTimeMillis() < deadline) {
            titles = onFx(() -> root.getChildren().stream().map(i -> i.getValue().getTitle()).toList());
            if (titles.equals(expected)) {
                return titles;
            }
            Thread.sleep(50);
        }
        return titles;
    }
}
