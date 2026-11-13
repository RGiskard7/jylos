package com.example.jylos.ui.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static com.example.jylos.tests.FxTestSupport.onFx;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.ResourceBundle;

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

/** With no tab open, the editor offers what Obsidian does, in its words: new note, new canvas, go to file. */
class EditorEmptyStateTest {

    @Test
    void theEmptyTabOffersANewNoteANewCanvasAndGoToFile() throws Exception {
        Assumptions.assumeTrue(FxTestSupport.isFxRuntimeAvailable());
        List<String> asked = new java.util.concurrent.CopyOnWriteArrayList<>();
        EventBus.Subscription subscription = EventBus.getInstance().subscribe(SystemActionEvent.class,
                event -> asked.add(event.getActionType().name()));
        try {
            List<String> labels = onFx(() -> {
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/example/jylos/ui/view/EditorView.fxml"),
                        ResourceBundle.getBundle("com.example.jylos.i18n.messages", Locale.forLanguageTag("es")));
                Parent root = loader.load();
                EditorController controller = loader.getController();
                java.lang.reflect.Field bus = EditorController.class.getDeclaredField("eventBus");
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
            assertEquals(List.of("Nueva nota", "Nuevo lienzo", "Ir a archivo"), labels);
            assertEquals(List.of("NEW_NOTE", "NEW_CANVAS", "QUICK_SWITCHER"), asked);
        } finally {
            subscription.cancel();
        }
    }
}
