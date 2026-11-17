package com.example.jylos.ui.components;

import static com.example.jylos.tests.FxTestSupport.onFx;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.kordamp.ikonli.javafx.FontIcon;

import com.example.jylos.data.models.Folder;
import com.example.jylos.data.models.Note;
import com.example.jylos.data.models.interfaces.Component;
import com.example.jylos.tests.FxTestSupport;

import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.TreeCell;
import javafx.scene.control.TreeItem;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

/**
 * A folder given a colour shows it on its icon in the Explorer (as Obsidian's Iconize does); its
 * name keeps the usual colour, and a selected row keeps the selection's look.
 */
class ExplorerFolderColorTest {

    @Test
    void aFoldersColourShowsOnItsIconOnlyAndFollowsChanges() throws Exception {
        Assumptions.assumeTrue(FxTestSupport.isFxRuntimeAvailable());
        Map<String, String> colours = new HashMap<>(Map.of("w", "#e93147"));
        AtomicReference<ExplorerTree> holder = new AtomicReference<>();
        AtomicReference<VBox> rootHolder = new AtomicReference<>();
        onFx(() -> {
            ExplorerTree tree = new ExplorerTree(new ExplorerTree.Source() {
                @Override
                public List<Folder> subfolders(Folder folder) {
                    return "ROOT".equals(folder.getId()) ? List.of(new Folder("w", "Trabajo"), new Folder("i", "Ideas")) : List.of();
                }

                @Override
                public List<Note> notes(Folder folder) {
                    return List.of();
                }
            }, Runnable::run, k -> k);
            tree.setRoot(new Folder("ROOT", "Vault"));
            tree.setFolderColor(folder -> colours.get(folder.getId()));
            VBox root = new VBox(tree.getView());
            root.getStyleClass().addAll("root-container", "root");
            tree.getView().getStyleClass().add("sidebar-tree");
            Scene scene = new Scene(root, 400, 300);
            scene.getStylesheets().add(ExplorerFolderColorTest.class
                    .getResource("/com/example/jylos/ui/css/dark-theme.css").toExternalForm());
            Stage stage = new Stage();
            stage.setScene(scene);
            stage.show();
            holder.set(tree);
            rootHolder.set(root);
            return null;
        });

        Map<String, String> first = onFx(() -> looks(holder.get(), rootHolder.get()));
        colours.put("w", "#08b94e");
        colours.put("i", "#7852ee");
        Map<String, String> changed = onFx(() -> {
            holder.get().refreshDecorations();
            return looks(holder.get(), rootHolder.get());
        });
        Map<String, String> selected = onFx(() -> {
            ExplorerTree tree = holder.get();
            for (TreeItem<Component> child : tree.getView().getRoot().getChildren()) {
                if (child.getValue() instanceof Folder f && "w".equals(f.getId())) {
                    tree.getView().getSelectionModel().select(tree.getView().getRow(child));
                }
            }
            Map<String, String> out = looks(tree, rootHolder.get());
            ((Stage) rootHolder.get().getScene().getWindow()).close();
            return out;
        });

        String usualName = first.get("Ideas").split(" ")[1];
        assertEquals("0xe93147ff " + usualName, first.get("Trabajo"), "the icon in the folder's colour, the name as usual");
        assertNotEquals(first.get("Trabajo"), first.get("Ideas"), "a folder without a colour looks as usual");
        assertEquals("0x08b94eff " + usualName, changed.get("Trabajo"), "a new colour shows once the rows are refreshed");
        assertEquals("0x7852eeff " + usualName, changed.get("Ideas"));
        assertNotEquals("0x08b94eff", selected.get("Trabajo").split(" ")[0], "a selected row keeps the selection's colours");
    }

    /** "title -> icon colour, name colour" for each folder row. */
    private static Map<String, String> looks(ExplorerTree tree, VBox root) {
        root.applyCss();
        root.layout();
        Map<String, String> out = new HashMap<>();
        for (Node node : tree.getView().lookupAll(".tree-cell")) {
            if (node instanceof TreeCell<?> cell && !cell.isEmpty() && cell.getItem() instanceof Folder folder) {
                FontIcon icon = (FontIcon) cell.lookup(".explorer-folder-icon");
                Label name = (Label) cell.lookup(".explorer-cell-name");
                out.put(folder.getTitle(), icon.getIconColor() + " " + name.getTextFill());
            }
        }
        return out;
    }
}
