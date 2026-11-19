package com.example.jylos.ui.theme;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.example.jylos.util.ThemePalette;

/**
 * An external theme's colours reach the editor, the reading view and the graph; with the built-in
 * light and dark themes they keep their own.
 */
class ActiveThemePaletteTest {

    @AfterEach
    void forget() {
        ActiveThemePalette.set(null);
    }

    @Test
    void anExternalThemeGivesItsColoursOverItsBase(@TempDir Path dir) throws Exception {
        Path css = dir.resolve("theme.css");
        Files.writeString(css, ".root { -fx-base-bg: #282828; -fx-text-main: #ebdbb2; }");
        ThemeCatalog.ThemeDescriptor theme = new ThemeCatalog.ThemeDescriptor("gruvbox", "Gruvbox", "external",
                css.toUri().toString(), true, "dark");
        ThemeCommand command = new ThemeCommand();

        ThemePalette palette = command.externalPalette(theme, command.resolveBuiltinStylesheet("dark", ThemeCommand.class));

        assertEquals("#282828", palette.background());
        assertEquals("#ebdbb2", palette.text());
        assertEquals("#3a3a3a", palette.border(), "the dark base's, which the theme leaves");
    }

    @Test
    void aThemeWithoutItsOwnBaseUsesOnlyItsStylesheet(@TempDir Path dir) throws Exception {
        Path css = dir.resolve("theme.css");
        Files.writeString(css, ".root { -fx-accent: #d79921; }");
        ThemeCatalog.ThemeDescriptor theme = new ThemeCatalog.ThemeDescriptor("partial", "Partial", "external",
                css.toUri().toString(), true, "none");

        assertNull(new ThemeCommand().externalPalette(theme, null), "no background or text: nothing to follow");
    }

    @Test
    void theActivePaletteIsTheOneSetLast() {
        ThemePalette palette = ThemePalette.fromStylesheets(List.of(
                ".root { -fx-base-bg: #282828; -fx-text-main: #ebdbb2; }"));

        ActiveThemePalette.set(palette);
        assertEquals(palette, ActiveThemePalette.get());
        ActiveThemePalette.set(null);
        assertNull(ActiveThemePalette.get());
    }
}
