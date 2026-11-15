package com.example.jylos.ui.components;

import java.util.List;

import javafx.collections.ObservableList;
import javafx.scene.Node;
import javafx.scene.control.TreeCell;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.control.skin.TreeCellSkin;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;

/**
 * Indent guides for a tree row: one thin vertical line per folder level above the row, under
 * that folder's arrow, so the lines of consecutive rows join into a guide that runs down
 * through everything inside an open folder (as in Obsidian and VS Code).
 *
 * <p>A cell owns one instance and calls {@link #layout} from its {@code layoutChildren},
 * after {@code super.layoutChildren()}. The lines live in a non-managed, mouse-transparent
 * layer put first among the cell's children, so they are drawn over the row's background
 * (hover, selection) and under its text, and never move anything.</p>
 *
 * @author Edu Díaz (RGiskard7)
 * @since 2.6.0
 */
public final class TreeIndentGuides {

    /** Style class of each line (colour set by the theme). */
    public static final String GUIDE_STYLE = "tree-indent-guide";
    /** JavaFX's own default indent per level, used if the skin is not a {@link TreeCellSkin}. */
    private static final double DEFAULT_INDENT = 10;

    private final Pane layer = new Pane();

    public TreeIndentGuides() {
        layer.setManaged(false);
        layer.setMouseTransparent(true);
        layer.getStyleClass().add("tree-indent-guides");
    }

    /**
     * Places the guides for {@code cell}'s current row. {@code children} is the cell's own
     * children list (protected in {@code Parent}, so the cell passes it in).
     */
    public void layout(TreeCell<?> cell, ObservableList<Node> children) {
        if (!children.contains(layer)) {
            children.add(0, layer);
        }
        TreeView<?> tree = cell.getTreeView();
        TreeItem<?> item = cell.getTreeItem();
        if (cell.isEmpty() || tree == null || item == null) {
            layer.getChildren().clear();
            return;
        }
        int level = tree.getTreeItemLevel(item) - (tree.isShowRoot() ? 0 : 1);
        double indent = cell.getSkin() instanceof TreeCellSkin<?> skin ? skin.getIndent() : DEFAULT_INDENT;
        double arrowCentre = TreeDisclosureArrow.SIZE / 2;
        double left = cell.snappedLeftInset();
        double height = cell.getHeight();

        List<Node> lines = layer.getChildren();
        while (lines.size() > Math.max(0, level)) {
            lines.remove(lines.size() - 1);
        }
        while (lines.size() < level) {
            Region line = new Region();
            line.getStyleClass().add(GUIDE_STYLE);
            // Not managed: the layer (a Pane) would otherwise resize it back to its preferred
            // size, 0 x 0, on every layout pass, leaving an invisible line.
            line.setManaged(false);
            lines.add(line);
        }
        for (int k = 0; k < level; k++) {
            double x = Math.floor(left + indent * k + arrowCentre);
            ((Region) lines.get(k)).resizeRelocate(x, 0, 1, height);
        }
        layer.resizeRelocate(0, 0, cell.getWidth(), height);
    }
}
