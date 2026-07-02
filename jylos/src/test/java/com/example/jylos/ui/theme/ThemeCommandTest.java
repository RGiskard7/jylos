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
}
