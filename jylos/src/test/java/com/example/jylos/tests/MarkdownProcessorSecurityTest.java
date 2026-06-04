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
        assertTrue(html.contains("<h1>Hello</h1>"));
        assertTrue(html.contains("<p>World</p>"));
    }
}
