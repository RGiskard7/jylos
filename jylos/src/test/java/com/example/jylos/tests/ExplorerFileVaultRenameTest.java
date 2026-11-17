package com.example.jylos.tests;

import static com.example.jylos.tests.FxTestSupport.onFx;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.ResourceBundle;
import java.util.function.BooleanSupplier;
import java.util.stream.Stream;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.example.jylos.data.dao.filesystem.FactoryDAOFileSystem;
import com.example.jylos.data.models.interfaces.Component;
import com.example.jylos.service.FolderService;
import com.example.jylos.service.NoteService;
import com.example.jylos.ui.controller.SidebarController;

import javafx.event.Event;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.TextField;
import javafx.scene.control.TreeCell;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.stage.Stage;

/**
 * Renaming a folder in the Explorer of a folder vault renames it on disk and in the tree, as the
 * user does it: the folder's menu, Rename, a new name and Enter. A folder's id is its path there,
 * and changes with the rename.
 */
class ExplorerFileVaultRenameTest {

    @Test
    void renamingAFolderInTheExplorerRenamesItOnDiskAndInTheTree(@TempDir Path vault) throws Exception {
        Assumptions.assumeTrue(FxTestSupport.isFxRuntimeAvailable());
        Files.createDirectories(vault.resolve("Trabajo"));
        Files.writeString(vault.resolve("Trabajo/Acta.md"), "# Acta\n");
        Files.writeString(vault.resolve("Inicio.md"), "# Inicio\n");
        FactoryDAOFileSystem factory = new FactoryDAOFileSystem(vault.toString());
        FolderService folders = new FolderService(factory.getFolderDAO(), factory.getNoteDAO());
        NoteService notes = new NoteService(factory.getNoteDAO(), factory.getFolderDAO());

        SidebarController sidebar = onFx(() -> {
            URL fxml = ExplorerFileVaultRenameTest.class.getClassLoader()
                    .getResource("com/example/jylos/ui/view/SidebarView.fxml");
            ResourceBundle bundle = ResourceBundle.getBundle("com.example.jylos.i18n.messages", Locale.forLanguageTag("es"));
            FXMLLoader loader = new FXMLLoader(fxml, bundle);
            Parent root = loader.load();
            SidebarController controller = loader.getController();
            controller.wire(null, notes, null, folders, bundle, x -> { }, x -> { }, x -> { }, x -> { }, x -> { },
                    x -> { }, x -> { });
            Stage stage = new Stage();
            stage.setScene(new Scene(root, 320, 480));
            stage.show();
            controller.setExplorerMode(true);
            return controller;
        });
        TreeView<Component> tree = sidebar.getExplorerTree().getView();
        waitUntil(() -> onFxQuiet(() -> row(tree, "Trabajo") != null));

        onFx(() -> {
            row(tree, "Trabajo").getContextMenu().getItems().stream()
                    .filter(i -> "Renombrar".equals(i.getText())).findFirst().orElseThrow().fire();
            return null;
        });
        waitUntil(() -> onFxQuiet(() -> tree.lookup(".explorer-rename-field") != null
                && tree.lookup(".explorer-rename-field").getScene() != null));
        onFx(() -> {
            TextField field = (TextField) tree.lookup(".explorer-rename-field");
            field.setText("Oficina");
            Event.fireEvent(field, new KeyEvent(KeyEvent.KEY_PRESSED, "", "", KeyCode.ENTER, false, false, false, false));
            return null;
        });
        waitUntil(() -> Files.isDirectory(vault.resolve("Oficina")));

        assertTrue(Files.isRegularFile(vault.resolve("Oficina/Acta.md")), "the folder and its notes are under the new name");
        waitUntil(() -> onFxQuiet(() -> topTitles(tree).contains("Oficina")));
        assertEquals(List.of("Oficina", "Inicio"), onFx(() -> topTitles(tree)));
        try (Stream<Path> top = Files.list(vault)) {
            assertEquals(List.of(), top.filter(p -> p.getFileName().toString().equals("Trabajo")).toList(),
                    "the old folder is gone");
        }
    }

