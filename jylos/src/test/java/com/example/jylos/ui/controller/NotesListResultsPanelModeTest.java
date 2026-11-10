package com.example.jylos.ui.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.net.URL;
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

import com.example.jylos.tests.FxTestSupport;

import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.ButtonBase;

/**
 * In the Explorer layout the notes panel only shows the results of a tag or a search. It
 * then has a close button, and no grid view or "notes of subfolders" toggle (those belong to
 * browsing a folder, which the Explorer tree does).
 */
class NotesListResultsPanelModeTest {

    @Test
    void resultsModeSwapsTheFolderControlsForACloseButton() throws Exception {
        Assumptions.assumeTrue(FxTestSupport.isFxRuntimeAvailable());
        List<String> closes = new ArrayList<>();

        List<Boolean> visibility = onFx(() -> {
            URL fxml = getClass().getClassLoader().getResource("com/example/jylos/ui/view/NotesListView.fxml");
            ResourceBundle bundle = ResourceBundle.getBundle("com.example.jylos.i18n.messages",
                    Locale.forLanguageTag("es"));
            FXMLLoader loader = new FXMLLoader(fxml, bundle);
            loader.load();
            NotesListController controller = loader.getController();
            controller.setCloseResultsAction(() -> closes.add("close"));

            List<Boolean> out = new ArrayList<>();
            out.addAll(shown(controller));
            controller.setResultsPanelMode(true);
            out.addAll(shown(controller));
            button(controller, "closeResultsBtn").fire();
            controller.setResultsPanelMode(false);
            out.addAll(shown(controller));
            return out;
        });

        // order per state: grid toggle, subfolders toggle, close button
        assertEquals(List.of(true, true, false, false, false, true, true, true, false), visibility);
        assertEquals(List.of("close"), closes);
    }

    private static List<Boolean> shown(NotesListController controller) throws Exception {
        return List.of(visible(button(controller, "viewModeToggleBtn")),
                visible(button(controller, "peekRecursiveBtn")),
                visible(button(controller, "closeResultsBtn")));
    }

    private static boolean visible(ButtonBase button) {
        return button.isVisible() && button.isManaged();
    }

    private static ButtonBase button(NotesListController controller, String name) throws Exception {
        Field field = NotesListController.class.getDeclaredField(name);
        field.setAccessible(true);
        return (ButtonBase) field.get(controller);
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
        assertTrue(done.await(30, TimeUnit.SECONDS), "test never completed on the FX thread");
        if (failure.get() != null) {
            throw new AssertionError(failure.get());
        }
        return result.get();
    }
}
