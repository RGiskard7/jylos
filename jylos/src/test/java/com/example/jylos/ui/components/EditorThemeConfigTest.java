package com.example.jylos.ui.components;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.example.jylos.util.ThemePalette;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/** What the editor page is told about the theme: dark or light, the accent, an external theme's colours. */
class EditorThemeConfigTest {

    private static final ThemePalette GRUVBOX = new ThemePalette("#282828", "#32302f", "#3c3836", "#ebdbb2",
            "#bdae93", "#928374", "#504945", "#45403d", "#d79921", "#d79921");

    @Test
    void anExternalThemesColoursGoToTheEditor() {
        JsonObject config = JsonParser.parseString(MarkdownEditorView.themeConfig(true, "", GRUVBOX)).getAsJsonObject();
        JsonObject palette = config.getAsJsonObject("palette");

        assertTrue(config.get("dark").getAsBoolean());
        assertEquals("#282828", palette.get("background").getAsString());
        assertEquals("#ebdbb2", palette.get("foreground").getAsString());
        assertEquals("#bdae93", palette.get("muted").getAsString());
        assertEquals("#3c3836", palette.get("panel").getAsString());
        assertEquals("#32302f", palette.get("surface").getAsString());
        assertEquals("#504945", palette.get("border").getAsString());
        assertEquals("#45403d", palette.get("selection").getAsString(), "a soft selection, not the accent behind the text");
        assertEquals("#d79921", palette.get("accent").getAsString());
    }

    /** The page keeps what it was told before: without a palette it must be told so, to drop the last one. */
    @Test
    void withoutAPaletteTheEditorIsToldToDropIt() {
        JsonObject config = JsonParser.parseString(MarkdownEditorView.themeConfig(false, "#ff00aa", null)).getAsJsonObject();

        assertTrue(config.has("palette"));
        assertTrue(config.get("palette").isJsonNull());
        assertEquals("#ff00aa", config.get("accent").getAsString());
    }
}
