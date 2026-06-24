package com.example.jylos.tests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import java.util.function.Function;

import org.junit.jupiter.api.Test;

import com.example.jylos.util.Transclusion;

class TransclusionTest {

    /** Mirrors the preview pipeline: protect (expand to tokens) then restore (tokens → HTML). */
    private static String render(String markdown, Map<String, String> notes) {
        Function<String, String> resolver = notes::get;
        Transclusion.Result r = Transclusion.protect(markdown, resolver, notes.keySet());
        return Transclusion.restore(r.markdown(), r.embeds());
    }

    @Test
    void embedExpandsNoteContentIntoCard() {
        String out = render("![[Target]]", Map.of("Target", "# Hello\n\nBody text"));
        assertTrue(out.contains("class=\"embed\""), out);
        assertTrue(out.contains("class=\"embed-title\""));
        assertTrue(out.contains("Hello"));
        assertTrue(out.contains("Body text"));
        assertFalse(out.contains("![[Target]]"), "the embed syntax must be consumed");
    }

    @Test
    void missingNoteRendersNotice() {
        String out = render("![[Ghost]]", Map.of());
        assertTrue(out.contains("embed-missing"), out);
        assertTrue(out.contains("note not found"));
    }

    @Test
    void cyclicEmbedIsBroken() {
        // A embeds itself — must degrade to a notice, not loop forever.
        String out = render("![[A]]", Map.of("A", "intro ![[A]] outro"));
        assertTrue(out.contains("circular embed"), out);
    }

    @Test
    void embedHeadingExtractsOnlyThatSection() {
        String doc = "# One\nalpha\n## Two\nbeta\n# Three\ngamma";
        String out = render("![[Doc#Two]]", Map.of("Doc", doc));
        assertTrue(out.contains("beta"), out);
        assertFalse(out.contains("alpha"), "content before the section must be excluded");
        assertFalse(out.contains("gamma"), "content after the section must be excluded");
    }

    @Test
    void missingSectionRendersNotice() {
        String out = render("![[Doc#Nope]]", Map.of("Doc", "# One\nalpha"));
        assertTrue(out.contains("embed-missing"));
        assertTrue(out.contains("section not found"));
    }

    @Test
    void wikiLinksInsideEmbedAreResolved() {
        String out = render("![[T]]", Map.of("T", "see [[Other]]"));
        assertTrue(out.contains("jylos://open-note/"), out);
    }

    @Test
    void plainMarkdownIsUntouched() {
        String md = "# Heading\n\nA paragraph with [[a link]] but no embeds.";
        assertEquals(md, render(md, Map.of()));
    }

    @Test
    void nestedEmbedsExpand() {
        String out = render("![[A]]", Map.of("A", "alpha ![[B]]", "B", "beta-content"));
        assertTrue(out.contains("alpha"), out);
        assertTrue(out.contains("beta-content"));
    }
}
