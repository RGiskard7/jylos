package com.example.jylos.ui.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import com.example.jylos.tests.FxTestSupport;

import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.TreeCell;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;

/**
 * JavaFX's {@code TreeCellSkin} caches one shared disclosure-arrow width per
 * {@code TreeView}, seeded with a hardcoded 18px fallback and only ever overwritten
 * once a real disclosure node measures WIDER than the current cached value — see
 * {@code TreeCellSkin.layoutChildren}, comment "RT-19656: default width of default
 * disclosure node" (javafx-controls 23.0.2 source). Left to JavaFX's own default arrow,
 * whichever rows render before the first expandable item gets measured use the stale
 * 18px offset while later rows use the corrected, larger one — siblings at the same
 * depth end up indented differently. {@code SidebarController.buildFixedSizeDisclosureArrow()}
 * fixes this by giving every cell a custom arrow locked at exactly 18px (matching the
 * fallback exactly, not merely staying under it — a branch row uses this arrow's own
 * real measured width while a leaf row uses the cache, so the two must land on the
 * same number or they'll simply disagree by a different, but still constant, amount).
 */
class SidebarFolderTreeAlignmentTest {

    @Test
    void theFixedArrowIsSmallerThanJavaFxsHardcodedDisclosureCacheFallback() throws Exception {
        Assumptions.assumeTrue(FxTestSupport.isFxRuntimeAvailable());

        CountDownLatch done = new CountDownLatch(1);
        double[] width = new double[1];
        Platform.runLater(() -> {
            try {
                StackPane arrow = invokeBuildFixedSizeDisclosureArrow();
                width[0] = arrow.prefWidth(-1);
            } catch (Exception e) {
                throw new RuntimeException(e);
            } finally {
                done.countDown();
            }
        });

        assertTrue(done.await(30, TimeUnit.SECONDS), "test never completed on the FX thread");
        // JavaFX's own fallback (TreeCellSkin.java, javafx-controls 23.0.2) is exactly 18px,
        // and the shared per-TreeView cache only grows when a measured width is STRICTLY
        // greater than that. Anything <= 18 here means the cache can never be corrected
        // away from 18 by this arrow, which is the whole point of the fix.
        assertTrue(width[0] <= 18,
                "the custom disclosure arrow must stay at/under JavaFX's 18px cache fallback "
                        + "so the shared per-TreeView cache never updates mid-render — was " + width[0]);
    }

    @Test
    void leafAndExpandableSiblingsAtTheSameDepthIndentIdentically() throws Exception {
        Assumptions.assumeTrue(FxTestSupport.isFxRuntimeAvailable());

        CountDownLatch done = new CountDownLatch(1);
        double[] leafX = new double[1];
        double[] branchX = new double[1];
        Platform.runLater(() -> {
            try {
                TreeItem<String> root = new TreeItem<>("root");
                root.setExpanded(true);

                // A leaf sibling and an expandable (branch) sibling at the SAME depth,
                // leaf listed FIRST so it's the one that would render before any disclosure
                // node gets measured — the exact ordering that exposed the original bug.
                TreeItem<String> leaf = new TreeItem<>("leaf folder");
                TreeItem<String> branch = new TreeItem<>("branch folder");
                branch.getChildren().add(new TreeItem<>("child"));
                branch.setExpanded(true);
                root.getChildren().addAll(leaf, branch);

                TreeView<String> tree = new TreeView<>(root);
                tree.setShowRoot(false);
                tree.setCellFactory(tv -> new TreeCell<String>() {
                    {
                        setDisclosureNode(invokeBuildFixedSizeDisclosureArrow());
                    }

                    @Override
                    protected void updateItem(String value, boolean empty) {
                        super.updateItem(value, empty);
                        if (empty || value == null) {
                            setText(null);
                            setGraphic(null);
                        } else {
                            Label label = new Label(value);
                            setGraphic(label);
                            setText(null);
                        }
                    }
                });

                Scene scene = new Scene(tree, 300, 200);
                Stage stage = new Stage();
                stage.setScene(scene);
                stage.show();
                tree.applyCss();
                tree.layout();

                leafX[0] = findLabelInParentX(tree, "leaf folder");
                branchX[0] = findLabelInParentX(tree, "branch folder");
            } catch (Exception e) {
                throw new RuntimeException(e);
            } finally {
                done.countDown();
            }
        });

        assertTrue(done.await(30, TimeUnit.SECONDS), "test never completed on the FX thread");
        assertEquals(leafX[0], branchX[0], 0.5,
                "a leaf folder and an expandable folder at the same tree depth must indent "
                        + "identically — leaf was at x=" + leafX[0] + ", branch (expandable) at x=" + branchX[0]);
    }

    @Test
    void noteCountsAlignToACommonColumnRegardlessOfFolderNameLength() throws Exception {
        Assumptions.assumeTrue(FxTestSupport.isFxRuntimeAvailable());

        CountDownLatch done = new CountDownLatch(1);
        double[] shortNameCountX = new double[1];
        double[] longNameCountX = new double[1];
        Platform.runLater(() -> {
            try {
                TreeView<String> tree = buildCountAlignmentTree("A", "A moderately longer folder name");
                Scene scene = new Scene(tree, 260, 200);
                Stage stage = new Stage();
                stage.setScene(scene);
                stage.show();
                tree.applyCss();
                tree.layout();

                shortNameCountX[0] = countLabelSceneX(tree, "A");
                longNameCountX[0] = countLabelSceneX(tree, "A moderately longer folder name");
            } catch (Exception e) {
                throw new RuntimeException(e);
            } finally {
                done.countDown();
            }
        });

        assertTrue(done.await(30, TimeUnit.SECONDS), "test never completed on the FX thread");
        assertEquals(shortNameCountX[0], longNameCountX[0], 0.5,
                "the note count must land on the same column for every row regardless of how "
                        + "long the folder name is — short-name row's count was at x="
                        + shortNameCountX[0] + ", long-name row's at x=" + longNameCountX[0]);
    }

