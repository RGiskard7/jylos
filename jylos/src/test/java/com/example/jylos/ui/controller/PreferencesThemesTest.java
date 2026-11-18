package com.example.jylos.ui.controller;

import static com.example.jylos.tests.FxTestSupport.onFx;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.example.jylos.tests.FxTestSupport;
import com.example.jylos.ui.components.SettingsPane;
import com.example.jylos.ui.preferences.IconSize;
import com.example.jylos.ui.preferences.LayoutMode;
import com.example.jylos.ui.preferences.UiPreferencesStore;
import com.example.jylos.ui.theme.ThemeCatalog;

import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DialogPane;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.stage.Window;

/**
 * Preferences lists the themes in the themes folder, and Reload shows one the user has just
 * copied there, keeping the theme chosen.
 */
class PreferencesThemesTest {

    private static final int APPEARANCE = 3;

    @Test
    void reloadShowsAThemeJustCopiedIntoTheFolder(@TempDir Path folder) throws Exception {
        Assumptions.assumeTrue(FxTestSupport.isFxRuntimeAvailable());
        theme(folder, "paper", "Paper");
        ResourceBundle es = ResourceBundle.getBundle("com.example.jylos.i18n.messages", Locale.forLanguageTag("es"));
        AtomicReference<Optional<DialogSupport.PreferencesDialogResult>> result = new AtomicReference<>();
        Platform.runLater(() -> {
            DialogSupport dialogs = new DialogSupport();
            dialogs.wire(es::getString, status -> { }, null);
            result.set(dialogs.showPreferences(new UiPreferencesStore().load(null), new ThemeCatalog(List.of(folder)),
                    null, Set.of(), LayoutMode.EXPLORER, true, IconSize.NORMAL, false, null));
        });
        DialogPane pane = null;
        for (int i = 0; i < 100 && pane == null; i++) {
            Thread.sleep(50);
            pane = onFx(PreferencesThemesTest::openPreferences);
        }
        assertTrue(pane != null, "the Preferences window never opened");
        DialogPane preferences = pane;

        List<String> before = onFx(() -> {
            ((SettingsPane) preferences.getContent()).showSection(APPEARANCE);
            preferences.applyCss();
            ComboBox<String> themes = themes(preferences, es);
            themes.getSelectionModel().select("Paper [paper]");
            return List.copyOf(themes.getItems());
        });
        theme(folder, "nord", "Nord");
        List<String> after = onFx(() -> {
            button(preferences, es.getString("dialog.preferences.themes_reload")).fire();
            ComboBox<String> themes = themes(preferences, es);
            List<String> items = List.copyOf(themes.getItems());
            String chosen = themes.getValue();
            preferences.getScene().getWindow().hide();
            return List.of(String.join(", ", items), chosen);
        });

        assertEquals(List.of(es.getString("pref.theme.none"), "Paper [paper]"), before);
        assertEquals(es.getString("pref.theme.none") + ", Nord [nord], Paper [paper]", after.get(0));
        assertEquals("Paper [paper]", after.get(1), "the theme chosen stays chosen");
    }

    private static void theme(Path folder, String id, String name) throws Exception {
        Path dir = Files.createDirectories(folder.resolve(id));
        Files.writeString(dir.resolve("theme.properties"), "id=" + id + "\nname=" + name + "\nbase=dark\ndarkLike=true\n");
        Files.writeString(dir.resolve("theme.css"), ".root { -fx-base-bg: #2e3440; }");
    }

    @SuppressWarnings("unchecked")
    private static ComboBox<String> themes(DialogPane preferences, ResourceBundle es) {
        for (Node node : preferences.lookupAll(".settings-row-name")) {
            if (node instanceof Label label && es.getString("dialog.preferences.external_theme").equals(label.getText())) {
                HBox row = (HBox) label.getParent().getParent();
                HBox controls = (HBox) row.getChildren().get(row.getChildren().size() - 1);
                return (ComboBox<String>) controls.getChildren().get(0);
            }
        }
        throw new AssertionError("no themes row");
    }

    private static Button button(DialogPane preferences, String tooltip) {
        for (Node node : preferences.lookupAll(".button")) {
            Tooltip tip = node instanceof Button b ? b.getTooltip() : null;
            if (tip != null && tooltip.equals(tip.getText())) {
                return (Button) node;
            }
        }
        throw new AssertionError("no button " + tooltip);
    }

    private static DialogPane openPreferences() {
        for (Window window : Window.getWindows()) {
            if (window.isShowing() && window.getScene() != null && window.getScene().getRoot() instanceof DialogPane p
                    && p.getContent() instanceof SettingsPane) {
                return p;
            }
        }
        return null;
    }
}
