package com.example.jylos.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;

import org.junit.jupiter.api.Test;

/** {@code ![[Note#^id]]} embeds the block marked {@code ^id}: its paragraph, list item or the block above it. */
class TransclusionBlockTest {

    private static final String NOTE = String.join("\n",
            "# Título",
            "",
            "Primera línea del párrafo",
            "segunda línea ^parrafo",
            "",
            "- uno",
            "- dos ^item",
            "- tres",
            "",
            "| a | b |",
            "|---|---|",
            "| 1 | 2 |",
            "",
            "^tabla",
            "",
            "Fin");

    @Test
    void aMarkedParagraphIsEmbeddedWholeWithoutItsMarker() {
        assertEquals("Primera línea del párrafo\nsegunda línea\n", Transclusion.extractSection(NOTE, "^parrafo"));
    }

    @Test
    void aMarkedListItemIsEmbeddedAlone() {
        assertEquals("- dos\n", Transclusion.extractSection(NOTE, "^item"));
    }

    @Test
    void anIdOnItsOwnLineMarksTheBlockAbove() {
        assertEquals("| a | b |\n|---|---|\n| 1 | 2 |\n", Transclusion.extractSection(NOTE, "^tabla"));
    }

    @Test
    void anUnknownBlockIsMissing() {
        assertNull(Transclusion.extractSection(NOTE, "^nada"));
    }

    @Test
    void theEmbedShowsTheBlock() {
        Transclusion.Result result = Transclusion.protect("![[Otra#^item]]", link -> NOTE, Set.of("Otra"));
        String html = result.embeds().values().iterator().next();
        assertTrue(html.contains("<li>dos</li>"), html);
        assertTrue(!html.contains("uno") && !html.contains("tres"), html);
    }
}
