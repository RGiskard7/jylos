package com.example.jylos.ui.components;

import javafx.beans.value.ChangeListener;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Label;
import javafx.scene.control.Labeled;
import javafx.scene.input.KeyCombination;
import javafx.scene.layout.Region;
import javafx.scene.layout.TilePane;

/**
 * The tiles of the editor's empty state (a new tab, or no note open): four across when the
 * window has room, two by two when it is narrower, one under another when it is narrow.
 * Never three and one. A tile's {@code .empty-state-shortcut} label, whose user data is a
 * {@link KeyCombination} such as {@code Shortcut+O}, shows it as this platform writes it.
 *
 * @author Edu Díaz (RGiskard7)
 * @since 2.6.0
 */
public class EmptyStateTiles extends TilePane {

    private final ChangeListener<Number> widthListener = (obs, old, width) -> fitColumns();

    public EmptyStateTiles() {
        // FXML adds the tiles first and then this pane to its container.
        parentProperty().addListener((obs, oldParent, newParent) -> {
            if (oldParent instanceof Region old) {
                old.widthProperty().removeListener(widthListener);
            }
            if (newParent instanceof Region container) {
                showShortcuts();
                container.widthProperty().addListener(widthListener);
                fitColumns();
            }
        });
    }

    /** A tile's label lives in its button's graphic, which is not a child until the button has a skin. */
    private void showShortcuts() {
        for (Node tile : getChildren()) {
            if (!(tile instanceof Labeled labeled) || labeled.getGraphic() == null) {
                continue;
            }
            for (Node node : labeled.getGraphic().lookupAll(".empty-state-shortcut")) {
                if (node instanceof Label label && label.getUserData() instanceof String shortcut) {
                    label.setText(KeyCombination.valueOf(shortcut).getDisplayText());
                }
            }
        }
    }

    private void fitColumns() {
        Parent parent = getParent();
        if (parent instanceof Region container) {
            double width = container.getWidth() - container.snappedLeftInset() - container.snappedRightInset();
            setPrefColumns(columnsFor(width));
        }
    }

    /** The most of 4, 2 or 1 columns that fit in {@code width}. */
    private int columnsFor(double width) {
        for (int columns : new int[] {4, 2}) {
            if (columns * getPrefTileWidth() + (columns - 1) * getHgap() <= width) {
                return columns;
            }
        }
        return 1;
    }
}
