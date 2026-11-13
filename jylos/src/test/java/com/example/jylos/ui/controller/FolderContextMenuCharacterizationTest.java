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
import java.util.prefs.Preferences;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import com.example.jylos.data.models.Folder;
import com.example.jylos.tests.FxTestSupport;

import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.TreeCell;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.stage.Stage;

/**
 * Characterization of the folder context menu of the sidebar's folder tree, pinned before
 * the menu moved to a shared builder ({@link FolderContextMenuSupport}) that the Explorer tree
 * also uses. The menu is described as its visible entries, in order, with "---" for
 * separators.
 *
 * <p>One deliberate change came with the move: "Move to folder" used to be hidden on every
 * folder, because its visibility was decided when the row was created, before it showed any
 * folder. It is now shown on every folder except the vault root.</p>
 */
class FolderContextMenuCharacterizationTest {

    private static final boolean FILE_VAULT = !"sqlite".equals(
            Preferences.userNodeForPackage(SidebarController.class).get("storage_type", "sqlite"));

    @Test
    void menuOfAFolder() throws Exception {
        Assumptions.assumeTrue(FxTestSupport.isFxRuntimeAvailable());
        List<String> entries = onFx(() -> describe(new Fixture().cellOf("t1").getContextMenu()));

        assertEquals(expected(true), entries);
    }

    @Test
    void menuOfTheVaultRootCannotMoveIt() throws Exception {
        Assumptions.assumeTrue(FxTestSupport.isFxRuntimeAvailable());
        List<String> entries = onFx(() -> describe(new Fixture().cellOf("ROOT").getContextMenu()));

        assertEquals(expected(false), entries);
    }

    @Test
    void revealInFilesDependsOnTheVaultKindAndMoveReachesItsAction() throws Exception {
        Assumptions.assumeTrue(FxTestSupport.isFxRuntimeAvailable());
        List<String> calls = new ArrayList<>();
        List<List<String>> menus = onFx(() -> {
            FolderContextMenuSupport.Actions actions = new FolderContextMenuSupport.Actions(f -> { }, f -> { },
                    f -> { }, f -> { }, f -> calls.add("move " + f.getId()), f -> calls.add("reveal " + f.getId()),
                    f -> { });
            Folder folder = new Folder("t1", "Trabajo");
            ContextMenu files = FolderContextMenuSupport.create(folder, actions, true, key -> key);
            ContextMenu database = FolderContextMenuSupport.create(folder, actions, false, key -> key);
            item(files, "action.move_to").fire();
            item(files, "action.reveal_in_files").fire();
            return List.of(describe(files), describe(database));
        });

        assertEquals(List.of("action.new_note", "action.new_canvas", "action.new_subfolder", "---",
                "action.rename", "action.move_to", "action.reveal_in_files", "action.delete"), menus.get(0));
        assertEquals(List.of("action.new_note", "action.new_canvas", "action.new_subfolder", "---",
                "action.rename", "action.move_to", "action.delete"), menus.get(1));
        assertEquals(List.of("move t1", "reveal t1"), calls);
    }

    private static List<String> expected(boolean movable) {
        List<String> expected = new ArrayList<>(List.of("Nueva Nota", "Nuevo Canvas", "Nueva Subcarpeta", "---",
                "Renombrar"));
        if (movable) {
            expected.add("Mover a Carpeta...");
        }
        if (FILE_VAULT) {
            expected.add("Mostrar en el explorador de archivos");
        }
        expected.add("Eliminar");
        return expected;
    }

    @Test
    void newNoteAndNewCanvasSelectTheFolderAndCreateInIt() throws Exception {
        Assumptions.assumeTrue(FxTestSupport.isFxRuntimeAvailable());
        List<String> calls = onFx(() -> {
            Fixture f = new Fixture();
            ContextMenu menu = f.cellOf("t1").getContextMenu();
            item(menu, "Nueva Nota").fire();
            item(menu, "Nuevo Canvas").fire();
            return f.calls;
        });

        assertEquals(List.of("select t1", "newNote t1", "select t1", "newCanvas t1"), calls);
    }

    private static List<String> describe(ContextMenu menu) {
        assertNotNull(menu, "no context menu");
        List<String> out = new ArrayList<>();
        for (MenuItem item : menu.getItems()) {
            if (item.isVisible()) {
                out.add(item instanceof SeparatorMenuItem ? "---" : item.getText());
            }
        }
        return out;
    }

    private static MenuItem item(ContextMenu menu, String label) {
        return menu.getItems().stream().filter(i -> label.equals(i.getText())).findFirst()
                .orElseThrow(() -> new AssertionError("no menu entry " + label));
    }

    private static final class Fixture {
        final TreeView<Folder> tree;
        final List<String> calls = new ArrayList<>();

        @SuppressWarnings("unchecked")
        Fixture() throws Exception {
            URL fxml = FolderContextMenuCharacterizationTest.class.getClassLoader()
                    .getResource("com/example/jylos/ui/view/SidebarView.fxml");
            ResourceBundle bundle = ResourceBundle.getBundle("com.example.jylos.i18n.messages",
                    Locale.forLanguageTag("es"));
            FXMLLoader loader = new FXMLLoader(fxml, bundle);
            Parent root = loader.load();
            SidebarController controller = loader.getController();
            controller.wire(null, null, null, null, bundle,
                    f -> calls.add("select " + f.getId()), t -> { }, c -> { }, n -> { },
                    f -> calls.add("newNote " + f.getId()), f -> calls.add("newCanvas " + f.getId()),
                    m -> { });

            Field rootField = SidebarController.class.getDeclaredField("vaultRootItem");
            rootField.setAccessible(true);
            TreeItem<Folder> vaultRoot = (TreeItem<Folder>) rootField.get(controller);
            vaultRoot.getChildren().add(new TreeItem<>(new Folder("t1", "Trabajo")));
            Field treeField = SidebarController.class.getDeclaredField("folderTreeView");
            treeField.setAccessible(true);
            tree = (TreeView<Folder>) treeField.get(controller);

            Stage stage = new Stage();
            stage.setScene(new Scene(root, 320, 480));
            stage.show();
            root.applyCss();
            root.layout();
        }

        @SuppressWarnings("unchecked")
        TreeCell<Folder> cellOf(String folderId) {
            for (Node node : tree.lookupAll(".tree-cell")) {
                if (node instanceof TreeCell<?> c && !c.isEmpty() && c.getItem() instanceof Folder f
                        && folderId.equals(f.getId())) {
                    return (TreeCell<Folder>) c;
                }
            }
            throw new AssertionError("no rendered cell for folder " + folderId);
        }
    }
}
