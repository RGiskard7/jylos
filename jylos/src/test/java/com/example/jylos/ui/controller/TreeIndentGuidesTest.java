package com.example.jylos.ui.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static com.example.jylos.tests.FxTestSupport.onFx;

import java.lang.reflect.Field;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import com.example.jylos.data.models.Folder;
import com.example.jylos.data.models.Note;
import com.example.jylos.data.models.interfaces.Component;
import com.example.jylos.tests.FxTestSupport;
import com.example.jylos.ui.components.ExplorerTree;
import com.example.jylos.ui.theme.UiSizeScale;

import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.TabPane;
import javafx.scene.control.TreeCell;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.paint.Paint;
import javafx.stage.Stage;

/**
 * Open folders draw a thin vertical guide under their arrow, down through everything inside
 * them, as in Obsidian and VS Code: a row shows one guide per folder level above it, and the
 * guide of a level is at the same x in every row. Checked in the Explorer tree, the classic
 * folder tree and the trash.
 */
class TreeIndentGuidesTest {

    @Test
    void explorerTree() throws Exception {
        Assumptions.assumeTrue(FxTestSupport.isFxRuntimeAvailable());
        // Let the reads (posted with runLater) apply, open the folders, then measure.
        AtomicReference<ExplorerTree> holder = new AtomicReference<>();
        onFx(() -> {
            ExplorerTree tree = new ExplorerTree(new ExplorerTree.Source() {
                @Override
                public List<Folder> subfolders(Folder folder) {
                    return switch (folder.getId()) {
                        case "ROOT" -> List.of(new Folder("a", "A"));
                        case "a" -> List.of(new Folder("a/b", "B"));
                        default -> List.of();
                    };
                }

                @Override
                public List<Note> notes(Folder folder) {
                    return List.of(new Note("n-" + folder.getId(), "Nota " + folder.getId(), "x"));
                }
            }, Runnable::run, k -> k);
            tree.setRoot(new Folder("ROOT", "Vault"));
            holder.set(tree);
            return null;
        });
        for (int i = 0; i < 3; i++) {
            onFx(() -> {
                expandAll(holder.get().getView().getRoot());
                return null;
            });
        }
        String guides = onFx(() -> {
            show(holder.get().getView());
            return describe(holder.get().getView());
        });

        assertEquals("A:0 B:1 Nota ROOT:0 Nota a:1 Nota a/b:2 | one x per level", guides);
    }

    /**
     * With a 22 px interface font the arrows grow with their icons (18 px × 22/13), and each
     * level's guide still runs under the centre of its folder's arrow.
     */
    @Test
    void explorerTreeAtALargeFont() throws Exception {
        Assumptions.assumeTrue(FxTestSupport.isFxRuntimeAvailable());
        AtomicReference<ExplorerTree> holder = new AtomicReference<>();
        onFx(() -> {
            ExplorerTree tree = new ExplorerTree(new ExplorerTree.Source() {
                @Override
                public List<Folder> subfolders(Folder folder) {
                    return switch (folder.getId()) {
                        case "ROOT" -> List.of(new Folder("a", "A"));
                        case "a" -> List.of(new Folder("a/b", "B"));
                        default -> List.of();
                    };
                }

                @Override
                public List<Note> notes(Folder folder) {
                    return List.of(new Note("n-" + folder.getId(), "Nota " + folder.getId(), "x"));
                }
            }, Runnable::run, k -> k);
            tree.setRoot(new Folder("ROOT", "Vault"));
            holder.set(tree);
            return null;
        });
        for (int i = 0; i < 3; i++) {
            onFx(() -> {
                expandAll(holder.get().getView().getRoot());
                return null;
            });
        }
        String seen = onFx(() -> {
            TreeView<?> tree = holder.get().getView();
            VBox root = new VBox(tree);
            root.getStyleClass().addAll("root-container", "theme-dark");
            root.setStyle("-fx-font-size: 22px;");
            Scene scene = new Scene(root, 420, 500);
            scene.getStylesheets().add(TreeIndentGuidesTest.class
                    .getResource("/com/example/jylos/ui/css/dark-theme.css").toExternalForm());
            UiSizeScale.apply(scene, 22, 1.0);
            Stage stage = new Stage();
            stage.setScene(scene);
            stage.show();
            root.applyCss();
            root.layout();
            Map<String, double[]> arrows = new TreeMap<>();
            Map<String, List<Double>> guides = new TreeMap<>();
            for (Node node : tree.lookupAll(".tree-cell")) {
                if (!(node instanceof TreeCell<?> cell) || cell.isEmpty() || !(cell.getItem() instanceof Component c)) {
                    continue;
                }
                if (cell.getDisclosureNode() instanceof Region arrow && arrow.isVisible() && arrow.getWidth() > 0) {
                    double minX = arrow.localToScene(arrow.getBoundsInLocal()).getMinX();
                    arrows.put(c.getTitle(), new double[] {arrow.getWidth(), minX + arrow.getWidth() / 2});
                }
                List<Double> xs = new ArrayList<>();
                for (Node guide : cell.lookupAll(".tree-indent-guide")) {
                    xs.add(guide.localToScene(guide.getBoundsInLocal()).getMinX());
                }
                xs.sort(Double::compare);
                guides.put(c.getTitle(), xs);
            }
            stage.close();
            List<String> out = new ArrayList<>();
            arrows.forEach((title, a) -> out.add(title + " arrow " + Math.round(a[0])));
            // Rows inside B have two guides: under A's arrow, then under B's.
            List<Double> inB = guides.get("Nota a/b");
            out.add(inB != null && inB.size() == 2
                    && Math.abs(inB.get(0) - arrows.get("A")[1]) <= 1
                    && Math.abs(inB.get(1) - arrows.get("B")[1]) <= 1
                    ? "guides under the arrows" : "guides at " + inB + " arrows at "
                            + arrows.get("A")[1] + ", " + arrows.get("B")[1]);
            return String.join(", ", out);
        });

        assertEquals("A arrow 30, B arrow 30, guides under the arrows", seen);
    }

