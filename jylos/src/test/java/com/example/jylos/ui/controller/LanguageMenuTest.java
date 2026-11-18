package com.example.jylos.ui.controller;

import static com.example.jylos.tests.FxTestSupport.onFx;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.ResourceBundle;
import java.util.UUID;
import java.util.prefs.Preferences;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import com.example.jylos.tests.FxTestSupport;

import javafx.fxml.FXMLLoader;
import javafx.scene.control.MenuItem;
import javafx.scene.control.RadioMenuItem;

/**
 * View > Language lists every language the app carries, each in its own language, with the one
 * in use ticked; picking another one asks to change to it.
 */
class LanguageMenuTest {

    private final Preferences prefs = Preferences.userRoot().node("/com/example/jylos/test/" + UUID.randomUUID());

    @AfterEach
    void forget() throws Exception {
        prefs.removeNode();
    }

    @Test
    void theMenuListsTheAppsLanguagesWithTheOneInUseTicked() throws Exception {
        Assumptions.assumeTrue(FxTestSupport.isFxRuntimeAvailable());
        prefs.put("language", "es");

        List<String> seen = onFx(() -> {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/example/jylos/ui/view/ToolbarView.fxml"),
                    ResourceBundle.getBundle("com.example.jylos.i18n.messages", Locale.forLanguageTag("es")));
            loader.load();
            ToolbarController toolbar = loader.getController();
            List<String> asked = new ArrayList<>();
            new AppSettings().initializeLanguageMenu(toolbar, prefs, asked::add);
            List<String> out = new ArrayList<>();
            for (MenuItem item : toolbar.getLanguageMenu().getItems()) {
                out.add((((RadioMenuItem) item).isSelected() ? "✓" : " ") + item.getText());
            }
            RadioMenuItem english = (RadioMenuItem) toolbar.getLanguageMenu().getItems().get(0);
            english.setSelected(true); // as a click does, before its action
            english.fire();
            out.add("asked " + asked);
            return out;
        });

        assertEquals(List.of(" English", "✓Español", "asked [en]"), seen);
    }
}
