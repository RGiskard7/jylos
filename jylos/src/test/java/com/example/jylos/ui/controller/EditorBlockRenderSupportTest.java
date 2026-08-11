package com.example.jylos.ui.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * Guards the fenced-block contract the editor's Live Preview shares with the host: both
 * sides must find the same blocks and derive the same lookup key, or plugin-rendered
 * blocks silently never appear.
 */
class EditorBlockRenderSupportTest {

    private static final Path LIVE_PREVIEW = Path.of("editor-web", "src", "live-preview.js");

    @Test
    void extractsFencedBlocksWithAnInfoString() {
        List<EditorBlockRenderSupport.Block> blocks = EditorBlockRenderSupport.extractBlocks("""
                Intro paragraph.

                ```dataview
                TABLE rating
                FROM #book
                ```

                ```java
                int x = 1;
                ```

                Trailing text.
                """);

        assertEquals(2, blocks.size(), "Both fenced blocks should be found");
        assertEquals("dataview", blocks.get(0).language());
        assertEquals("TABLE rating\nFROM #book", blocks.get(0).body(),
                "The body should exclude the fences and be trimmed");
        assertEquals("java", blocks.get(1).language());
    }

    @Test
    void ignoresFencesWithoutAnInfoStringAndUnclosedBlocks() {
        assertTrue(EditorBlockRenderSupport.extractBlocks("```\nplain\n```").isEmpty(),
                "A fence with no info string belongs to no renderer");
        assertTrue(EditorBlockRenderSupport.extractBlocks("```dataview\nLIST").isEmpty(),
                "A half-typed block must keep showing its source rather than render");
    }

    @Test
    void keyIsLowercasedLanguageThenNewlineThenTrimmedBody() {
        assertEquals("dataview\nLIST", EditorBlockRenderSupport.blockKey("DataView", "\n  LIST  \n"),
                "Key normalisation must match the editor's fencedBlockKey()");
        assertEquals(EditorBlockRenderSupport.blockKey("dataview", "LIST"),
                EditorBlockRenderSupport.extractBlocks("```dataview\nLIST\n```").get(0).key());
    }

    @Test
    void tildeFencesAndIndentedFencesAreSupported() {
        List<EditorBlockRenderSupport.Block> tilde =
                EditorBlockRenderSupport.extractBlocks("~~~dataview\nLIST\n~~~");
        assertEquals(1, tilde.size(), "Tilde fences are valid CommonMark and must work too");
        assertEquals("LIST", tilde.get(0).body());

        List<EditorBlockRenderSupport.Block> indented =
                EditorBlockRenderSupport.extractBlocks("  ```dataview\n  LIST\n  ```");
        assertEquals(1, indented.size(), "Up to three leading spaces still opens a fence");
    }

    @Test
    void editorSideDerivesTheSameKeyShape() throws IOException {
        // The two implementations cannot share code across the Java/JavaScript boundary,
        // so this checks the editor still builds `language + "\n" + trimmed body` and
        // still requires the closing fence to repeat the opening one.
        String livePreview = Files.readString(LIVE_PREVIEW, StandardCharsets.UTF_8);

        assertTrue(livePreview.contains("${open.language.toLowerCase()}\\n${open.body.join(\"\\n\").trim()}"),
                "live-preview.js should build the block key as lowercased language + newline + trimmed body");
        assertTrue(livePreview.contains("close[1] === open.marker"),
                "live-preview.js should pair fences exactly, matching the host's backreference");
    }
}
