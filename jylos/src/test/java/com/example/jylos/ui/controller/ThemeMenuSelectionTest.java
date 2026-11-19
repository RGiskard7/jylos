package com.example.jylos.ui.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URL;
import java.util.Locale;
import java.util.ResourceBundle;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import com.example.jylos.tests.FxTestSupport;

import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.ToggleGroup;

/**
 * The View > Theme menu shows the theme the app opened in: the saved one, not the item the
 * menu file marks by default (Light).
 */
class ThemeMenuSelectionTest {

    @Test
    void theThemeMenuMarksTheSavedThemeOnStartup() throws Exception {
        Assumptions.assumeTrue(FxTestSupport.isFxRuntimeAvailable());
        assertEquals("Oscuro", markedAfterStartup("dark"));
        assertEquals("Sistema", markedAfterStartup("system"));
        assertEquals("Claro", markedAfterStartup("light"));
    }

    /** Loads the real menu and initializes it as MainController does; the item then marked. */
    private static String markedAfterStartup(String savedTheme) throws Exception {
        AtomicReference<String> marked = new AtomicReference<>();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        CountDownLatch done = new CountDownLatch(1);
        Platform.runLater(() -> {
            try {
                URL fxml = ThemeMenuSelectionTest.class.getClassLoader()
                        .getResource("com/example/jylos/ui/view/ToolbarView.fxml");
                FXMLLoader loader = new FXMLLoader(fxml,
                        ResourceBundle.getBundle("com.example.jylos.i18n.messages", Locale.forLanguageTag("es")));
                loader.load();
                ToolbarController toolbar = loader.getController();
                AppSettings settings = new AppSettings();
                ToggleGroup group = settings.initializeThemeMenu(toolbar, savedTheme, () -> "light",
                        theme -> { }, null);
                marked.set(group.getSelectedToggle() == toolbar.getDarkThemeMenuItem() ? "Oscuro"
                        : group.getSelectedToggle() == toolbar.getSystemThemeMenuItem() ? "Sistema"
                        : group.getSelectedToggle() == toolbar.getLightThemeMenuItem() ? "Claro" : "ninguno");
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
        return marked.get();
    }
}