    @Test
    void classicFolderTree() throws Exception {
        Assumptions.assumeTrue(FxTestSupport.isFxRuntimeAvailable());
        String guides = onFx(() -> {
            Object controller = load();
            @SuppressWarnings("unchecked")
            TreeItem<Folder> vault = (TreeItem<Folder>) field(controller, "vaultRootItem");
            TreeItem<Folder> a = new TreeItem<>(new Folder("a", "A"));
            TreeItem<Folder> b = new TreeItem<>(new Folder("a/b", "B"));
            a.getChildren().add(b);
            b.getChildren().add(new TreeItem<>(new Folder("a/b/c", "C")));
            vault.getChildren().add(a);
            a.setExpanded(true);
            b.setExpanded(true);
            @SuppressWarnings("unchecked")
            TreeView<Folder> tree = (TreeView<Folder>) field(controller, "folderTreeView");
            return describe(tree, title -> List.of("A", "B", "C").contains(title));
        });

        // The vault root row (titled after the machine's vault) is left out; folders under it
        // show one more guide per level.
        assertEquals("A:1 B:2 C:3 | one x per level", guides);
    }

    @Test
    void trash() throws Exception {
        Assumptions.assumeTrue(FxTestSupport.isFxRuntimeAvailable());
        String guides = onFx(() -> {
            Object controller = load();
            @SuppressWarnings("unchecked")
            TreeView<Component> tree = (TreeView<Component>) field(controller, "trashTreeView");
            TreeItem<Component> folder = new TreeItem<>(new Folder("d", "Borrada"));
            folder.getChildren().add(new TreeItem<>(new Note("n", "Dentro", "x")));
            folder.setExpanded(true);
            tree.getRoot().getChildren().add(folder);
            TabPane tabs = (TabPane) field(controller, "navigationTabPane");
            tabs.getSelectionModel().select(tabs.getTabs().size() - 1);
            return describe(tree);
        });

        assertEquals("Borrada:0 Dentro:1 | one x per level", guides);
    }

