package com.example.jylos.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * {@code ==highlight==} and {@code > [!type] Title} callouts in the reading view, as in
 * Obsidian; neither applies inside code.
 */
class CalloutsAndHighlightTest {

    @Test
    void doubleEqualsHighlights() {
        assertEquals("<p>Esto es <mark>importante</mark> y <mark>esto <strong>también</strong></mark></p>\n",
                MarkdownProcessor.markdownToHtml("Esto es ==importante== y ==esto **también**=="));
    }

    @Test
    void highlightIsNotAppliedInCodeOrWithASingleEquals() {
        String html = MarkdownProcessor.markdownToHtml("`==código==` y a = b = c\n\n```\n==bloque==\n```");
        assertFalse(html.contains("<mark>"), html);
        assertTrue(html.contains("<code>==código==</code>"), html);
    }

    @Test
    void aCalloutHasItsTypeTitleAndContent() {
        String html = MarkdownProcessor.markdownToHtml("> [!warning] Cuidado con **esto**\n> Primera línea\n>\n> Segundo párrafo");

        assertTrue(html.startsWith("<div class=\"callout callout-orange\" data-callout=\"warning\">"), html);
        assertTrue(html.contains("<span class=\"callout-title-inner\">Cuidado con <strong>esto</strong></span>"), html);
        assertTrue(html.contains("<div class=\"callout-content\">\n<p>Primera línea</p>\n<p>Segundo párrafo</p>\n</div>"),
                html);
        assertFalse(html.contains("[!warning]"), html);
        assertFalse(html.contains("<blockquote>"), html);
    }

    @Test
    void aCalloutWithoutTitleIsTitledByItsTypeAndAliasesShareAColour() {
        String html = MarkdownProcessor.markdownToHtml("> [!faq]\n> ¿Por qué?");
        assertTrue(html.contains("class=\"callout callout-orange\" data-callout=\"faq\""), html);
        assertTrue(html.contains("<span class=\"callout-title-inner\">Faq</span>"), html);
        assertTrue(html.contains("<p>¿Por qué?</p>"), html);
    }

    @Test
    void foldableCalloutsStartOpenOrClosed() {
        String closed = MarkdownProcessor.markdownToHtml("> [!tip]- Pista\n> Oculto");
        assertTrue(closed.startsWith("<details class=\"callout callout-cyan\" data-callout=\"tip\">"), closed);
        assertTrue(closed.contains("<summary class=\"callout-title\">"), closed);

        String open = MarkdownProcessor.markdownToHtml("> [!tip]+ Pista\n> Visible");
        assertTrue(open.startsWith("<details class=\"callout callout-cyan\" data-callout=\"tip\" open>"), open);
    }

    @Test
    void unknownTypesLookLikeANoteAndPlainQuotesStayQuotes() {
        assertTrue(MarkdownProcessor.markdownToHtml("> [!loquesea] X")
                .startsWith("<div class=\"callout callout-blue\" data-callout=\"loquesea\">"));
        assertEquals("<blockquote>\n<p>Una cita normal</p>\n</blockquote>\n",
                MarkdownProcessor.markdownToHtml("> Una cita normal"));
    }

    @Test
    void calloutsNest() {
        String html = MarkdownProcessor.markdownToHtml("> [!note] Fuera\n> > [!bug] Dentro\n> > Texto");
        assertTrue(html.contains("data-callout=\"note\""), html);
        assertTrue(html.contains("data-callout=\"bug\""), html);
        assertTrue(html.indexOf("data-callout=\"bug\"") > html.indexOf("data-callout=\"note\""), html);
    }
}
