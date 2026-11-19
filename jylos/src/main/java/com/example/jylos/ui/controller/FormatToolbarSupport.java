package com.example.jylos.ui.controller;

import java.util.function.Consumer;

import com.example.jylos.ui.UiNodes;

import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.ButtonBase;

/**
 * The format bar under the editor, shown or folded away. Its arrow folds it; folded, it leaves
 * a notch at the bottom of the editor that brings it back. Preferences, the View menu and the
 * palette show and hide it too. A change made here (arrow, notch, command) is handed to
 * {@link #bind}'s listener to be remembered; one from Preferences is saved by Preferences.
 *
 * @author Edu Díaz (RGiskard7)
 * @since 2.6.0
 */
final class FormatToolbarSupport {

    private Node bar;
    private Node notch;
    private boolean visible = true;
    private Consumer<Boolean> onChange = shown -> { };

    /** Takes the bar and, from the editor pane, the arrow and the notch, by their FXML ids. */
    void wire(Node bar, Parent editorPane) {
        this.bar = bar;
        if (bar instanceof Parent parent && parent.lookup("#collapseFormatToolbarBtn") instanceof ButtonBase arrow) {
            arrow.setOnAction(e -> toggle());
        }
        if (editorPane != null && editorPane.lookup("#formatToolbarNotch") instanceof ButtonBase button) {
            notch = button;
            button.setOnAction(e -> toggle());
        }
        apply();
    }

    /** Starts shown or folded, and hands every later change made here to {@code onChange}. */
    void bind(boolean shown, Consumer<Boolean> onChange) {
        this.onChange = onChange != null ? onChange : s -> { };
        setVisible(shown);
    }

    /** Shows or folds the bar without handing the change on (Preferences saves its own). */
    void setVisible(boolean shown) {
        visible = shown;
        apply();
    }

    /** Folds a shown bar, shows a folded one (arrow, notch, View menu, palette). */
    void toggle() {
        setVisible(!visible);
        onChange.accept(visible);
    }

    boolean isVisible() {
        return visible;
    }

    private void apply() {
        UiNodes.show(bar, visible);
        if (notch != null) {
            // Laid out over the editor's bottom edge, so only shown, never taken out of the layout.
            notch.setVisible(!visible);
        }
    }
}