    /** With the real theme stylesheets the guides are painted: a visible colour, 1px wide, full row height. */
    @Test
    void guidesArePaintedWithTheThemes() throws Exception {
        Assumptions.assumeTrue(FxTestSupport.isFxRuntimeAvailable());
        for (String theme : List.of("modern-theme.css", "dark-theme.css")) {
            String seen = onFx(() -> {
                Object controller = load();
                @SuppressWarnings("unchecked")
                TreeItem<Folder> vault = (TreeItem<Folder>) field(controller, "vaultRootItem");
                TreeItem<Folder> a = new TreeItem<>(new Folder("a", "A"));
                a.getChildren().add(new TreeItem<>(new Folder("a/b", "B")));
                vault.getChildren().add(a);
                a.setExpanded(true);
                @SuppressWarnings("unchecked")
                TreeView<Folder> tree = (TreeView<Folder>) field(controller, "folderTreeView");
                tree.getScene().getStylesheets().add(
                        TreeIndentGuidesTest.class.getResource("/com/example/jylos/ui/css/" + theme).toExternalForm());
                Parent root = tree.getScene().getRoot();
                root.applyCss();
                root.layout();
                for (Node node : tree.lookupAll(".tree-cell")) {
                    if (node instanceof TreeCell<?> cell && cell.getItem() instanceof Folder f && "B".equals(f.getTitle())) {
                        cell.applyCss();
                        cell.layout();
                        List<String> out = new ArrayList<>();
                        for (Node guide : cell.lookupAll(".tree-indent-guide")) {
                            Region r = (Region) guide;
                            Paint fill = r.getBackground() == null || r.getBackground().getFills().isEmpty()
                                    ? null : r.getBackground().getFills().get(0).getFill();
                            boolean painted = fill instanceof Color c && c.getOpacity() > 0;
                            out.add("painted=" + painted + " w=" + r.getWidth() + " h>0=" + (r.getHeight() > 0)
                                    + " parentVisible=" + guide.getParent().isVisible()
                                    + " inCell=" + (guide.getParent().getParent() == cell));
                        }
                        return out.toString();
                    }
                }
                return "no row B";
            });
            String line = "painted=true w=1.0 h>0=true parentVisible=true inCell=true";
            // "B" is two levels under the vault root row, so two guides.
            assertEquals("[" + line + ", " + line + "]", seen, theme);
        }
    }

    // ------------------------------------------------------------------ helpers

    /** Rows as "title:guides" sorted by title, then whether each level's guide has one x. */
    private static String describe(TreeView<?> tree) {
        return describe(tree, title -> true);
    }

    private static String describe(TreeView<?> tree, Predicate<String> include) {
        Parent root = tree.getScene().getRoot();
        root.applyCss();
        root.layout();
        TreeMap<String, Integer> counts = new TreeMap<>();
        TreeMap<Integer, Set<Double>> xByLevel = new TreeMap<>();
        for (Node node : tree.lookupAll(".tree-cell")) {
            if (!(node instanceof TreeCell<?> cell) || cell.isEmpty() || cell.getItem() == null) {
                continue;
            }
            List<Double> xs = new ArrayList<>();
            for (Node guide : cell.lookupAll(".tree-indent-guide")) {
                if (guide.isVisible() && guide.getParent() != null && guide.getParent().isVisible()) {
                    xs.add(guide.localToScene(guide.getBoundsInLocal()).getMinX());
                }
            }
            xs.sort(Double::compare);
            String title = cell.getItem() instanceof Component c ? c.getTitle() : String.valueOf(cell.getItem());
            if (!include.test(title)) {
                continue;
            }
            counts.put(title, xs.size());
            for (int i = 0; i < xs.size(); i++) {
                xByLevel.computeIfAbsent(i, k -> new TreeSet<>()).add(Math.rint(xs.get(i)));
            }
        }
        StringBuilder out = new StringBuilder();
        counts.forEach((title, n) -> out.append(title).append(':').append(n).append(' '));
        boolean onePerLevel = xByLevel.values().stream().allMatch(set -> set.size() == 1);
        out.append("| ").append(onePerLevel ? "one x per level" : "levels drift " + xByLevel);
        return out.toString();
    }

    private static Object loaded;

    private static Object load() throws Exception {
        URL fxml = TreeIndentGuidesTest.class.getClassLoader().getResource("com/example/jylos/ui/view/SidebarView.fxml");
        ResourceBundle bundle = ResourceBundle.getBundle("com.example.jylos.i18n.messages", Locale.forLanguageTag("es"));
        FXMLLoader loader = new FXMLLoader(fxml, bundle);
        Parent root = loader.load();
        Stage stage = new Stage();
        stage.setScene(new Scene(root, 320, 600));
        stage.show();
        loaded = loader.getController();
        return loaded;
    }

    private static void show(TreeView<?> tree) {
        Stage stage = new Stage();
        stage.setScene(new Scene(tree, 320, 400));
        stage.show();
    }

    private static void expandAll(TreeItem<?> item) {
        for (TreeItem<?> child : item.getChildren()) {
            if (child.getValue() instanceof Folder) {
                child.setExpanded(true);
                expandAll(child);
            }
        }
    }

    private static Object field(Object target, String name) throws Exception {
        Field f = target.getClass().getDeclaredField(name);
        f.setAccessible(true);
        return f.get(target);
    }
}
