package com.example.jylos.ui.theme;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import com.example.jylos.tests.FxTestSupport;

import javafx.application.Platform;
import javafx.css.PseudoClass;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Control;
import javafx.scene.control.IndexedCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundFill;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

/**
 * Lists and trees fill their height with empty rows. Hovering one of those rows lit it up
 * as if it were a note or a folder, because the theme's hover rules did not require the
 * cell to hold an item. A row with nothing in it must look the same hovered or not, while
 * a real row must still react to the pointer.
 */
class EmptyRowHoverTest {

    private static final PseudoClass HOVER = PseudoClass.getPseudoClass("hover");

    static List<Arguments> themesAndContainers() {
        List<String> themes = List.of("modern-theme.css", "dark-theme.css");
        List<String> containers = List.of("list", "notes-list", "sidebar-list", "tree", "sidebar-tree");
        return themes.stream()
                .flatMap(theme -> containers.stream().map(container -> Arguments.of(theme, container)))
                .toList();
    }

    @ParameterizedTest(name = "{0} / {1}")
    @MethodSource("themesAndContainers")
    void anEmptyRowDoesNotChangeOnHoverButAFilledOneDoes(String theme, String container) throws Exception {
        Assumptions.assumeTrue(FxTestSupport.isFxRuntimeAvailable());

        AtomicReference<Throwable> failure = new AtomicReference<>();
        Object[] result = new Object[4];
        CountDownLatch done = new CountDownLatch(1);
        Platform.runLater(() -> {
            try {
                Control control = buildControl(container);
                VBox root = new VBox(control);
                root.getStyleClass().add("root-container");
                Scene scene = new Scene(root, 300, 400);
                scene.getStylesheets().add(EmptyRowHoverTest.class.getClassLoader()
                        .getResource("com/example/jylos/ui/css/" + theme).toExternalForm());
                Stage stage = new Stage();
                stage.setScene(scene);
                stage.show();
                root.applyCss();
                root.layout();

                IndexedCell<?> filled = findCell(control, false);
                IndexedCell<?> empty = findCell(control, true);
                assertNotNull(filled, "no filled row was rendered");
                assertNotNull(empty, "no empty row was rendered");

                result[0] = fills(empty);
                result[1] = fillsWhileHovered(empty, root);
                result[2] = fills(filled);
                result[3] = fillsWhileHovered(filled, root);
                stage.close();
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
        assertNotEquals(result[2], result[3],
                "a filled row must change its background on hover (otherwise this test proves nothing)");
        assertEquals(result[0], result[1],
                "an empty row must not change its background on hover, but went from "
                        + result[0] + " to " + result[1]);
    }

    private static Control buildControl(String container) {
        if (container.endsWith("tree")) {
            TreeItem<String> rootItem = new TreeItem<>("Vault");
            rootItem.getChildren().add(new TreeItem<>("Folder"));
            rootItem.setExpanded(true);
            TreeView<String> tree = new TreeView<>(rootItem);
            if (!container.equals("tree")) {
                tree.getStyleClass().add(container);
            }
            return tree;
        }
        ListView<String> list = new ListView<>();
        list.getItems().add("Note");
        if (!container.equals("list")) {
            list.getStyleClass().add(container);
        }
        return list;
    }

    private static IndexedCell<?> findCell(Control control, boolean wantEmpty) {
        for (Node node : control.lookupAll(".indexed-cell")) {
            if (node instanceof IndexedCell<?> cell && cell.isVisible() && cell.isEmpty() == wantEmpty) {
                return cell;
            }
        }
        return null;
    }

    private static List<String> fillsWhileHovered(IndexedCell<?> cell, VBox root) {
        cell.pseudoClassStateChanged(HOVER, true);
        root.applyCss();
        List<String> hovered = fills(cell);
        cell.pseudoClassStateChanged(HOVER, false);
        root.applyCss();
        return hovered;
    }

    private static List<String> fills(IndexedCell<?> cell) {
        Background background = cell.getBackground();
        if (background == null) {
            return List.of();
        }
        return background.getFills().stream()
                .map(BackgroundFill::getFill)
                .map(Objects::toString)
                .toList();
    }
}
