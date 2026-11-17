package com.example.jylos.ui;

import javafx.scene.Node;
import javafx.scene.control.Labeled;
import javafx.scene.control.OverrunStyle;
import javafx.scene.layout.Region;

/**
 * Small helpers for JavaFX nodes shared by the UI.
 *
 * @author Edu Díaz (RGiskard7)
 * @since 2.6.0
 */
public final class UiNodes {

    private UiNodes() {
    }

    /**
     * Shows or hides {@code node}, and makes it take layout space only while shown (a hidden
     * node that stays managed leaves a gap). A {@code null} node is ignored.
     */
    public static void show(Node node, boolean shown) {
        if (node != null) {
            node.setVisible(shown);
            node.setManaged(shown);
        }
    }

    /** Adds {@code styleClass} to {@code node} if {@code on} (once), and removes it otherwise. */
    public static void setStyleClass(Node node, String styleClass, boolean on) {
        node.getStyleClass().removeAll(styleClass);
        if (on) {
            node.getStyleClass().add(styleClass);
        }
    }

    /**
     * Keeps a label or button that shows a symbol (×, ●, ▼) whole. In a row too narrow for
     * everything, the text beside it gives way, and the symbol never turns into an ellipsis:
     * it is never made narrower than it needs, and if a fraction of a pixel is still missing
     * it is clipped rather than replaced by "…". Returns {@code symbol}.
     */
    public static <T extends Labeled> T keepWhole(T symbol) {
        symbol.setMinWidth(Region.USE_PREF_SIZE);
        symbol.setTextOverrun(OverrunStyle.CLIP);
        return symbol;
    }
}
