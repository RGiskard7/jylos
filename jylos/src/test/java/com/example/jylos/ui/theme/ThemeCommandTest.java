package com.example.jylos.ui.theme;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.example.jylos.ui.preferences.UiPreferencesStore;

class ThemeCommandTest {

    @Test
    void resolveThemeToApplyMapsSystemToLightOrDark() {
        ThemeCommand command = new ThemeCommand();
        String resolved = command.resolveThemeToApply("system");
        assertTrue("light".equals(resolved) || "dark".equals(resolved));
    }

    @Test
    void resolveThemeToApplyKeepsExplicitThemes() {
        ThemeCommand command = new ThemeCommand();
        assertEquals("dark", command.resolveThemeToApply("dark"));
        assertEquals("light", command.resolveThemeToApply("light"));
        assertEquals("light", command.resolveThemeToApply("unknown"));
    }

    @Test
    void isDarkThemeActiveFollowsResolvedVariant() {
        ThemeCommand command = new ThemeCommand();
        assertFalse(command.isDarkThemeActive("light"));
        assertTrue(command.isDarkThemeActive("dark"));
    }

    @Test
    void effectiveThemeSourceHonorsExternalPreferenceWhenMenuIsSystem() {
        assertEquals(UiPreferencesStore.THEME_SOURCE_EXTERNAL,
                ThemeCommand.effectiveThemeSource("system", UiPreferencesStore.THEME_SOURCE_EXTERNAL));
        assertEquals(UiPreferencesStore.THEME_SOURCE_BUILTIN,
                ThemeCommand.effectiveThemeSource("system", UiPreferencesStore.THEME_SOURCE_BUILTIN));
    }

    @Test
    void isSystemBuiltinModeRequiresSystemMenuAndBuiltinPreference() {
        assertFalse(ThemeCommand.isSystemBuiltinMode("system", UiPreferencesStore.THEME_SOURCE_EXTERNAL));
        assertTrue(ThemeCommand.isSystemBuiltinMode("system", UiPreferencesStore.THEME_SOURCE_BUILTIN));
        assertFalse(ThemeCommand.isSystemBuiltinMode("dark", UiPreferencesStore.THEME_SOURCE_BUILTIN));
    }

    /** The startup splash is drawn before the main window exists: it reads the theme from the preferences. */
    @Test
    void opensDarkReadsTheSavedThemeWithoutTheMainWindow() throws Exception {
        java.util.List<ThemeCatalog.ThemeDescriptor> themes = java.util.List.of(
                new ThemeCatalog.ThemeDescriptor("night", "Night", "external", "file:/night.css", true, "dark"),
                new ThemeCatalog.ThemeDescriptor("paper", "Paper", "external", "file:/paper.css", false, "light"));
        ThemeCommand command = new ThemeCommand();
        StringBuilder seen = new StringBuilder();
        String[][] cases = {
                {"dark", "builtin", ""}, {"light", "builtin", ""}, {null, null, null},
                {"light", "external", "night"}, {"dark", "external", "paper"},
                {"dark", "external", "missing"}, {"light", "builtin", "night"}};
        for (String[] c : cases) {
            java.util.prefs.Preferences prefs = java.util.prefs.Preferences.userRoot()
                    .node("/com/example/jylos/test/" + java.util.UUID.randomUUID());
            try {
                if (c[0] != null) {
                    prefs.put("theme", c[0]);
                    prefs.put(UiPreferencesStore.THEME_SOURCE_KEY, c[1]);
                    prefs.put(UiPreferencesStore.THEME_EXTERNAL_ID_KEY, c[2]);
                }
                seen.append(command.opensDark(prefs, themes) ? 'D' : 'L');
            } finally {
                prefs.removeNode();
            }
        }
        // An external theme decides by itself; one that is gone falls back to the menu choice.
        assertEquals("DLLDLDL", seen.toString());
    }
}
