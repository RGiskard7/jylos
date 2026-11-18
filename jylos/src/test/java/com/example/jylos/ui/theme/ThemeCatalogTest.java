package com.example.jylos.ui.theme;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.example.jylos.util.ThemePalette;

class ThemeCatalogTest {

    /** The repository's themes folder, from the module's directory where the tests run. */
    private static final Path REPO_THEMES = Path.of("..", "themes");

    @Test
    void resolveBaseVariantUsesMenuSystemWhenBaseAuto() {
        ThemeCatalog.ThemeDescriptor theme = new ThemeCatalog.ThemeDescriptor(
                "test", "Test", "external", "file:///x.css", true, "auto");
        assertEquals("dark", theme.resolveBaseVariant("system", () -> "dark"));
        assertEquals("light", theme.resolveBaseVariant("light", () -> "dark"));
    }

    @Test
    void resolveBaseVariantHonorsConfiguredBase() {
        ThemeCatalog.ThemeDescriptor theme = new ThemeCatalog.ThemeDescriptor(
                "test", "Test", "external", "file:///x.css", false, "system");
        assertEquals("light", theme.resolveBaseVariant("dark", () -> "light"));
    }

    /** The themes in the repository's themes folder, ready to copy: each one complete and readable. */
    @Test
    void theRepositorysThemesAreListedFromTheirFolder() throws Exception {
        ThemeCatalog catalog = new ThemeCatalog(List.of(REPO_THEMES));
        List<ThemeCatalog.ThemeDescriptor> themes = catalog.getAvailableThemes();

        List<String> external = themes.stream().filter(t -> "external".equals(t.source()))
                .map(ThemeCatalog.ThemeDescriptor::id).toList();
        assertEquals(List.of("catppuccin-latte", "catppuccin-mocha", "gruvbox-dark", "gruvbox-light", "nord-dark",
                "nord-light", "retro-phosphor", "rose-pine", "rose-pine-dawn"), external);
        for (String id : external) {
            try (InputStream css = URI.create(catalog.findById(themes, id).cssPath()).toURL().openStream()) {
                assertTrue(css.readAllBytes().length > 100, id + "'s stylesheet is there");
            }
        }
    }

    /**
     * Nord, Catppuccin and Rosé Pine: each variant is dark or light as its base, its name reads
     * with its accents, and the editor and the reading view get its background and text.
     */
    @Test
    void nordCatppuccinAndRosePineAreDarkOrLightAsTheirBase() {
        ThemeCatalog catalog = new ThemeCatalog(List.of(REPO_THEMES));
        List<ThemeCatalog.ThemeDescriptor> themes = catalog.getAvailableThemes();
        List<String> seen = new ArrayList<>();
        for (String id : List.of("nord-dark", "nord-light", "catppuccin-mocha", "catppuccin-latte", "rose-pine",
                "rose-pine-dawn")) {
            ThemeCatalog.ThemeDescriptor theme = catalog.findById(themes, id);
            ThemePalette colours = new ThemeCommand().externalPalette(theme,
                    new ThemeCommand().resolveBuiltinStylesheet(theme.base(), ThemeCommand.class));
            seen.add(theme.name() + " " + theme.base() + " " + theme.darkLike() + " " + colours.background() + " "
                    + colours.text());
        }

        assertEquals(List.of("Nord Dark dark true #2e3440 #eceff4", "Nord Light light false #eceff4 #2e3440",
                "Catppuccin Mocha dark true #1e1e2e #cdd6f4", "Catppuccin Latte light false #eff1f5 #4c4f69",
                "Rosé Pine dark true #191724 #e0def4", "Rosé Pine Dawn light false #faf4ed #575279"), seen);
    }

    @Test
    void gruvboxIsDarkOverTheDarkBaseAndLightOverTheLightOne() {
        ThemeCatalog catalog = new ThemeCatalog(List.of(REPO_THEMES));
        List<ThemeCatalog.ThemeDescriptor> themes = catalog.getAvailableThemes();
        ThemeCatalog.ThemeDescriptor dark = catalog.findById(themes, "gruvbox-dark");
        ThemeCatalog.ThemeDescriptor light = catalog.findById(themes, "gruvbox-light");

        assertTrue(dark.darkLike());
        assertEquals("dark", dark.base());
        assertFalse(light.darkLike());
        assertEquals("light", light.base());
        ThemePalette darkColours = new ThemeCommand().externalPalette(dark,
                new ThemeCommand().resolveBuiltinStylesheet("dark", ThemeCommand.class));
        ThemePalette lightColours = new ThemeCommand().externalPalette(light,
                new ThemeCommand().resolveBuiltinStylesheet("light", ThemeCommand.class));
        assertEquals("#282828", darkColours.background());
        assertEquals("#ebdbb2", darkColours.text());
        assertEquals("#fbf1c7", lightColours.background());
        assertEquals("#3c3836", lightColours.text());
    }

    @Test
    void nothingButTheMenusThemesWithAnEmptyFolder(@TempDir Path empty) {
        List<String> ids = new ThemeCatalog(List.of(empty)).getAvailableThemes().stream()
                .map(ThemeCatalog.ThemeDescriptor::id).toList();

        assertEquals(List.of("light", "dark", "system"), ids, "no theme inside the app");
    }

    @Test
    void aThemeCalledLikeTheMenusLightDarkOrSystemIsLeftOut(@TempDir Path dir) throws Exception {
        for (String id : List.of("dark", "light", "system")) {
            Path theme = Files.createDirectories(dir.resolve("mine-" + id));
            Files.writeString(theme.resolve("theme.properties"), "id=" + id + "\nname=Mine " + id + "\n");
            Files.writeString(theme.resolve("theme.css"), ".root { }");
        }

        List<ThemeCatalog.ThemeDescriptor> themes = new ThemeCatalog(List.of(dir)).getAvailableThemes();

        assertTrue(themes.stream().noneMatch(t -> t.name().startsWith("Mine ")));
        assertEquals("builtin", new ThemeCatalog(List.of(dir)).findById(themes, "dark").source());
    }

    @Test
    void theUsersThemesFolderIsCreatedToOpenIt(@TempDir Path dir) {
        Path folder = dir.resolve("themes");

        Path opened = new ThemeCatalog(List.of(folder)).userDirectory();

        assertEquals(folder, opened);
        assertTrue(Files.isDirectory(folder));
        assertNotNull(opened);
    }
}
