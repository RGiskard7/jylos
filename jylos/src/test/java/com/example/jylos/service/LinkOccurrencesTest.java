package com.example.jylos.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.example.jylos.service.LinkOccurrences.Occurrence;

/**
 * Where the links and the unlinked mentions are in a note: the line they are on (shown as
 * context in the panel) and their place in it (to highlight them and to turn a mention into a
 * link).
 */
class LinkOccurrencesTest {

    private static final String NOTE = String.join("\n",
            "# Título",
            "Aquí va [[Target]] y también [[Target|alias]] en una línea.",
            "Otra con [Target](Target.md) markdown.",
            "```",
            "[[EnCodigo]]",
            "```",
            "Y `[[EnLinea]]` no.");

    @Test
    void linksCarryTheirLineTheirPlaceAndWhatTheyPointTo() {
        List<Occurrence> links = LinkOccurrences.links(NOTE);

        assertEquals(3, links.size());
        Occurrence first = links.get(0);
        assertEquals(1, first.line());
        assertEquals("Aquí va [[Target]] y también [[Target|alias]] en una línea.", first.lineText());
        assertEquals("[[Target]]", first.matched());
        assertEquals("Target", first.target());
        assertEquals("[[Target|alias]]", links.get(1).matched());
        assertEquals(1, links.get(1).line());
        assertEquals("[Target](Target.md)", links.get(2).matched());
        assertEquals(2, links.get(2).line());
        assertEquals("Target", links.get(2).target());
    }

    @Test
    void mentionsAreTheTermsOutsideLinksCodeAndFrontmatterByWholeWords() {
        String md = String.join("\n",
                "---",
                "nota: Target en las propiedades",
                "---",
                "Hablo de Target y de target otra vez, no de Targets.",
                "Ya enlazado [[Target]] y [con texto Target](https://x.com) y https://target.example/Target.",
                "`Target` en código",
                "El Alias Target completo");

        List<Occurrence> found = LinkOccurrences.mentions(md, List.of("Target", "Alias Target"));

        // line 3: two (case-insensitive); line 6: the whole alias and the word inside it
        assertEquals(List.of("3:Target", "3:target", "6:Alias Target", "6:Target"),
                found.stream().map(o -> o.line() + ":" + o.matched()).toList());
        assertEquals("Hablo de Target y de target otra vez, no de Targets.", found.get(0).lineText());
    }

    @Test
    void replacingAMentionRewritesOnlyThatOccurrence() {
        String md = "Uno Target\nDos Target y Target";
        Occurrence second = LinkOccurrences.mentions(md, List.of("Target")).get(2);

        assertEquals("Uno Target\nDos Target y [[Target]]", LinkOccurrences.replace(md, second, "[[Target]]"));
    }

    @Test
    void replacingAMentionThatChangedSinceItWasFoundDoesNothing() {
        String md = "Uno Target";
        Occurrence mention = LinkOccurrences.mentions(md, List.of("Target")).get(0);

        assertNull(LinkOccurrences.replace("Uno Otra cosa", mention, "[[Target]]"));
        assertNull(LinkOccurrences.replace("Antes\nUno Target", mention, "[[Target]]"),
                "the line moved: the position no longer means anything");
    }

    @Test
    void shortTermsAndEmptyNotesFindNothing() {
        assertEquals(List.of(), LinkOccurrences.mentions("a b c", List.of("a")));
        assertEquals(List.of(), LinkOccurrences.mentions("", List.of("Target")));
        assertEquals(List.of(), LinkOccurrences.mentions(null, List.of("Target")));
        assertEquals(List.of(), LinkOccurrences.links(null));
    }
}