    /**
     * Renaming a folder and then one inside it: the subfolder's path changed with its parent's,
     * and the second rename must still find it.
     */
    @Test
    void aSubfolderCanBeRenamedAfterItsParentWas(@TempDir Path vault) throws Exception {
        Assumptions.assumeTrue(FxTestSupport.isFxRuntimeAvailable());
        Files.createDirectories(vault.resolve("Trabajo/Actas"));
        Files.writeString(vault.resolve("Trabajo/Actas/Lunes.md"), "# Lunes\n");
        TreeView<Component> tree = explorerOn(vault);
        waitUntil(() -> onFxQuiet(() -> row(tree, "Trabajo") != null));
        onFx(() -> {
            tree.getRoot().getChildren().get(0).setExpanded(true);
            return null;
        });
        waitUntil(() -> onFxQuiet(() -> row(tree, "Actas") != null));

        rename(tree, "Trabajo", "Oficina");
        waitUntil(() -> Files.isDirectory(vault.resolve("Oficina/Actas")));
        waitUntil(() -> onFxQuiet(() -> row(tree, "Oficina") != null && row(tree, "Actas") != null));
        rename(tree, "Actas", "Reuniones");

        waitUntil(() -> Files.isDirectory(vault.resolve("Oficina/Reuniones")));
        assertTrue(Files.isRegularFile(vault.resolve("Oficina/Reuniones/Lunes.md")));
    }

    private static TreeView<Component> explorerOn(Path vault) throws Exception {
        FactoryDAOFileSystem factory = new FactoryDAOFileSystem(vault.toString());
        FolderService folders = new FolderService(factory.getFolderDAO(), factory.getNoteDAO());
        NoteService notes = new NoteService(factory.getNoteDAO(), factory.getFolderDAO());
        SidebarController sidebar = onFx(() -> {
            URL fxml = ExplorerFileVaultRenameTest.class.getClassLoader()
                    .getResource("com/example/jylos/ui/view/SidebarView.fxml");
            ResourceBundle bundle = ResourceBundle.getBundle("com.example.jylos.i18n.messages", Locale.forLanguageTag("es"));
            FXMLLoader loader = new FXMLLoader(fxml, bundle);
            Parent root = loader.load();
            SidebarController controller = loader.getController();
            controller.wire(null, notes, null, folders, bundle, x -> { }, x -> { }, x -> { }, x -> { }, x -> { },
                    x -> { }, x -> { });
            Stage stage = new Stage();
            stage.setScene(new Scene(root, 320, 480));
            stage.show();
            controller.setExplorerMode(true);
            return controller;
        });
        return sidebar.getExplorerTree().getView();
    }

    /** As the user does it: the row's menu, Rename, the new name, Enter. */
    private static void rename(TreeView<Component> tree, String from, String to) throws Exception {
        onFx(() -> {
            row(tree, from).getContextMenu().getItems().stream()
                    .filter(i -> "Renombrar".equals(i.getText())).findFirst().orElseThrow().fire();
            return null;
        });
        waitUntil(() -> onFxQuiet(() -> tree.lookup(".explorer-rename-field") != null
                && tree.lookup(".explorer-rename-field").getScene() != null));
        onFx(() -> {
            TextField field = (TextField) tree.lookup(".explorer-rename-field");
            field.setText(to);
            Event.fireEvent(field, new KeyEvent(KeyEvent.KEY_PRESSED, "", "", KeyCode.ENTER, false, false, false, false));
            return null;
        });
    }

    private static TreeCell<?> row(TreeView<Component> tree, String title) {
        tree.layout();
        for (Node node : tree.lookupAll(".tree-cell")) {
            if (node instanceof TreeCell<?> cell && cell.getItem() instanceof Component c && title.equals(c.getTitle())) {
                return cell;
            }
        }
        return null;
    }

    private static List<String> topTitles(TreeView<Component> tree) {
        return tree.getRoot().getChildren().stream().map(TreeItem::getValue)
                .map(v -> v != null ? v.getTitle() : "").toList();
    }

    private static boolean onFxQuiet(java.util.concurrent.Callable<Boolean> check) {
        try {
            return onFx(check);
        } catch (Exception e) {
            return false;
        }
    }

    private static void waitUntil(BooleanSupplier condition) throws InterruptedException {
        for (int i = 0; i < 100; i++) {
            if (condition.getAsBoolean()) {
                return;
            }
            Thread.sleep(50);
        }
        assertTrue(condition.getAsBoolean(), "the condition was never met");
    }
}
