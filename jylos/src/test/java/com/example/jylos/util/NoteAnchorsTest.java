package com.example.jylos.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Optional;

import org.junit.jupiter.api.Test;

/**
 * Where a link's heading or block is in the note it opens: the character offset (for the
 * editor) and the element id (for the reading view), the same ids the renderer gives.
 */
class NoteAnchorsTest {

    private static final String NOTE = String.join("\n",
            "# Intro",                 // 0
            "Texto",                   // 8
            "## Mi Sección",           // 14
            "Un párrafo ^bloque-1",    // 28
            "## Mi Sección",           // 49
            "",
            "```",
            "# No es título",
            "```");

    @Test
    void aHeadingIsFoundByItsTextInAnyCase() {
        assertEquals(Optional.of(new NoteAnchors.Anchor(14, "mi-sección")), NoteAnchors.locate(NOTE, "mi sección"));
        assertEquals(Optional.of(new NoteAnchors.Anchor(0, "intro")), NoteAnchors.locate(NOTE, "Intro"));
    }

    /** Two headings with the same id: the renderer numbers the second, and so must the jump. */
    @Test
    void aHeadingWhoseIdRepeatsAnEarlierOneGetsItsNumber() {
        assertEquals(Optional.of(new NoteAnchors.Anchor(8, "hola-1")), NoteAnchors.locate("# Hola!\n## Hola\n", "Hola"));
    }

    @Test
    void aNestedHeadingPathUsesItsLastPart() {
        assertEquals(Optional.of(new NoteAnchors.Anchor(14, "mi-sección")), NoteAnchors.locate(NOTE, "Intro#Mi Sección"));
    }

    @Test
    void aBlockIsFoundByItsId() {
        assertEquals(Optional.of(new NoteAnchors.Anchor(28, "blockref-bloque-1")), NoteAnchors.locate(NOTE, "^bloque-1"));
    }

    @Test
    void headingsInCodeAndUnknownTargetsAreNotFound() {
        assertEquals(Optional.empty(), NoteAnchors.locate(NOTE, "No es título"));
        assertEquals(Optional.empty(), NoteAnchors.locate(NOTE, "^nada"));
        assertEquals(Optional.empty(), NoteAnchors.locate(NOTE, ""));
        assertEquals(Optional.empty(), NoteAnchors.locate(null, "Intro"));
    }

    @Test
    void theSubpathOfALinkIsWhatFollowsTheFirstHash() {
        assertEquals("Sección", WikiLinkResolver.subpathOf("Nota#Sección|texto"));
        assertEquals("^b1", WikiLinkResolver.subpathOf("Nota#^b1"));
        assertEquals("H", WikiLinkResolver.subpathOf("#H"));
        assertEquals(null, WikiLinkResolver.subpathOf("Nota|a#b"));
        assertEquals(null, WikiLinkResolver.subpathOf("Nota"));
    }
}
