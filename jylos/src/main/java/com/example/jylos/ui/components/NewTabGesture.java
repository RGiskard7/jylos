package com.example.jylos.ui.components;

import javafx.scene.control.ButtonBase;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;

/**
 * The mouse gestures that open a note in a new editor tab instead of the current one,
 * as in VS Code and Obsidian: Cmd+click on macOS / Ctrl+click elsewhere, and middle click.
 *
 * @author Edu Díaz (RGiskard7)
 * @since 2.6.0
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

    /**
     * Cmd/Ctrl+click or middle click on {@code button} runs {@code inNewTab} instead of its own
     * action, which a plain click still runs.
     */
    public static void onButton(ButtonBase button, Runnable inNewTab) {
        // A button fires on a plain click only (a modifier or another button leaves it
        // unarmed), so these clicks reach no action of its own: they get this one, when let
        // go over the button, as a click is.
        button.addEventFilter(MouseEvent.MOUSE_RELEASED, event -> {
            if (opensInNewTab(event) && button.contains(event.getX(), event.getY())) {
                inNewTab.run();
            }
        });
    }
}
