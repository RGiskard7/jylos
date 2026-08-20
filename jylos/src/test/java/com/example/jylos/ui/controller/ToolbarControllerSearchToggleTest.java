package com.example.jylos.ui.controller;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import java.net.URL;
import java.util.Locale;
import java.util.ResourceBundle;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import com.example.jylos.tests.FxTestSupport;

import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.TextField;
import javafx.stage.Stage;

/**
 * The toolbar search box used to always be a full text field. It's now collapsed to
 * an icon button until clicked — Obsidian-style — but it's still the exact same
 * {@link TextField} instance every other part of the app already holds a reference to
 * (the live-search text listener, the various {@code getSearchField().getText()}
 * callers): only its visibility toggles, nothing about it is destroyed or recreated.
 *
 * <p>{@code expandSearch()}/{@code collapseSearchIfEmpty()} are exercised directly
 * rather than through a real click + a real OS focus change: whether JavaFX correctly
 * fires a {@code focusedProperty} listener or grants window focus is the toolkit's own
 * well-established guarantee, not something worth re-proving per-feature, and a
 * background test run has no real window manager to grant focus to in the first place
 * — asserting {@code isFocused()} here would test the test environment, not this
 * code. What actually could be wrong is the branching in these two methods
 * (show/hide the right control, keep text typed so far), which is what's checked.</p>
 */
class ToolbarControllerSearchToggleTest {

    @Test
    void clickingTheSearchIconExpandsTheRealSearchField() throws Exception {
        Assumptions.assumeTrue(FxTestSupport.isFxRuntimeAvailable());

        CountDownLatch done = new CountDownLatch(1);
        boolean[] toggleVisibleBefore = new boolean[1];
        boolean[] fieldVisibleBefore = new boolean[1];
        boolean[] toggleVisibleAfter = new boolean[1];
        boolean[] fieldVisibleAfter = new boolean[1];
        Platform.runLater(() -> {
            try {
                FXMLLoader loader = toolbarLoader();
                Parent root = loader.load();
                ToolbarController controller = loader.getController();
                Button toggle = (Button) root.lookup("#searchToggleBtn");
                TextField field = (TextField) root.lookup("#searchField");
                Stage stage = new Stage();
                stage.setScene(new Scene(root, 1100, 90));
                stage.show();

                toggleVisibleBefore[0] = toggle.isVisible();
                fieldVisibleBefore[0] = field.isVisible();

                controller.expandSearch();

                toggleVisibleAfter[0] = toggle.isVisible();
                fieldVisibleAfter[0] = field.isVisible();
            } catch (Exception e) {
                throw new RuntimeException(e);
            } finally {
                done.countDown();
            }
        });

        assertTrue(done.await(30, TimeUnit.SECONDS), "test never completed on the FX thread");
        assertTrue(toggleVisibleBefore[0], "the search icon must be what shows by default");
        assertFalse(fieldVisibleBefore[0], "the real field must start collapsed/hidden");
        assertFalse(toggleVisibleAfter[0], "expanding must hide the icon in favour of the field");
        assertTrue(fieldVisibleAfter[0], "expanding must reveal the real search field");
    }

    @Test
    void collapsingWithNoTextHidesTheFieldAndShowsTheIconAgain() throws Exception {
        Assumptions.assumeTrue(FxTestSupport.isFxRuntimeAvailable());

        CountDownLatch done = new CountDownLatch(1);
        boolean[] fieldVisibleAfterCollapse = new boolean[1];
        boolean[] toggleVisibleAfterCollapse = new boolean[1];
        Platform.runLater(() -> {
            try {
                FXMLLoader loader = toolbarLoader();
                Parent root = loader.load();
                ToolbarController controller = loader.getController();
                Button toggle = (Button) root.lookup("#searchToggleBtn");
                TextField field = (TextField) root.lookup("#searchField");
                Stage stage = new Stage();
                stage.setScene(new Scene(root, 1100, 90));
                stage.show();

                controller.expandSearch(); // field is empty
                invokeCollapseSearchIfEmpty(controller);

                fieldVisibleAfterCollapse[0] = field.isVisible();
                toggleVisibleAfterCollapse[0] = toggle.isVisible();
            } catch (Exception e) {
                throw new RuntimeException(e);
            } finally {
                done.countDown();
            }
        });

        assertTrue(done.await(30, TimeUnit.SECONDS), "test never completed on the FX thread");
        assertFalse(fieldVisibleAfterCollapse[0], "an empty field must collapse back to hidden");
        assertTrue(toggleVisibleAfterCollapse[0], "the icon must reappear once the field collapses");
    }

    @Test
    void collapsingWithTextTypedLeavesTheFieldExpanded() throws Exception {
        Assumptions.assumeTrue(FxTestSupport.isFxRuntimeAvailable());

        CountDownLatch done = new CountDownLatch(1);
        boolean[] fieldVisibleAfterCollapseAttempt = new boolean[1];
        Platform.runLater(() -> {
            try {
                FXMLLoader loader = toolbarLoader();
                Parent root = loader.load();
                ToolbarController controller = loader.getController();
                TextField field = (TextField) root.lookup("#searchField");
                Stage stage = new Stage();
                stage.setScene(new Scene(root, 1100, 90));
                stage.show();

                controller.expandSearch();
                field.setText("dominios");
                invokeCollapseSearchIfEmpty(controller); // e.g. called by a stray blur

                fieldVisibleAfterCollapseAttempt[0] = field.isVisible();
            } catch (Exception e) {
                throw new RuntimeException(e);
            } finally {
                done.countDown();
            }
        });

        assertTrue(done.await(30, TimeUnit.SECONDS), "test never completed on the FX thread");
        assertTrue(fieldVisibleAfterCollapseAttempt[0],
                "a search in progress must not disappear — only an empty field collapses");
    }

    private static FXMLLoader toolbarLoader() {
        URL fxml = ToolbarControllerSearchToggleTest.class.getClassLoader()
                .getResource("com/example/jylos/ui/view/ToolbarView.fxml");
        ResourceBundle bundle = ResourceBundle.getBundle("com.example.jylos.i18n.messages", Locale.forLanguageTag("es"));
        return new FXMLLoader(fxml, bundle);
    }

    private static void invokeCollapseSearchIfEmpty(ToolbarController controller) throws ReflectiveOperationException {
        Method m = ToolbarController.class.getDeclaredMethod("collapseSearchIfEmpty");
        m.setAccessible(true);
        m.invoke(controller);
    }
}
