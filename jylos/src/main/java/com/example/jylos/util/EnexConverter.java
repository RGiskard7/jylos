package com.example.jylos.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Converts Evernote ENML note content (the XHTML-like markup inside an
 * {@code .enex} export's {@code <content>} CDATA) into plain Markdown.
 *
 * <p>Scope (documented, intentionally basic): block elements become line breaks,
 * common inline emphasis maps to Markdown, lists become {@code -} / checkbox items,
 * links become {@code [text](url)}, and everything else is stripped to text.
 * Attachments ({@code <en-media>}) are noted as placeholders — binary resources are
 * not imported.</p>
 *
 * @author Edu Díaz (RGiskard7)
 * @since 2.0.0
 */
public final class EnexConverter {

    private EnexConverter() {
        // utility class
    }

    private static final Pattern LINK = Pattern.compile(
            "<a [^>]*href=\"([^\"]*)\"[^>]*>(.*?)</a>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    /** Converts an ENML body to Markdown text. Null-safe. */
    public static String toMarkdown(String enml) {
        if (enml == null || enml.isBlank()) {
            return "";
        }
        String s = enml;

        // Drop the XML prolog / doctype / outer <en-note> wrapper.
        s = s.replaceAll("(?is)<\\?xml[^>]*\\?>", "");
        s = s.replaceAll("(?is)<!DOCTYPE[^>]*>", "");
        s = s.replaceAll("(?is)</?en-note[^>]*>", "");

        // Links first (need their attributes before tags are stripped).
        Matcher link = LINK.matcher(s);
        StringBuilder sb = new StringBuilder();
        while (link.find()) {
            String url = link.group(1);
            String text = stripTags(link.group(2)).strip();
            link.appendReplacement(sb, Matcher.quoteReplacement(
                    "[" + (text.isEmpty() ? url : text) + "](" + url + ")"));
        }
        link.appendTail(sb);
        s = sb.toString();

        // Headings.
        for (int h = 6; h >= 1; h--) {
            s = s.replaceAll("(?is)<h" + h + "[^>]*>(.*?)</h" + h + ">",
                    "\n" + "#".repeat(h) + " $1\n");
        }

        // Inline emphasis and code.
        s = s.replaceAll("(?is)<(b|strong)[^>]*>(.*?)</\\1>", "**$2**");
        s = s.replaceAll("(?is)<(i|em)[^>]*>(.*?)</\\1>", "*$2*");
        s = s.replaceAll("(?is)<code[^>]*>(.*?)</code>", "`$1`");

        // Evernote to-do checkboxes.
        s = s.replaceAll("(?i)<en-todo[^>]*checked=\"true\"[^>]*/?>", "[x] ");
        s = s.replaceAll("(?i)<en-todo[^>]*/?>", "[ ] ");

        // Attachments cannot be carried over from ENML; leave an explicit marker.
        s = s.replaceAll("(?is)<en-media[^>]*/?>", "*(attachment not imported)*");

        // List items, block separators.
        s = s.replaceAll("(?is)<li[^>]*>(.*?)</li>", "\n- $1");
        s = s.replaceAll("(?i)<br[^>]*/?>", "\n");
        s = s.replaceAll("(?is)</(div|p|ul|ol|table|tr)>", "\n");

        // Strip any remaining tags and decode the common entities.
        s = stripTags(s);
        s = s.replace("&nbsp;", " ")
             .replace("&amp;", "&")
             .replace("&lt;", "<")
             .replace("&gt;", ">")
             .replace("&quot;", "\"")
             .replace("&#39;", "'")
             .replace("&apos;", "'");

        // Collapse excessive blank lines and trim.
        s = s.replaceAll("[ \\t]+\\n", "\n").replaceAll("\\n{3,}", "\n\n");
        return s.strip();
    }

    private static String stripTags(String s) {
        return s.replaceAll("(?s)<[^>]+>", "");
    }
}
