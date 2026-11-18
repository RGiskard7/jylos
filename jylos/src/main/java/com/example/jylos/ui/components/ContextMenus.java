package com.example.jylos.ui.components;

import javafx.event.EventHandler;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.ContextMenu;
import javafx.scene.input.MouseEvent;
import javafx.stage.WindowEvent;

/**
 * Shows a right-click menu that closes as a menu should: when another opens and when the
 * mouse is pressed anywhere in its window outside it, not only on Escape.
 *
 * <p>JavaFX closes a menu on a press outside it, but not on the node it was opened over when
 * that node is a plain pane rather than a control (the editor, the reading view, the canvas,
 * a Kanban or grid card): a click in it left the menu open, and a second right click opened
 * a second menu over the first. Menus set on a control (a row of a list or tree) close
 * already.</p>
 *
 * @author Edu Díaz (RGiskard7)
 * @since 2.6.0
 */
public final class ContextMenus {

    /** The menu open now through here, if any: there is only one at a time. */
    private static ContextMenu open;

    private ContextMenus() {
    }

    /** Shows {@code menu} at the given screen point, over {@code owner}. */
    public static void show(ContextMenu menu, Node owner, double screenX, double screenY) {
        if (open != null) {
            open.hide();
        }
        Scene scene = owner.getScene();
        EventHandler<MouseEvent> pressOutside = event -> menu.hide();
        if (scene != null) {
            // A press inside the menu reaches the menu's own scene, not this one.
            scene.addEventFilter(MouseEvent.MOUSE_PRESSED, pressOutside);
        }
        menu.addEventHandler(WindowEvent.WINDOW_HIDDEN, new EventHandler<WindowEvent>() {
            @Override
            public void handle(WindowEvent event) {
                menu.removeEventHandler(WindowEvent.WINDOW_HIDDEN, this);
                if (scene != null) {
                    scene.removeEventFilter(MouseEvent.MOUSE_PRESSED, pressOutside);
                }
                if (open == menu) {
                    open = null;
                }
            }
        });
        open = menu;
        menu.show(owner, screenX, screenY);
    }
}
