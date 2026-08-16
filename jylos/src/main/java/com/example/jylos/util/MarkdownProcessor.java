package com.example.jylos.util;

import org.commonmark.node.Code;
import org.commonmark.node.Heading;
import org.commonmark.node.Node;
import org.commonmark.node.Text;
import org.commonmark.Extension;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.AttributeProvider;
import org.commonmark.renderer.html.HtmlRenderer;
import org.commonmark.ext.gfm.tables.TablesExtension;
import org.commonmark.ext.gfm.strikethrough.StrikethroughExtension;
import org.commonmark.ext.autolink.AutolinkExtension;

import java.text.Normalizer;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * CommonMark-based Markdown-to-HTML renderer used by the preview pipeline.
 *
 * <p>This class owns only Markdown parsing/rendering. Jylos-specific features
 * such as wiki-links, transclusion, local-image embedding, emoji fallback and
 * plugin preview enhancers are layered in {@link MarkdownPreview} before or after
 * this renderer.</p>
 */
public class MarkdownProcessor {
    
    private static final List<Extension> EXTENSIONS = Arrays.asList(
        TablesExtension.create(),
        StrikethroughExtension.create(),
        AutolinkExtension.create()
    );
    
    private static final Parser PARSER = Parser.builder()
        .extensions(EXTENSIONS)
        .build();
    
    /*
     * escapeHtml and sanitizeUrls are intentionally disabled for preview parity with
     * Markdown apps such as Obsidian: users may author raw HTML, and Jylos wiki-links
     * use a custom jylos:// protocol that URL sanitizing would remove.
     */
    private static final HtmlRenderer RENDERER = HtmlRenderer.builder()
        .extensions(EXTENSIONS)
        // JavaFX WebView 23 renders raw soft-break characters inside paragraph text as
        // replacement boxes. Emit semantic HTML line breaks instead of relying on its
        // whitespace handling.
        .softbreak("<br>")
        // Gives every heading a stable #anchor id, the same convention GitHub/GitLab
        // use, so in-note links (e.g. TableOfContentsPlugin's generated TOC) and
        // Outline-style "jump to heading" navigation actually have something to land
        // on. A new HeadingIdAttributeProvider per render, so its per-document dedup
        // counter (two "Overview" headings must not collide on the same id) never
        // leaks between notes.
        .attributeProviderFactory(context -> new HeadingIdAttributeProvider())
        .build();
    
    /**
     * Converts Markdown text to HTML.
     * 
     * @param markdown The Markdown text to convert
     * @return HTML representation of the Markdown text
     */
    public static String markdownToHtml(String markdown) {
        if (markdown == null || markdown.trim().isEmpty()) {
            return "";
        }
        
        try {
            Node document = PARSER.parse(markdown);
            return RENDERER.render(document);
        } catch (Exception e) {
            // Fallback to plain text if Markdown parsing fails
            return "<pre>" + escapeHtml(markdown) + "</pre>";
        }
    }
    
    /**
     * Escapes HTML special characters in text.
     *
     * @param text The text to escape
     * @return HTML-escaped text
     */
    private static String escapeHtml(String text) {
        if (text == null) {
            return "";
        }
        return text.replace("&", "&amp;")
                   .replace("<", "&lt;")
                   .replace(">", "&gt;")
                   .replace("\"", "&quot;")
                   .replace("'", "&#x27;");
    }

    /**
     * Converts heading text to a GitHub-style anchor slug: lowercase, keeping letters
     * (any script — {@code \p{L}} is Unicode-aware, so "Introducción" keeps its
     * "ó") and digits, each run of whitespace collapsed to a single hyphen. Used both
     * for this renderer's heading {@code id}s and by {@code TableOfContentsPlugin}'s
     * generated links (via this same method) — the two must always agree, or a TOC
     * link inserted into a note stops resolving to its heading.
     *
     * <p>Normalises to NFC first: text pasted from other tools can spell an accented
     * letter as a base letter plus a separate combining accent mark (NFD) instead of
     * one precomposed character (NFC). {@code \p{L}} only matches the letter itself —
     * a standalone combining mark is its own, different Unicode category — so without
     * this, "e" + "´" would silently lose the accent while "é" typed directly would
     * not, producing a different id for what looks like identical text.</p>
     */
    public static String slugifyHeading(String text) {
        if (text == null) {
            return "";
        }
        return Normalizer.normalize(text, Normalizer.Form.NFC)
                .toLowerCase()
                .replaceAll("[^\\p{L}\\p{N}\\s-]", "")
                .replaceAll("\\s+", "-");
    }

    /** Plain-text content of a node, walking past inline formatting (bold, links, ...). */
    private static String extractText(Node node) {
        StringBuilder text = new StringBuilder();
        appendText(node, text);
        return text.toString();
    }

    private static void appendText(Node node, StringBuilder out) {
        for (Node child = node.getFirstChild(); child != null; child = child.getNext()) {
            if (child instanceof Text textNode) {
                out.append(textNode.getLiteral());
            } else if (child instanceof Code codeNode) {
                out.append(codeNode.getLiteral());
            } else {
                appendText(child, out);
            }
        }
    }

    /** Assigns each heading a {@link #slugifyHeading} id, deduped within one render. */
    private static class HeadingIdAttributeProvider implements AttributeProvider {
        private final Map<String, Integer> seenSlugs = new HashMap<>();

        @Override
        public void setAttributes(Node node, String tagName, Map<String, String> attributes) {
            if (!(node instanceof Heading heading)) {
                return;
            }
            String base = slugifyHeading(extractText(heading));
            if (base.isEmpty()) {
                return;
            }
            int occurrence = seenSlugs.getOrDefault(base, 0);
            seenSlugs.put(base, occurrence + 1);
            attributes.put("id", occurrence == 0 ? base : base + "-" + occurrence);
        }
    }
}
