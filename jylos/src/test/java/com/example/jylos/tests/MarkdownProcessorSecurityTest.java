package com.example.jylos.tests;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.example.jylos.util.MarkdownProcessor;

/**
 * Tests for MarkdownProcessor behavior.
 *
 * <p>Note: escapeHtml and sanitizeUrls are intentionally DISABLED in
 * MarkdownProcessor because Jylos is a local-only desktop app where
 * all content is user-authored. These tests verify that the processor
 * renders Markdown correctly, including raw HTML and custom protocols
 * (needed for WikiLinks).</p>
 */
class MarkdownProcessorSecurityTest {

    @Test
    void markdownToHtmlRendersRawHtml() {
        // Raw HTML should pass through (needed for user-authored HTML and WikiLinks)
        String html = MarkdownProcessor.markdownToHtml("<b>bold</b>");
        assertTrue(html.contains("<b>bold</b>"),
                "Raw HTML should pass through for local desktop app");
    }

    @Test
    void markdownToHtmlPreservesCustomProtocols() {
        // WikiLinks use jylos:// protocol; it must not be stripped
        String html = MarkdownProcessor.markdownToHtml("[My Note](jylos://open-note/My%20Note)");
        assertTrue(html.contains("jylos://open-note/My%20Note"),
                "Custom jylos:// protocol must be preserved for WikiLinks");
    }

    @Test
    void markdownToHtmlRendersStandardMarkdown() {
        String html = MarkdownProcessor.markdownToHtml("# Hello\n\nWorld");
        assertTrue(html.contains("<h1 id=\"hello\">Hello</h1>"));
        assertTrue(html.contains("<p>World</p>"));
    }

    @Test
    void markdownToHtmlSlugifiesDecomposedAndPrecomposedAccentsTheSame() {
        // "Introducción" spelled with a standalone COMBINING ACUTE ACCENT (U+0301)
        // after the 'o' — NFD form, what some external tools/paste sources produce —
        // instead of one precomposed 'ó' (U+00F3, NFC) — must slug the same, or a TOC
        // link copy-pasted from one source stops matching a heading typed directly.
        String decomposedHeading = "Introducción";
        String html = MarkdownProcessor.markdownToHtml("# " + decomposedHeading);
        assertTrue(html.contains("<h1 id=\"introducción\">"),
                "NFD-composed accent must slug the same as the precomposed character");
    }

    @Test
    void markdownToHtmlKeepsAccentedLettersInHeadingSlugs() {
        // Real-world TOC links (GitHub-style sluggers, e.g. VSCode's Markdown TOC
        // generators) keep accented letters — "Introducción" slugs to "introducción",
        // not "introduccin". A "-" surrounded by spaces yields a triple hyphen, since
        // each side's space is its own whitespace run.
        String html = MarkdownProcessor.markdownToHtml(
                "# Introducción\n\n## Adición de un Repositorio Remoto\n\n### Flujos de trabajo - GitFlow");
        assertTrue(html.contains("<h1 id=\"introducción\">Introducción</h1>"));
        assertTrue(html.contains("<h2 id=\"adición-de-un-repositorio-remoto\">"));
        assertTrue(html.contains("<h3 id=\"flujos-de-trabajo---gitflow\">"));
    }

    @Test
    void markdownToHtmlGivesEachHeadingASlugIdForAnchorNavigation() {
        String html = MarkdownProcessor.markdownToHtml("## Getting Started\n\n### API Reference!");
        assertTrue(html.contains("<h2 id=\"getting-started\">Getting Started</h2>"));
        assertTrue(html.contains("<h3 id=\"api-reference\">API Reference!</h3>"));
    }

    @Test
    void markdownToHtmlDedupesRepeatedHeadingSlugsInDocumentOrder() {
        String html = MarkdownProcessor.markdownToHtml("# Overview\n\ntext\n\n# Overview");
        assertTrue(html.contains("<h1 id=\"overview\">Overview</h1>"),
                "First occurrence keeps the plain slug");
        assertTrue(html.contains("<h1 id=\"overview-1\">Overview</h1>"),
                "Second occurrence must not collide with the first heading's id");
    }

    @Test
    void markdownToHtmlRendersSoftBreaksAsHtmlBreaks() {
        String html = MarkdownProcessor.markdownToHtml("first\nsecond");

        assertTrue(html.contains("<p>first<br>second</p>"));
    }
}
