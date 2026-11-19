package com.example.jylos.tests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.example.jylos.util.MarkdownPreview;
import com.example.jylos.util.ThemePalette;

/**
 * The reading view takes the colours of an external theme; with the built-in themes it keeps its
 * own, unchanged.
 */
class PreviewThemePaletteTest {

    private static final ThemePalette GRUVBOX = new ThemePalette("#282828", "#32302f", "#3c3836", "#ebdbb2",
            "#bdae93", "#928374", "#504945", "#3c3836", "#d79921", "#d79921");

    @Test
    void anExternalThemesColoursPaintThePage() {
        String html = MarkdownPreview.buildPreviewHtml("# Título\n\n> cita\n\n[[Otra]]", true, List.of(),
                MarkdownPreview.PreviewOptions.none().withPalette(GRUVBOX));

        String rules = html.substring(html.lastIndexOf("/* theme palette */"));
        assertTrue(rules.contains("background-color: #282828"), "the page's background");
        assertTrue(rules.contains("color: #ebdbb2"), "the text");
        assertTrue(rules.contains("#d79921"), "links and quotes in the accent");
        assertTrue(rules.contains("#504945"), "borders");
        assertTrue(html.indexOf("/* theme palette */") > html.indexOf("#1E1E1E"),
                "after the built-in colours, so they win");
    }

    @Test
    void withoutAPaletteThePageIsAsBefore() {
        MarkdownPreview.PreviewOptions none = MarkdownPreview.PreviewOptions.none();

        String dark = MarkdownPreview.buildPreviewHtml("# Título", true, List.of(), none);
        String light = MarkdownPreview.buildPreviewHtml("# Título", false, List.of(), none);

        assertFalse(dark.contains("/* theme palette */"));
        assertFalse(light.contains("/* theme palette */"));
        assertTrue(dark.contains("background-color: #1E1E1E"));
        assertTrue(light.contains("background-color: #FFFFFF"));
    }

    @Test
    void theEmptyPageTakesThePaletteToo() {
        String themed = MarkdownPreview.buildEmptyHtml(true, GRUVBOX);

        assertTrue(themed.contains("background-color: #282828"));
        assertFalse(themed.contains("#1E1E1E"));
        assertEquals(MarkdownPreview.buildEmptyHtml(true), MarkdownPreview.buildEmptyHtml(true, null));
    }
}
