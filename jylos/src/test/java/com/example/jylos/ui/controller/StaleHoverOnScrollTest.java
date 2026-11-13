package com.example.jylos.ui.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static com.example.jylos.tests.FxTestSupport.onFx;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.ResourceBundle;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import com.example.jylos.data.models.Folder;
import com.example.jylos.data.models.Note;
import com.example.jylos.data.models.interfaces.Component;
import com.example.jylos.tests.FxTestSupport;

import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Cell;
import javafx.scene.control.ListView;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.stage.Stage;

/**
 * JavaFX updates a row's :hover only when the mouse moves. Lists and trees reuse rows while
 * scrolling, so scrolling under a still mouse handed the highlight of the row that left the
 * view to whatever row reused it. Checked on the classic layout's notes list, folder tree and
 * trash.
 */
class StaleHoverOnScrollTest {

    @Test
    void notesList() throws Exception {
        Assumptions.assumeTrue(FxTestSupport.isFxRuntimeAvailable());
        List<String> hovered = onFx(() -> {
            Parent root = load("NotesListView.fxml");
            NotesListController notes = (NotesListController) controller;
            notes.wire(null, null, null, null, bundle(), n -> { }, n -> { }, n -> { }, (n, m) -> { },
                    m -> { });
            @SuppressWarnings("unchecked")
            ListView<Note> list = (ListView<Note>) field(NotesListController.class, notes, "notesListView");
            for (int i = 10; i < 70; i++) {
                Note note = new Note("Nota " + i, "x");
                note.setId("n" + i);
                list.getItems().add(note);
            }
            show(root);
            hoverRowShowing(list, "Nota 10");
            list.scrollTo(40);
            root.layout();
            return hoveredOtherThan(list, "Nota 10");
        });
        assertEquals(List.of(), hovered);
    }

    @Test
    void folderTree() throws Exception {
        Assumptions.assumeTrue(FxTestSupport.isFxRuntimeAvailable());
        List<String> hovered = onFx(() -> {
            Parent root = load("SidebarView.fxml");
            @SuppressWarnings("unchecked")
            TreeItem<Folder> vaultRoot = (TreeItem<Folder>) field(SidebarController.class, controller, "vaultRootItem");
            for (int i = 10; i < 70; i++) {
                vaultRoot.getChildren().add(new TreeItem<>(new Folder("f" + i, "Carpeta " + i)));
            }
            @SuppressWarnings("unchecked")
            TreeView<Folder> tree = (TreeView<Folder>) field(SidebarController.class, controller, "folderTreeView");
            show(root);
            hoverRowShowing(tree, "Carpeta 10");
            tree.scrollTo(40);
            root.layout();
            return hoveredOtherThan(tree, "Carpeta 10");
        });
        assertEquals(List.of(), hovered);
    }

    @Test
    void trash() throws Exception {
        Assumptions.assumeTrue(FxTestSupport.isFxRuntimeAvailable());
        List<String> hovered = onFx(() -> {
            Parent root = load("SidebarView.fxml");
            @SuppressWarnings("unchecked")
            TreeView<Component> tree = (TreeView<Component>) field(SidebarController.class, controller, "trashTreeView");
            for (int i = 10; i < 70; i++) {
                Note note = new Note("Borrada " + i, "x");
                note.setId("t" + i);
                tree.getRoot().getChildren().add(new TreeItem<>(note));
            }
            SidebarController sidebar = (SidebarController) controller;
            Field tab = SidebarController.class.getDeclaredField("navigationTabPane");
            tab.setAccessible(true);
            javafx.scene.control.TabPane tabs = (javafx.scene.control.TabPane) tab.get(sidebar);
            tabs.getSelectionModel().select(tabs.getTabs().size() - 1);
            show(root);
            hoverRowShowing(tree, "Borrada 10");
            tree.scrollTo(40);
            root.layout();
            return hoveredOtherThan(tree, "Borrada 10");
        });
        assertEquals(List.of(), hovered);
    }

