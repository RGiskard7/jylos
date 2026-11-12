package com.example.jylos.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * A link to a heading or a block of another note carries that part to the click, so the
 * note opens there; a link to a heading or block of the same note jumps within the page.
 */
class WikiLinkSubpathTest {

    @Test
    void aLinkToASectionCarriesItsSubpath() {
        String html = WikiLinkResolver.resolve("[[Nota#Mi sección]] [[Nota#^b1|ver]] [[Nota]]", link -> true);

        assertTrue(html.contains("data-target=\"Nota\" data-subpath=\"Mi sección\">Nota › Mi sección</a>"), html);
        assertTrue(html.contains("data-target=\"Nota\" data-subpath=\"^b1\">ver</a>"), html);
        assertTrue(html.endsWith("data-target=\"Nota\">Nota</a>"), "no subpath attribute without a section: " + html);
    }

    @Test
    void aMarkdownLinkToASectionCarriesItToo() {
        String html = WikiLinkResolver.resolve("[aquí](Nota.md#Detalle)", link -> true);
        assertTrue(html.contains("data-target=\"Nota\" data-subpath=\"Detalle\">aquí</a>"), html);
    }

    @Test
    void linksWithinTheSameNoteJumpToTheHeadingOrBlock() {
        String html = WikiLinkResolver.resolve("Ver [[#Introducción breve]] y [[#^paso-2|el paso]]", link -> false);

        assertEquals("Ver <a class=\"wikilink wikilink-local\" href=\"#introducción-breve\">Introducción breve</a>"
                + " y <a class=\"wikilink wikilink-local\" href=\"#blockref-paso-2\">el paso</a>", html);
    }

    @Test
    void sameNoteLinksInsideCodeStayText() {
        assertEquals("`[[#H]]`", WikiLinkResolver.resolve("`[[#H]]`", link -> true));
    }

    @Test
    void sameNoteLinksAreNotLinksToOtherNotes() {
        assertEquals(List.of("Otra"), WikiLinkResolver.extractRawLinks("[[#H]] [[Otra]]"));
        assertFalse(WikiLinkResolver.resolve("[[#H]]", link -> true).contains("data-target"));
    }
}
