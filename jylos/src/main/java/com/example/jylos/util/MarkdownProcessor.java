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
 * Utility class for processing Markdown content.
 * Provides conversion from Markdown to HTML for preview functionality.
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
     * escapeHtml and sanitizeUrls are intentionally DISABLED:
     * - This is a local-only desktop application; all content is user-authored.
     * - WikiLinks inject standard Markdown links with a custom jylos://
     *   protocol that sanitizeUrls would strip.
     * - Users may embed raw HTML in their notes (e.g. <details>, <kbd>).
     */
    private static final HtmlRenderer RENDERER = HtmlRenderer.builder()
        .extensions(EXTENSIONS)
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
