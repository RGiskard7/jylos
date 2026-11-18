package com.example.jylos.ui.components;

import static com.example.jylos.tests.FxTestSupport.onFx;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import com.example.jylos.tests.FxTestSupport;

import javafx.event.Event;
import javafx.geometry.Point2D;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.stage.Stage;
import javafx.stage.Window;

/**
 * A right-click menu closes when the mouse is pressed outside it (on the node it was opened
 * over or anywhere else in the window), and when another opens: it does not wait for Escape.
 * A press inside the menu does not close it.
 */
class ContextMenusTest {

    @AfterEach
    void closeWindows() throws Exception {
        if (FxTestSupport.isFxRuntimeAvailable()) {
            onFx(() -> {
                new ArrayList<>(Window.getWindows()).forEach(Window::hide);
                return null;
            });
        }
    }

    @Test
    void aMenuClosesOnAPressOutsideItAndWhenAnotherOpens() throws Exception {
        Assumptions.assumeTrue(FxTestSupport.isFxRuntimeAvailable());

        List<String> seen = onFx(() -> {
            Region editor = new Region();
            editor.setPrefSize(200, 200);
            Region sidebar = new Region();
            sidebar.setPrefSize(100, 200);
            Stage stage = new Stage();
            stage.setScene(new Scene(new HBox(sidebar, editor), 300, 200));
            stage.show();
            ContextMenu first = new ContextMenu(new MenuItem("Copiar"));
            ContextMenu second = new ContextMenu(new MenuItem("Pegar"));
            List<String> out = new ArrayList<>();

            ContextMenus.show(first, editor, stage.getX() + 150, stage.getY() + 50);
            press(first.getSkin().getNode());
            out.add("press inside: " + showing(first));
            press(editor);
            out.add("press on its node: " + showing(first));

            ContextMenus.show(first, editor, stage.getX() + 150, stage.getY() + 50);
            press(sidebar);
            out.add("press elsewhere: " + showing(first));

            ContextMenus.show(first, editor, stage.getX() + 150, stage.getY() + 50);
            ContextMenus.show(second, editor, stage.getX() + 160, stage.getY() + 60);
            out.add("another opens: " + showing(first) + " " + showing(second));
            return out;
        });

        assertEquals(List.of("press inside: true", "press on its node: false", "press elsewhere: false",
                "another opens: false true"), seen);
    }

    private static String showing(ContextMenu menu) {
        return String.valueOf(menu.isShowing());
    }

    private static void press(Node node) {
        Point2D at = node.localToScene(5, 5);
        Point2D screen = node.localToScreen(5, 5);
        Event.fireEvent(node, new MouseEvent(MouseEvent.MOUSE_PRESSED, at.getX(), at.getY(), screen.getX(),
                screen.getY(), MouseButton.PRIMARY, 1, false, false, false, false, true, false, false,
                false, false, true, null));
    }
}
