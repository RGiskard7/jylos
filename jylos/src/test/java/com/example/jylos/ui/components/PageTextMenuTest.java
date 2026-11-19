package com.example.jylos.ui.components;

import static com.example.jylos.tests.FxTestSupport.onFx;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import com.example.jylos.tests.FxTestSupport;
import com.example.jylos.ui.web.JavaFxHtmlView;

import javafx.event.Event;
import javafx.geometry.Point2D;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ContextMenuEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.PickResult;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import javafx.stage.Window;

/**
 * A page that is read (the reading view) has the app's own right-click menu, in its language:
 * Copy (only with something selected) and Select all. It closes with a click on the page, and
 * giving the page the menu again only relabels it.
 */
class PageTextMenuTest {

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
    void theReadingMenuCopiesSelectsAllAndClosesOnAClick() throws Exception {
        Assumptions.assumeTrue(FxTestSupport.isFxRuntimeAvailable());
        CountDownLatch loaded = new CountDownLatch(1);
        JavaFxHtmlView page = onFx(() -> {
            JavaFxHtmlView view = new JavaFxHtmlView();
            Stage stage = new Stage();
            stage.setScene(new Scene(new StackPane(view), 400, 300));
            stage.show();
            view.whenLoaded(loaded::countDown);
            view.loadHtml("<html><body><p>Hola mundo</p></body></html>");
            return view;
        });
        assertTrue(loaded.await(30, TimeUnit.SECONDS), "the page never loaded");

        List<String> seen = onFx(() -> {
            List<String> out = new ArrayList<>();
            PageTextMenu.install(page, "Copy", "Select all");
            PageTextMenu.install(page, "Copiar", "Seleccionar todo");

            ContextMenu menu = request(page.node());
            out.add(menu == null ? "no menu" : labels(menu) + " copy disabled " + menu.getItems().get(0).isDisable());
            if (menu == null) {
                return out;
            }
            menu.getItems().get(1).fire();
            out.add("selected: " + page.evaluate("window.getSelection().toString()"));

            menu = request(page.node());
            out.add("copy disabled " + menu.getItems().get(0).isDisable() + ", menus " + openMenus());
            menu.getItems().get(0).fire();
            out.add("clipboard: " + Clipboard.getSystemClipboard().getString());

            press(page.node());
            out.add("after a click: " + openMenus());
            return out;
        });

        assertEquals(List.of("[Copiar, Seleccionar todo] copy disabled true", "selected: Hola mundo",
                "copy disabled false, menus 1", "clipboard: Hola mundo", "after a click: 0"), seen);
    }

    private static ContextMenu request(Node node) {
        Point2D screen = node.localToScreen(20, 20);
        Event.fireEvent(node, new ContextMenuEvent(ContextMenuEvent.CONTEXT_MENU_REQUESTED, 20, 20,
                screen.getX(), screen.getY(), false, new PickResult(node, 20, 20)));
        return Window.getWindows().stream().filter(ContextMenu.class::isInstance).map(ContextMenu.class::cast)
                .filter(Window::isShowing).findFirst().orElse(null);
    }

    private static long openMenus() {
        return Window.getWindows().stream().filter(ContextMenu.class::isInstance).filter(Window::isShowing).count();
    }

    private static List<String> labels(ContextMenu menu) {
        return menu.getItems().stream().map(MenuItem::getText).toList();
    }

    private static void press(Node node) {
        Point2D at = node.localToScene(30, 30);
        Point2D screen = node.localToScreen(30, 30);
        Event.fireEvent(node, new MouseEvent(MouseEvent.MOUSE_PRESSED, at.getX(), at.getY(), screen.getX(),
                screen.getY(), MouseButton.PRIMARY, 1, false, false, false, false, true, false, false,
                false, false, true, null));
    }
}
