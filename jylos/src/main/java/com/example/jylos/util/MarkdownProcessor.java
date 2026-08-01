package com.example.jylos.util;

import org.commonmark.node.Node;
import org.commonmark.Extension;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;
import org.commonmark.ext.gfm.tables.TablesExtension;
import org.commonmark.ext.gfm.strikethrough.StrikethroughExtension;
import org.commonmark.ext.autolink.AutolinkExtension;

import java.util.Arrays;
import java.util.List;

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
}