    /** The trash uses the Explorer's icons (it is the same kind of mixed tree), not "[+]"/"[/]". */
    @Test
    void trashRowsUseTheFolderAndDocumentIconsAndTheArrowFollowsExpansion() throws Exception {
        Assumptions.assumeTrue(FxTestSupport.isFxRuntimeAvailable());
        List<String> seen = onFx(() -> {
            Parent root = load("SidebarView.fxml");
            @SuppressWarnings("unchecked")
            TreeView<Component> tree = (TreeView<Component>) field(SidebarController.class, controller, "trashTreeView");
            TreeItem<Component> folder = new TreeItem<>(new Folder("d", "Borrada"));
            Note inside = new Note("Dentro", "x");
            inside.setId("n");
            folder.getChildren().add(new TreeItem<>(inside));
            tree.getRoot().getChildren().add(folder);
            javafx.scene.control.TabPane tabs = (javafx.scene.control.TabPane) field(SidebarController.class,
                    controller, "navigationTabPane");
            tabs.getSelectionModel().select(tabs.getTabs().size() - 1);
            show(root);
            List<String> out = new ArrayList<>();
            javafx.scene.control.TreeCell<?> row = (javafx.scene.control.TreeCell<?>) rowShowing(tree, "Borrada");
            out.add(iconOf(row));
            out.add(String.valueOf(row.getDisclosureNode().getRotate()));
            folder.setExpanded(true);
            root.layout();
            out.add(String.valueOf(row.getDisclosureNode().getRotate()));
            out.add(iconOf(rowShowing(tree, "Dentro")));
            return out;
        });
        assertEquals(List.of("fth-folder", "0.0", "90.0", "fth-file-text"), seen);
    }

    private static Node rowShowing(Node container, String title) {
        for (Node node : container.lookupAll(".cell")) {
            if (node instanceof Cell<?> cell && !cell.isEmpty() && title.equals(titleOf(cell.getItem()))) {
                return cell;
            }
        }
        throw new AssertionError("no row shows " + title);
    }

    private static String iconOf(Node row) {
        for (Node node : row.lookupAll(".ikonli-font-icon")) {
            if (node instanceof org.kordamp.ikonli.javafx.FontIcon icon && icon.getParent() instanceof javafx.scene.layout.HBox) {
                return icon.getIconLiteral();
            }
        }
        for (Node node : row.lookupAll(".label")) {
            if (node instanceof javafx.scene.control.Label label && label.getText() != null
                    && label.getText().startsWith("[")) {
                return label.getText();
            }
        }
        return "none";
    }

    // ------------------------------------------------------------------ helpers

    private static Object controller;

    private static Parent load(String view) throws Exception {
        URL fxml = StaleHoverOnScrollTest.class.getClassLoader().getResource("com/example/jylos/ui/view/" + view);
        FXMLLoader loader = new FXMLLoader(fxml, bundle());
        Parent root = loader.load();
        controller = loader.getController();
        return root;
    }

    private static ResourceBundle bundle() {
        return ResourceBundle.getBundle("com.example.jylos.i18n.messages", Locale.forLanguageTag("es"));
    }

    private static void show(Parent root) {
        Stage stage = new Stage();
        stage.setScene(new Scene(root, 320, 480));
        stage.show();
        root.applyCss();
        root.layout();
    }

    private static void hoverRowShowing(Node container, String title) throws Exception {
        for (Node node : container.lookupAll(".cell")) {
            if (node instanceof Cell<?> cell && !cell.isEmpty() && title.equals(titleOf(cell.getItem()))) {
                Method m = Node.class.getDeclaredMethod("setHover", boolean.class);
                m.setAccessible(true);
                m.invoke(cell, true);
                return;
            }
        }
        throw new AssertionError("no row shows " + title);
    }

    private static List<String> hoveredOtherThan(Node container, String title) {
        List<String> out = new ArrayList<>();
        for (Node node : container.lookupAll(".cell")) {
            if (node instanceof Cell<?> cell && cell.isVisible() && cell.isHover() && !cell.isEmpty()
                    && !title.equals(titleOf(cell.getItem()))) {
                out.add(titleOf(cell.getItem()));
            }
        }
        return out;
    }

    private static String titleOf(Object item) {
        return item instanceof Component c ? c.getTitle() : String.valueOf(item);
    }

    private static Object field(Class<?> type, Object target, String name) throws Exception {
        Field f = type.getDeclaredField(name);
        f.setAccessible(true);
        return f.get(target);
    }
}
