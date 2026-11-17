package com.example.jylos.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;

/** The colours of a theme, read from the variables its stylesheets give {@code .root}. */
class ThemePaletteTest {

    private static final String BASE = """
            /* the base theme */
            .root {
                -fx-base-bg: #1e1e1e;
                -fx-sidebar-bg: #252525;
                -fx-header-bg: #2a2a2a;
                -fx-accent: #5563d6;
                -fx-text-main: #e0e0e0;
                -fx-text-muted: #a0a0a0;
                -fx-text-faint: #777777;
                -fx-fn-border-color: #3a3a3a;
                -fx-hover-bg: #333333;
                -fx-selected-bg: #5563d6;
            }
            .sidebar { -fx-base-bg: #ff0000; }
            """;

    @Test
    void aThemeOverTheBaseTakesItsOwnColoursAndTheBaseOnesItLeavesOut() {
        String theme = """
                .root,
                .root-container {
                    -fx-base-bg: #06120D;
                    -fx-text-main: #d0ffe1;
                    -fx-accent: #7dffb3;
                }
                """;

        ThemePalette palette = ThemePalette.fromStylesheets(List.of(BASE, theme));

        assertEquals("#06120d", palette.background());
        assertEquals("#d0ffe1", palette.text());
        assertEquals("#7dffb3", palette.accent());
        assertEquals("#3a3a3a", palette.border(), "left to the base");
        assertEquals("#252525", palette.surface());
        assertEquals("#a0a0a0", palette.muted());
    }

    @Test
    void onlyTheRootCountsAndCommentsAreIgnored() {
        String theme = """
                /* .root { -fx-base-bg: #111111; } */
                .note-card { -fx-base-bg: #222222; }
                .root-container { -fx-base-bg: #333333; }
                """;

        assertEquals("#1e1e1e", ThemePalette.fromStylesheets(List.of(BASE, theme)).background());
    }

    @Test
    void aColourGivenAsAnotherVariableIsThatVariablesColour() {
        String theme = ".root { -fx-text-main: #f2e5bc; -fx-selected-bg: -fx-accent; -fx-accent: rgb(215, 153, 33); }";

        ThemePalette palette = ThemePalette.fromStylesheets(List.of(BASE, theme));

        assertEquals("#d79921", palette.accent());
        assertEquals("#d79921", palette.selection());
    }

    @Test
    void aValueThatIsNotAPlainColourKeepsTheOneBeforeIt() {
        String theme = ".root { -fx-hover-bg: derive(-fx-base-bg, 10%); -fx-text-faint: linear-gradient(red, blue); }";

        ThemePalette palette = ThemePalette.fromStylesheets(List.of(BASE, theme));

        assertEquals("#333333", palette.hover());
        assertEquals("#777777", palette.faint());
    }

    @Test
    void withoutABackgroundOrTextThereIsNoPalette() {
        assertNull(ThemePalette.fromStylesheets(List.of(".root { -fx-accent: #7dffb3; }")));
        assertNull(ThemePalette.fromStylesheets(List.of()));
    }

    @Test
    void theRealDarkThemeAndRetroPhosphorGiveTheirColours() throws IOException {
        String dark = resource("/com/example/jylos/ui/css/dark-theme.css");
        Path retro = Path.of("..", "themes", "retro-phosphor", "theme.css");

        ThemePalette palette = ThemePalette.fromStylesheets(List.of(dark, Files.readString(retro)));

        assertNotNull(palette);
        assertEquals("#06120d", palette.background());
        assertEquals("#d0ffe1", palette.text());
        assertEquals("#7dffb3", palette.accent());
        assertEquals("#2f7f5d", palette.border());
        assertEquals("#1e1e1e", ThemePalette.fromStylesheets(List.of(dark)).background());
    }

    @Test
    void readsStylesheetsFromTheirAddresses() throws IOException {
        Path theme = Files.createTempFile("theme", ".css");
        try {
            Files.writeString(theme, ".root { -fx-base-bg: #282828; -fx-text-main: #ebdbb2; }");

            ThemePalette palette = ThemePalette.read(List.of(theme.toUri().toString()));

            assertEquals("#282828", palette.background());
            assertEquals("#ebdbb2", palette.text());
        } finally {
            Files.deleteIfExists(theme);
        }
    }

    private static String resource(String path) throws IOException {
        try (InputStream in = ThemePaletteTest.class.getResourceAsStream(path)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
