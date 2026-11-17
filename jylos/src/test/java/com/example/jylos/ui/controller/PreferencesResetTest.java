package com.example.jylos.ui.controller;

import static com.example.jylos.tests.FxTestSupport.onFx;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import com.example.jylos.tests.FxTestSupport;
import com.example.jylos.ui.components.SettingsPane;
import com.example.jylos.ui.preferences.IconSize;
import com.example.jylos.ui.preferences.LayoutMode;
import com.example.jylos.ui.preferences.UiPreferencesStore;

import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DialogPane;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.stage.Window;

/**
 * A section's Reset in Preferences puts that section's settings back to their defaults, and
 * only that section's: what was changed elsewhere is saved as chosen.
 */
class PreferencesResetTest {

    private static final int EDITOR = 1;
    private static final int APPEARANCE = 3;

    @Test
    void resettingAppearanceRestoresItsDefaultsAndKeepsTheOtherSections() throws Exception {
        Assumptions.assumeTrue(FxTestSupport.isFxRuntimeAvailable());
        ResourceBundle es = ResourceBundle.getBundle("com.example.jylos.i18n.messages", Locale.forLanguageTag("es"));
        AtomicReference<Optional<DialogSupport.PreferencesDialogResult>> result = new AtomicReference<>();
        Platform.runLater(() -> {
            DialogSupport dialogs = new DialogSupport();
            dialogs.wire(es::getString, status -> { }, null);
            result.set(dialogs.showPreferences(new UiPreferencesStore().load(null), List.of(), null, Set.of(),
                    LayoutMode.EXPLORER, true, IconSize.NORMAL, false));
        });
        DialogPane pane = null;
        for (int i = 0; i < 100 && pane == null; i++) {
            Thread.sleep(50);
            pane = onFx(PreferencesResetTest::openPreferences);
        }
        assertTrue(pane != null, "the Preferences window never opened");
        DialogPane preferences = pane;

        onFx(() -> {
            SettingsPane settings = (SettingsPane) preferences.getContent();
            settings.showSection(EDITOR);
            preferences.applyCss();
            ((CheckBox) control(preferences, es.getString("dialog.preferences.readable_line_length"))).setSelected(true);
            settings.showSection(APPEARANCE);
            preferences.applyCss();
            @SuppressWarnings("unchecked")
            ComboBox<Integer> font = (ComboBox<Integer>) control(preferences, es.getString("dialog.preferences.font_size"));
            font.setValue(18);
            assertEquals(18, font.getValue(), "the font was changed before the reset");
            @SuppressWarnings("unchecked")
            ComboBox<IconSize> icons = (ComboBox<IconSize>) control(preferences, es.getString("dialog.preferences.icon_size"));
            icons.setValue(IconSize.EXTRA_LARGE);
            ((CheckBox) control(preferences, es.getString("dialog.preferences.colored_icons"))).setSelected(true);
            ((Button) preferences.lookup(".settings-page .settings-reset-btn")).fire();
            for (Node node : preferences.lookupAll(".button")) {
                if (node instanceof Button button && es.getString("action.save").equals(button.getText())) {
                    button.fire();
                }
            }
            return null;
        });
        for (int i = 0; i < 100 && result.get() == null; i++) {
            Thread.sleep(50);
        }

        DialogSupport.PreferencesDialogResult saved = result.get().orElseThrow();
        assertEquals(List.of(UiPreferencesStore.DEFAULT_UI_FONT_SIZE, IconSize.NORMAL, false, true),
                List.of(saved.uiFontSize(), saved.iconSize(), saved.coloredIcons(), saved.readableLineLength()),
                "interface font and icons back to defaults; the editor change kept");
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

    /** The control on the row of the setting named {@code name}, in the section shown. */
    private static Node control(Parent root, String name) {
        for (Node node : root.lookupAll(".settings-row-name")) {
            if (node instanceof Label label && name.equals(label.getText())) {
                HBox row = (HBox) label.getParent().getParent();
                return row.getChildren().get(row.getChildren().size() - 1);
            }
        }
        throw new AssertionError("no setting named " + name);
    }
}
