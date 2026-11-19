package com.example.jylos.ui.controller;

import static com.example.jylos.tests.FxTestSupport.onFx;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;
import java.util.prefs.Preferences;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import com.example.jylos.tests.FxTestSupport;

import javafx.scene.Scene;
import javafx.scene.layout.Pane;

/**
 * Choosing the dark or the light theme restyles the window, is kept for next time, says so
 * in the status bar and redraws what depends on the theme.
 */
class ThemeSupportTest {

    private final Preferences prefs = Preferences.userRoot().node("jylos-test-theme-support");

    @AfterEach
    void forget() throws Exception {
        prefs.removeNode();
    }

    @Test
    void choosingAThemeRestylesTheWindowAndIsKept() throws Exception {
        Assumptions.assumeTrue(FxTestSupport.isFxRuntimeAvailable());
        List<String> seen = onFx(() -> {
            List<String> out = new ArrayList<>();
            Scene scene = new Scene(new Pane(), 300, 200);
            ThemeSupport theme = new ThemeSupport();
            theme.wire(prefs, () -> true, () -> scene, () -> null, () -> null, () -> out.add("dependents redrawn"),
                    () -> null, new AppSettings(), key -> key, out::add);

            theme.setDark();
            out.add("dark " + theme.isDark() + ", kept " + prefs.get("theme", "?") + ", styled dark "
                    + scene.getStylesheets().stream().anyMatch(s -> s.contains("dark")));
            theme.setLight();
            out.add("dark " + theme.isDark() + ", kept " + prefs.get("theme", "?") + ", styled dark "
                    + scene.getStylesheets().stream().anyMatch(s -> s.contains("dark")));
            return out;
        });

        assertEquals(List.of(
                "dependents redrawn", "status.theme_dark", "dark true, kept dark, styled dark true",
                "dependents redrawn", "status.theme_light", "dark false, kept light, styled dark false"), seen);
    }
}
