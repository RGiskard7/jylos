package com.example.jylos.ui;

import javafx.scene.Node;

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
}
