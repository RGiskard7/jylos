package com.example.jylos.ui.components;

import static com.example.jylos.tests.FxTestSupport.onFx;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import com.example.jylos.tests.FxTestSupport;

import javafx.event.Event;
import javafx.event.EventType;
import javafx.geometry.Point2D;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.StackPane;

/** Which clicks open a note in a new tab: Cmd/Ctrl+click (the platform shortcut) and middle click. */
class NewTabGestureTest {

    @Test
    void shortcutClickAndMiddleClickOpenANewTab() {
        assertTrue(NewTabGesture.opensInNewTab(click(MouseButton.PRIMARY, true)));
        assertTrue(NewTabGesture.opensInNewTab(click(MouseButton.MIDDLE, false)));
    }

    @Test
    void plainAndSecondaryClicksDoNot() {
        assertFalse(NewTabGesture.opensInNewTab(click(MouseButton.PRIMARY, false)));
        assertFalse(NewTabGesture.opensInNewTab(click(MouseButton.SECONDARY, false)));
        // Cmd/Ctrl + right click is a context-menu request, not "open".
        assertFalse(NewTabGesture.opensInNewTab(click(MouseButton.SECONDARY, true)));
        assertFalse(NewTabGesture.opensInNewTab(null));
    }

    /**
     * On a button (the graph's in the toolbar), Cmd/Ctrl+click and middle click run the new-tab
     * action and not the button's own; a plain click runs only its own. Right click and a
     * Cmd+click let go outside the button run nothing.
     */
    @Test
    void onAButtonShortcutAndMiddleClickRunTheNewTabActionInsteadOfItsOwn() throws Exception {
        Assumptions.assumeTrue(FxTestSupport.isFxRuntimeAvailable());

        List<String> seen = onFx(() -> {
            List<String> out = new ArrayList<>();
            Button button = new Button("Grafo");
            button.setOnAction(e -> out.add("own"));
            NewTabGesture.onButton(button, () -> out.add("new tab"));
            StackPane root = new StackPane(button);
            new Scene(root, 200, 100);
            root.applyCss(); // the button's skin, which arms and fires it on a click
            root.layout();
            double x = button.getWidth() / 2;
            double y = button.getHeight() / 2;

            out.add("plain:");
            pressAndRelease(button, MouseButton.PRIMARY, false, x, y);
            out.add("shortcut:");
            pressAndRelease(button, MouseButton.PRIMARY, true, x, y);
            out.add("middle:");
            pressAndRelease(button, MouseButton.MIDDLE, false, x, y);
            out.add("secondary:");
            pressAndRelease(button, MouseButton.SECONDARY, false, x, y);
            out.add("outside:");
            pressAndRelease(button, MouseButton.PRIMARY, true, -40, -40);
            return out;
        });

        assertEquals(List.of("plain:", "own", "shortcut:", "new tab", "middle:", "new tab", "secondary:",
                "outside:"), seen);
    }

    /** Presses on the button's centre and lets go at ({@code x}, {@code y}), in its coordinates. */
    private static void pressAndRelease(Button button, MouseButton which, boolean shortcut, double x, double y) {
        Event.fireEvent(button, mouse(button, MouseEvent.MOUSE_PRESSED, which, shortcut, button.getWidth() / 2,
                button.getHeight() / 2, true));
        Event.fireEvent(button, mouse(button, MouseEvent.MOUSE_RELEASED, which, shortcut, x, y, false));
    }

    /** A mouse event at ({@code x}, {@code y}) in the button's coordinates (it carries the scene's). */
    private static MouseEvent mouse(Button on, EventType<MouseEvent> type, MouseButton button, boolean shortcut,
            double x, double y, boolean down) {
        boolean mac = System.getProperty("os.name", "").toLowerCase().contains("mac");
        Point2D scene = on.localToScene(x, y);
        return new MouseEvent(type, scene.getX(), scene.getY(), scene.getX(), scene.getY(), button, 1,
                false, shortcut && !mac, false, shortcut && mac,
                down && button == MouseButton.PRIMARY, down && button == MouseButton.MIDDLE,
                down && button == MouseButton.SECONDARY, false, false, true, null);
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