    /**
     * A first fix attempt forced the row's container to a guessed preferred width
     * (the cell's own width minus a flat constant) and locked its max width there too —
     * that guess didn't account for how much space a given row's indent/disclosure
     * offset actually leaves, and since a plain {@link Label} never shrinks below its
     * own text's natural width, an oversized guess just let the name refuse to shrink
     * and pushed the count (and the row's real content) past the tree's right edge,
     * forcing a permanent horizontal scrollbar and hiding the count entirely — exactly
     * what was reported after the previous fix shipped. This asserts the count is
     * inside the TreeView's own visible bounds even in a realistically narrow sidebar
     * with a long folder name, which is the actual user-observable requirement.
     */
    @Test
    void noteCountStaysInsideTheVisibleTreeWidthEvenWithALongFolderName() throws Exception {
        Assumptions.assumeTrue(FxTestSupport.isFxRuntimeAvailable());

        CountDownLatch done = new CountDownLatch(1);
        double[] treeRightEdge = new double[1];
        double[] countRightEdge = new double[1];
        Platform.runLater(() -> {
            try {
                String longName = "009 - Estudio del sufrimiento y sus consecuencias";
                TreeView<String> tree = buildCountAlignmentTree(longName);
                // Matches the real sidebar's narrow default width, not a generous test tree.
                Scene scene = new Scene(tree, 220, 120);
                Stage stage = new Stage();
                stage.setScene(scene);
                stage.show();
                tree.applyCss();
                tree.layout();

                treeRightEdge[0] = tree.localToScene(tree.getBoundsInLocal()).getMaxX();
                Label countLabel = findCountLabel(tree, longName);
                countRightEdge[0] = countLabel.localToScene(countLabel.getBoundsInLocal()).getMaxX();
            } catch (Exception e) {
                throw new RuntimeException(e);
            } finally {
                done.countDown();
            }
        });

        assertTrue(done.await(30, TimeUnit.SECONDS), "test never completed on the FX thread");
        assertTrue(countRightEdge[0] <= treeRightEdge[0] + 0.5,
                "a long folder name must not push the note count past the tree's own visible "
                        + "right edge (that's what forces permanent horizontal scroll and hides "
                        + "the count) — tree right edge at x=" + treeRightEdge[0]
                        + ", count's right edge at x=" + countRightEdge[0]);
    }

    /** Builds a TreeView whose cells use the exact same construction as
     *  {@code SidebarController.setupCellFactories()}'s folder cell: a name Label that
     *  grows AND shrinks (min width 0) to fill whatever room the row actually has,
     *  followed by a fixed-size count Label — no separate spacer, no guessed width. */
    private static TreeView<String> buildCountAlignmentTree(String... folderNames) {
        TreeItem<String> root = new TreeItem<>("root");
        root.setExpanded(true);
        for (String name : folderNames) {
            root.getChildren().add(new TreeItem<>(name));
        }

        TreeView<String> tree = new TreeView<>(root);
        tree.setShowRoot(false);
        tree.setCellFactory(tv -> new TreeCell<String>() {
            @Override
            protected void updateItem(String value, boolean empty) {
                super.updateItem(value, empty);
                if (empty || value == null) {
                    setText(null);
                    setGraphic(null);
                    return;
                }
                HBox container = new HBox(6);
                container.setAlignment(Pos.CENTER_LEFT);
                Label nameLabel = new Label(value);
                nameLabel.setMaxWidth(Double.MAX_VALUE);
                nameLabel.setMinWidth(0);
                nameLabel.setPrefWidth(0);
                HBox.setHgrow(nameLabel, Priority.ALWAYS);
                Label countLabel = new Label("(3)");
                countLabel.setId("count-" + value.replace(" ", "_"));
                container.getChildren().addAll(nameLabel, countLabel);
                setGraphic(container);
                setText(null);
            }
        });
        return tree;
    }

    private static double countLabelSceneX(TreeView<String> tree, String folderName) {
        return findCountLabel(tree, folderName).localToScene(
                findCountLabel(tree, folderName).getBoundsInLocal()).getMinX();
    }

    private static Label findCountLabel(TreeView<String> tree, String folderName) {
        String id = "count-" + folderName.replace(" ", "_");
        for (javafx.scene.Node node : tree.lookupAll("#" + id)) {
            if (node instanceof Label label) {
                return label;
            }
        }
        throw new IllegalStateException("Could not find count label for: " + folderName);
    }

    private static StackPane invokeBuildFixedSizeDisclosureArrow() {
        try {
            Method m = SidebarController.class.getDeclaredMethod("buildFixedSizeDisclosureArrow");
            m.setAccessible(true);
            return (StackPane) m.invoke(null);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    /** Walks the tree's real rendered cells looking for the graphic Label with this text,
     *  returning its x position relative to the TreeView (post-layout, so indentation
     *  from the disclosure node/skin is already baked into the number). */
    private static double findLabelInParentX(TreeView<String> tree, String text) {
        for (javafx.scene.Node node : tree.lookupAll(".label")) {
            if (node instanceof Label label && text.equals(label.getText())) {
                return label.localToScene(label.getBoundsInLocal()).getMinX();
            }
        }
        throw new IllegalStateException("Could not find rendered label for: " + text);
    }
}
