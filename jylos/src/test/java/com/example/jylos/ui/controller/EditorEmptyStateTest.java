package com.example.jylos.ui.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.ResourceBundle;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import com.example.jylos.event.EventBus;
import com.example.jylos.event.events.SystemActionEvent;
import com.example.jylos.tests.FxTestSupport;

import javafx.application.Platform;
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

    private static <T> T onFx(Callable<T> work) throws Exception {
        AtomicReference<T> result = new AtomicReference<>();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        CountDownLatch done = new CountDownLatch(1);
        Platform.runLater(() -> {
            try {
                result.set(work.call());
            } catch (Throwable t) {
                failure.set(t);
            } finally {
                done.countDown();
            }
        });
        assertTrue(done.await(30, TimeUnit.SECONDS), "never completed on the FX thread");
        if (failure.get() != null) {
            throw new AssertionError(failure.get());
        }
        return result.get();
    }
}
