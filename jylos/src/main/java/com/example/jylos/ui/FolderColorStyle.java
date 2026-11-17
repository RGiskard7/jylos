package com.example.jylos.ui;

import javafx.scene.Node;

/**
 * A folder's own colour on the row that shows it, in either tree: the row gets
 * {@link #STYLE_CLASS} and the colour as {@code -fx-folder-custom}, which the themes give to the
 * folder's icon (not its name). A selected row keeps the selection's colours (the themes' selected
 * rules are more specific).
 *
 * @author Edu Díaz (RGiskard7)
 * @since 2.6.0
 */
public final class FolderColorStyle {

    /** Style class of a row whose folder has a colour of its own. */
    public static final String STYLE_CLASS = "folder-colored";

    private FolderColorStyle() {
    }

    /** Shows {@code hex} ({@code #rrggbb}) on {@code row}, or no colour for {@code null}. */
    public static void apply(Node row, String hex) {
        UiNodes.setStyleClass(row, STYLE_CLASS, hex != null);
        row.setStyle(hex != null ? "-fx-folder-custom: " + hex + ";" : "");
    }
}
