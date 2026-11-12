package com.example.jylos.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

/**
 * Links are taken as written (folder path included, heading and display text dropped), and a
 * {@code [[link]]} inside code is text, not a link: it is not extracted nor rendered.
 */
class WikiLinkRawLinksTest {

    @Test
    void linksKeepTheirPathAndDropHeadingAndDisplayText() {
        assertEquals(List.of("Carpeta/Nota", "Otra nota", "Simple"),
                WikiLinkResolver.extractRawLinks(
                        "[[Carpeta/Nota#Sección|ver]] [texto](Otra%20nota.md) [[Simple]] [[Simple|otra vez]]"));
    }

    @Test
    void linksInsideCodeAreIgnored() {
        String md = String.join("\n",
                "Antes `[[EnLinea]]` y ``[[Doble]] ` sigue`` [[Fuera]]",
                "```",
                "[[BloqueBacktick]]",
                "```",
                "~~~js",
                "[[BloqueTilde]]",
                "~~~",
                "[[Despues]]");
        assertEquals(List.of("Fuera", "Despues"), WikiLinkResolver.extractRawLinks(md));
    }

    @Test
    void anUnclosedFenceRunsToTheEnd() {
        assertEquals(List.of("Antes"), WikiLinkResolver.extractRawLinks("[[Antes]]\n```\n[[Dentro]]\n"));
    }

    @Test
    void externalLinksAnchorsAndAttachmentsAreNotNoteLinks() {
        assertEquals(List.of(), WikiLinkResolver.extractRawLinks(
                "[g](https://x.com) [s](#sec) [m](mailto:a@b.c) [[foto.png]] ![i](img.png)"));
    }

    @Test
    void renderingLeavesLinksInCodeAsTheyAre() {
        String html = WikiLinkResolver.resolve("`[[Nota]]` y [[Nota]]", Set.of("Nota"));
        assertTrue(html.startsWith("`[[Nota]]` y <a class=\"wikilink\""), html);
    }

    @Test
    void renderingAsksTheGivenTestWhetherTheLinkExists() {
        String html = WikiLinkResolver.resolve("[[Carpeta/Nota]] [[Otra/Nota]]",
                link -> link.equals("Carpeta/Nota"));
        assertTrue(html.contains("class=\"wikilink\" href=\"jylos://open-note/Carpeta/Nota\" data-target=\"Carpeta/Nota\""),
                html);
        assertTrue(html.contains("class=\"wikilink wikilink-new\""), html);
        assertFalse(html.contains("[["), html);
    }
}
