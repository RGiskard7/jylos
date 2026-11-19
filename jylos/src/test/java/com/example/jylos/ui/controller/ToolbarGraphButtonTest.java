package com.example.jylos.ui.controller;

import static com.example.jylos.tests.FxTestSupport.onFx;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.ResourceBundle;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import com.example.jylos.event.EventBus;
import com.example.jylos.event.events.SystemActionEvent;
import com.example.jylos.tests.FxTestSupport;

import javafx.event.Event;
import javafx.event.EventType;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Point2D;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;

/**
 * The graph button in the toolbar opens the graph as a note opens, in the current tab; with
 * Cmd/Ctrl or the middle button, in a new tab, as a note does from the list.
 */
class ToolbarGraphButtonTest {

    private static final boolean MAC = System.getProperty("os.name", "").toLowerCase().contains("mac");

    @AfterEach
    void clearEventBus() {
        EventBus.getInstance().clear();
    }

    @Test
    void aPlainClickOpensTheGraphAndShortcutOrMiddleClickOpensItInANewTab() throws Exception {
        Assumptions.assumeTrue(FxTestSupport.isFxRuntimeAvailable());
        List<SystemActionEvent.ActionType> published = new ArrayList<>();

        onFx(() -> {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/example/jylos/ui/view/ToolbarView.fxml"),
                    ResourceBundle.getBundle("com.example.jylos.i18n.messages", Locale.forLanguageTag("es")));
            Parent root = loader.load();
            new Scene(root, 1100, 90);
            root.applyCss(); // the button's skin, which arms and fires it on a click
            root.layout();
            ToolbarController toolbar = loader.getController();
            toolbar.wire(EventBus.getInstance(), null, null, null, null, null, null, null);
            EventBus.getInstance().subscribe(SystemActionEvent.class, e -> published.add(e.getActionType()));
            Button graph = (Button) loader.getNamespace().get("graphViewBtn");

            click(graph, MouseButton.PRIMARY, false);
            click(graph, MouseButton.PRIMARY, true);
            click(graph, MouseButton.MIDDLE, false);
            return null;
        });

        assertEquals(List.of(SystemActionEvent.ActionType.GRAPH_VIEW, SystemActionEvent.ActionType.GRAPH_VIEW_NEW_TAB,
                SystemActionEvent.ActionType.GRAPH_VIEW_NEW_TAB), published);
    }

    /** Presses and lets go on the button's centre. */
    private static void click(Button button, MouseButton which, boolean shortcut) {
        Point2D centre = button.localToScene(button.getWidth() / 2, button.getHeight() / 2);
        Event.fireEvent(button, mouse(MouseEvent.MOUSE_PRESSED, which, shortcut, centre, true));
        Event.fireEvent(button, mouse(MouseEvent.MOUSE_RELEASED, which, shortcut, centre, false));
    }

    private static MouseEvent mouse(EventType<MouseEvent> type, MouseButton button, boolean shortcut,
            Point2D at, boolean down) {
        return new MouseEvent(type, at.getX(), at.getY(), at.getX(), at.getY(), button, 1,
                false, shortcut && !MAC, false, shortcut && MAC,
                down && button == MouseButton.PRIMARY, down && button == MouseButton.MIDDLE, false,
                false, false, true, null);
    }
}
