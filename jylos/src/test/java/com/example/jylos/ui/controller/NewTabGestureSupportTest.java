package com.example.jylos.ui.controller;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;

/** Which clicks open a note in a new tab: Cmd/Ctrl+click (the platform shortcut) and middle click. */
class NewTabGestureSupportTest {

    @Test
    void shortcutClickAndMiddleClickOpenANewTab() {
        assertTrue(NewTabGestureSupport.opensInNewTab(click(MouseButton.PRIMARY, true)));
        assertTrue(NewTabGestureSupport.opensInNewTab(click(MouseButton.MIDDLE, false)));
    }

    @Test
    void plainAndSecondaryClicksDoNot() {
        assertFalse(NewTabGestureSupport.opensInNewTab(click(MouseButton.PRIMARY, false)));
        assertFalse(NewTabGestureSupport.opensInNewTab(click(MouseButton.SECONDARY, false)));
        // Cmd/Ctrl + right click is a context-menu request, not "open".
        assertFalse(NewTabGestureSupport.opensInNewTab(click(MouseButton.SECONDARY, true)));
        assertFalse(NewTabGestureSupport.opensInNewTab(null));
    }

    /** {@code shortcut} sets the platform shortcut modifier (Cmd on macOS, Ctrl elsewhere). */
    private static MouseEvent click(MouseButton button, boolean shortcut) {
        boolean mac = System.getProperty("os.name", "").toLowerCase().contains("mac");
        return new MouseEvent(MouseEvent.MOUSE_PRESSED, 0, 0, 0, 0, button, 1,
                false, shortcut && !mac, false, shortcut && mac,
                button == MouseButton.PRIMARY, button == MouseButton.MIDDLE, button == MouseButton.SECONDARY,
                false, false, true, null);
    }
}
