package com.example.jylos.ui.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static com.example.jylos.tests.FxTestSupport.onFx;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.ResourceBundle;
import java.util.concurrent.CopyOnWriteArrayList;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import com.example.jylos.event.EventBus;
import com.example.jylos.event.events.SystemActionEvent;
import com.example.jylos.tests.FxTestSupport;

import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.layout.Pane;

/**
 * With no note open (or in a new tab), the editor offers what Obsidian does, in its words: new
 * note, new canvas, go to file; and the graph.
 */
class EditorEmptyStateTest {

    @Test
    void theEmptyTabOffersANewNoteANewCanvasGoToFileAndTheGraph() throws Exception {
        Assumptions.assumeTrue(FxTestSupport.isFxRuntimeAvailable());
        List<String> asked = new CopyOnWriteArrayList<>();
        EventBus.Subscription subscription = EventBus.getInstance().subscribe(SystemActionEvent.class,
                event -> asked.add(event.getActionType().name()));
        try {
            List<String> labels = onFx(() -> {
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/example/jylos/ui/view/EditorView.fxml"),
                        ResourceBundle.getBundle("com.example.jylos.i18n.messages", Locale.forLanguageTag("es")));
                Parent root = loader.load();
                EditorController controller = loader.getController();
                Field bus = EditorController.class.getDeclaredField("eventBus");
                bus.setAccessible(true);
                bus.set(controller, EventBus.getInstance());
                List<Button> buttons = new ArrayList<>();
                for (Node child : ((Pane) root.lookup(".editor-empty-state")).getChildren()) {
                    if (child instanceof Button button) {
                        buttons.add(button);
                    }
                }
                buttons.forEach(Button::fire);
                return buttons.stream().map(Button::getText).toList();
            });
            assertEquals(List.of("Nueva nota", "Nuevo lienzo", "Ir a archivo", "Vista de grafo"), labels);
            assertEquals(List.of("NEW_NOTE", "NEW_CANVAS", "QUICK_SWITCHER", "GRAPH_VIEW"), asked);
        } finally {
            subscription.cancel();
        }
    }

    /** As in Obsidian, a new tab still has back and forward: it can go back to what it showed. */
    @Test
    void theEmptyTabKeepsBackAndForward() throws Exception {
        Assumptions.assumeTrue(FxTestSupport.isFxRuntimeAvailable());

        List<String> seen = onFx(() -> {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/example/jylos/ui/view/EditorView.fxml"),
                    ResourceBundle.getBundle("com.example.jylos.i18n.messages", Locale.forLanguageTag("es")));
            Parent root = loader.load();
            EditorController controller = loader.getController();
            controller.updateNavigationState(true, false);
            Node back = (Node) loader.getNamespace().get("navBackBtn");
            Node title = (Node) loader.getNamespace().get("noteTitleField");
            return List.of("back shown " + shown(back, root) + " enabled " + !back.isDisabled(),
                    "title shown " + shown(title, root));
        });

        assertEquals(List.of("back shown true enabled true", "title shown false"), seen);
    }

    /** Whether {@code node} and every parent up to {@code root} are visible. */
    private static boolean shown(Node node, Parent root) {
        for (Node current = node; current != null; current = current.getParent()) {
            if (!current.isVisible()) {
                return false;
            }
            if (current == root) {
                return true;
            }
        }
        return false;
    }
}
