package com.example.jylos.ui.components;

import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;

/**
 * The mouse gestures that open a note in a new editor tab instead of the current one,
 * as in VS Code and Obsidian: Cmd+click on macOS / Ctrl+click elsewhere, and middle click.
 */
public final class NewTabGesture {

    private NewTabGesture() {
    }

    public static boolean opensInNewTab(MouseEvent event) {
        if (event == null) {
            return false;
        }
        return event.getButton() == MouseButton.MIDDLE
                || (event.getButton() == MouseButton.PRIMARY && event.isShortcutDown());
    }
}
