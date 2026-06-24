package com.example.jylos.tests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.example.jylos.util.RichLinks;
import com.example.jylos.util.RichLinks.RichLink;

class RichLinksTest {

    // ── OpenGraph parsing ────────────────────────────────────────────────────

    @Test
    void parsesOpenGraphTags() {
        String html = """
                <html><head>
                  <meta property="og:title" content="The Title">
                  <meta property="og:description" content="A summary">
                  <meta property="og:image" content="https://ex.com/og.png">
                  <meta property="og:site_name" content="Example">
                </head><body></body></html>""";
        RichLink link = RichLinks.parseMetadata(html, "https://ex.com/post");
        assertEquals("The Title", link.title());
        assertEquals("A summary", link.description());
        assertEquals("https://ex.com/og.png", link.image());
        assertEquals("Example", link.siteName());
    }

    @Test
    void fallsBackToTitleDescriptionAndHost() {
        String html = """
                <html><head>
                  <title>Plain Title</title>
                  <meta name="description" content="Meta description">
                </head><body></body></html>""";
        RichLink link = RichLinks.parseMetadata(html, "https://www.site.org/x");
        assertEquals("Plain Title", link.title());
        assertEquals("Meta description", link.description());
        assertEquals("site.org", link.siteName()); // www. stripped, used as fallback
    }

    @Test
    void hostOfStripsWww() {
        assertEquals("github.com", RichLinks.hostOf("https://github.com/a/b"));
        assertEquals("news.bbc.co.uk", RichLinks.hostOf("http://news.bbc.co.uk/x"));
        assertEquals("", RichLinks.hostOf("not a url"));
    }

    // ── Markdown generation ──────────────────────────────────────────────────

    @Test
    void toMarkdownOmitsBlankFieldsButAlwaysKeepsUrl() {
        String md = RichLinks.toMarkdown(new RichLink("https://ex.com", "Title", "", "", "Example"));
        assertTrue(md.startsWith("::: rich-link\n"));
        assertTrue(md.contains("url: https://ex.com"));
        assertTrue(md.contains("title: Title"));
        assertTrue(md.contains("siteName: Example"));
        assertFalse(md.contains("description:"));
        assertFalse(md.contains("image:"));
        assertTrue(md.endsWith(":::"));
    }

    @Test
    void toMarkdownCollapsesNewlinesInValues() {
        String md = RichLinks.toMarkdown(new RichLink("https://ex.com", "Two\nlines", "", "", ""));
        assertTrue(md.contains("title: Two lines"), md);
    }

    // ── Preview rendering ────────────────────────────────────────────────────

    @Test
    void renderTurnsBlockIntoCardHtml() {
        String md = "Intro\n\n::: rich-link\nurl: https://ex.com\ntitle: Hello\nsiteName: Example\n:::\n\nOutro";
        String out = RichLinks.render(md);
        assertTrue(out.contains("class=\"rich-link-card\""));
        assertTrue(out.contains("href=\"https://ex.com\""));
        assertTrue(out.contains(">Hello<"));
        assertTrue(out.contains("Intro"));
        assertTrue(out.contains("Outro"));
        assertFalse(out.contains("::: rich-link"));
    }

    @Test
    void renderEscapesHtmlInFields() {
        String md = "::: rich-link\nurl: https://ex.com\ntitle: <script>alert(1)</script>\n:::";
        String out = RichLinks.render(md);
        assertFalse(out.contains("<script>"), "title HTML must be escaped");
        assertTrue(out.contains("&lt;script&gt;"));
    }

    @Test
    void renderOnlyEmbedsHttpImages() {
        String md = "::: rich-link\nurl: https://ex.com\nimage: javascript:alert(1)\n:::";
        String out = RichLinks.render(md);
        assertFalse(out.contains("rich-link-thumb"), "non-http image must be dropped");
    }

    @Test
    void renderLeavesBlockWithNonHttpUrlUntouched() {
        String md = "::: rich-link\nurl: ftp://ex.com\ntitle: nope\n:::";
        String out = RichLinks.render(md);
        assertTrue(out.contains("::: rich-link"), "a block without a usable http url is left as-is");
        assertFalse(out.contains("rich-link-card"));
    }

    @Test
    void renderLeavesPlainMarkdownUntouched() {
        String md = "# Heading\n\nA [normal](https://ex.com) link.";
        assertEquals(md, RichLinks.render(md));
    }
}
