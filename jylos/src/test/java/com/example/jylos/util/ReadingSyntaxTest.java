package com.example.jylos.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Obsidian syntax resolved on the Markdown before it is rendered: comments are hidden,
 * footnotes are numbered and listed at the end, {@code ^id} marks a block others can link
 * to, and {@code #tags} become clickable. None of it applies inside code.
 */
class ReadingSyntaxTest {

    // ── comments ───────────────────────────────────────────────────────────

    @Test
    void commentsAreHiddenInlineAndAcrossLines() {
        assertEquals("Visible  y fin\nTras el bloque",
                ReadingSyntax.stripComments("Visible %%oculto%% y fin\n%%\nvarias\nlíneas\n%%Tras el bloque"));
    }

    @Test
    void commentsInsideCodeStayAndAnUnclosedMarkerIsText() {
        assertEquals("`%%no%%` y\n```\n%%tampoco%%\n```\n", ReadingSyntax.stripComments("`%%no%%` y\n```\n%%tampoco%%\n```\n"));
        assertEquals("100%% seguro", ReadingSyntax.stripComments("100%% seguro"));
        assertEquals("a  c %% sin pareja", ReadingSyntax.stripComments("a %%b%% c %% sin pareja"),
                "after the pairs, a leftover marker is text, not the start of a comment");
    }

    // ── footnotes ──────────────────────────────────────────────────────────

    @Test
    void footnotesAreNumberedByFirstUseAndListedAtTheEnd() {
        ReadingSyntax.Footnotes notes = ReadingSyntax.footnotes(String.join("\n",
                "Primero[^b] y luego[^a], otra vez[^b].",
                "",
                "[^a]: Nota **A**.",
                "[^b]: Nota B",
                "    que sigue."));

        assertEquals("Primero" + ref(1, true) + " y luego" + ref(2, true) + ", otra vez" + ref(1, false) + ".\n",
                notes.markdown());
        String section = notes.html();
        assertTrue(section.startsWith("<section class=\"footnotes\">"), section);
        int first = section.indexOf("<li id=\"fn-1\">Nota B que sigue.");
        int second = section.indexOf("<li id=\"fn-2\">Nota <strong>A</strong>.");
        assertTrue(first >= 0 && second > first, section);
        assertTrue(section.contains("<a href=\"#fnref-1\" class=\"footnote-backref\"><svg"), section);
    }

    @Test
    void inlineFootnotesAndUndefinedReferences() {
        ReadingSyntax.Footnotes notes = ReadingSyntax.footnotes("Dice^[en línea] y [^nada] queda.");
        assertEquals("Dice" + ref(1, true) + " y [^nada] queda.", notes.markdown());
        assertTrue(notes.html().contains("<li id=\"fn-1\">en línea"), notes.html());
    }

    @Test
    void footnotesInsideCodeAreText() {
        ReadingSyntax.Footnotes notes = ReadingSyntax.footnotes("`[^a]`\n\n[^a]: def");
        assertTrue(notes.markdown().startsWith("`[^a]`"), notes.markdown());
        assertEquals("", notes.html(), "a definition no one references is not listed");
    }

    // ── block anchors ──────────────────────────────────────────────────────

    @Test
    void aBlockIdAtTheEndOfALineBecomesAnInvisibleAnchor() {
        assertEquals("Un párrafo<span class=\"block-anchor\" id=\"blockref-mi-id1\"></span>\n"
                        + "- item<span class=\"block-anchor\" id=\"blockref-x\"></span>\n"
                        + "`código ^no`\nprecio^2 no",
                ReadingSyntax.blockAnchors("Un párrafo ^mi-id1\n- item ^x\n`código ^no`\nprecio^2 no"));
        assertEquals("```\nlínea ^encodigo\n```", ReadingSyntax.blockAnchors("```\nlínea ^encodigo\n```"),
                "an id at the end of a line of a code block is code");
    }

    // ── tags ───────────────────────────────────────────────────────────────

    @Test
    void tagsBecomeLinksOutsideCodeLinksAndUrls() {
        String md = "Con #proyecto y #area/sub-1 al final #fin\n"
                + "No: `#codigo`, [[Nota#Sección]], [x](#ancla), https://x.com/#frag, a#b, # Título";
        String out = ReadingSyntax.tags(md);

        assertTrue(out.startsWith("Con " + tag("proyecto") + " y " + tag("area/sub-1") + " al final " + tag("fin") + "\n"),
                out);
        assertTrue(out.endsWith("No: `#codigo`, [[Nota#Sección]], [x](#ancla), https://x.com/#frag, a#b, # Título"), out);
    }

    @Test
    void aTagInsideALinkTextOrAnHtmlAttributeIsNotATag() {
        String md = "[ver #dentro](Nota.md) y [[Nota|alias #dentro]] y <span title=\"a #dentro\">x</span>";
        assertEquals(md, ReadingSyntax.tags(md));
    }

    @Test
    void headingsAndHtmlEntitiesAreNotTags() {
        assertEquals("## Título\n&#39; y &amp;", ReadingSyntax.tags("## Título\n&#39; y &amp;"));
    }

    private static String ref(int n, boolean first) {
        return "<sup class=\"footnote-ref\"" + (first ? " id=\"fnref-" + n + "\"" : "") + "><a href=\"#fn-" + n + "\">" + n
                + "</a></sup>";
    }

    private static String tag(String name) {
        return "<a class=\"tag\" href=\"jylos://tag/" + name + "\">#" + name + "</a>";
    }
}
