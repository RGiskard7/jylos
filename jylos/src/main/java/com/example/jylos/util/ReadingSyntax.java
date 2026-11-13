package com.example.jylos.util;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Obsidian syntax resolved on the Markdown before CommonMark renders it, all of it left
 * alone inside code (fenced blocks and inline spans):
 * <ul>
 *   <li>{@code %%comment%%} (inline or across lines) is hidden;</li>
 *   <li>{@code [^label]} and {@code ^[inline]} footnotes are numbered by first use and listed
 *       at the end, each with a link back;</li>
 *   <li>{@code ^block-id} at the end of a line becomes an invisible anchor, so
 *       {@code [[Note#^block-id]]} can scroll to it;</li>
 *   <li>{@code #tag} becomes a link that opens the notes with that tag.</li>
 * </ul>
 * The inserted HTML passes through CommonMark untouched.
 */
public final class ReadingSyntax {

    private ReadingSyntax() {
    }

    /** Prefix of a block anchor's element id ({@code ^abc} → {@code blockref-abc}). */
    public static final String BLOCK_ID_PREFIX = "blockref-";

    /** Protocol of a tag link; the WebView hands the tag to the app instead of navigating. */
    public static final String TAG_PROTOCOL = "jylos://tag/";

    private static final Pattern FOOTNOTE_DEFINITION = Pattern.compile("^ {0,3}\\[\\^([^\\]\\s]+)\\]:[ \\t]?(.*)$");
    private static final Pattern FOOTNOTE_REFERENCE = Pattern.compile("\\^\\[([^\\]\\n]+)\\]|\\[\\^([^\\]\\s]+)\\]");
    private static final Pattern BLOCK_ID = Pattern.compile("(?:^|[ \\t]+)\\^([A-Za-z0-9-]+)[ \\t]*$", Pattern.MULTILINE);
    /** Tag characters, as in Obsidian; only after a space or at a line start. */
    private static final Pattern TAG = Pattern.compile("(?<![^\\s])#([\\p{L}\\p{N}_/-]+)");
    /** Text a tag cannot be in: links of any kind, URLs, HTML tags. */
    private static final Pattern NOT_TAGGABLE = Pattern.compile(
            "!?\\[\\[[^\\]\\n]*\\]\\]|!?\\[[^\\[\\]]*\\]\\([^)\\n]*\\)|(?i:https?://|www\\.)\\S+|<[^>\\n]*>");

    /**
     * The "back to the text" arrow of a footnote, drawn rather than typed: the preview turns
     * arrow characters such as ↩ into emoji images.
     */
    private static final String BACK_ARROW = "<svg viewBox=\"0 0 24 24\" width=\"12\" height=\"12\" fill=\"none\" "
            + "stroke=\"currentColor\" stroke-width=\"2\" stroke-linecap=\"round\" stroke-linejoin=\"round\">"
            + "<polyline points=\"9 14 4 9 9 4\"/><path d=\"M20 20v-7a4 4 0 0 0-4-4H4\"/></svg>";

    /** The Markdown with footnote references replaced, and the HTML list to append after it. */
    public record Footnotes(String markdown, String html) {
    }

    /** Removes every {@code %%…%%} outside code. A {@code %%} without a partner stays as text. */
    public static String stripComments(String markdown) {
        if (markdown == null || !markdown.contains("%%")) {
            return markdown;
        }
        BitSet code = WikiLinkResolver.codeRanges(markdown);
        List<Integer> markers = new ArrayList<>();
        int from = 0;
        while ((from = markdown.indexOf("%%", from)) >= 0) {
            if (!code.get(from)) {
                markers.add(from);
            }
            from += 2;
        }
        if (markers.size() < 2) {
            return markdown;
        }
        StringBuilder out = new StringBuilder(markdown.length());
        int kept = 0;
        for (int i = 0; i + 1 < markers.size(); i += 2) {
            out.append(markdown, kept, markers.get(i));
            kept = markers.get(i + 1) + 2;
        }
        out.append(markdown.substring(kept));
        return out.toString();
    }

    /**
     * Numbers the footnotes of {@code markdown} by first use: references become superscript
     * links, definitions ({@code [^label]: text}, continued by indented lines) leave the
     * text, and {@link Footnotes#html()} lists the referenced ones (empty when there are none).
     */
    public static Footnotes footnotes(String markdown) {
        if (markdown == null || markdown.indexOf('^') < 0) {
            return new Footnotes(markdown, "");
        }
        // 1. Definitions out of the text.
        BitSet code = WikiLinkResolver.codeRanges(markdown);
        String[] lines = markdown.split("\n", -1);
        Map<String, String> definitions = new LinkedHashMap<>();
        List<String> kept = new ArrayList<>();
        int offset = 0;
        for (int i = 0; i < lines.length; i++) {
            Matcher def = FOOTNOTE_DEFINITION.matcher(lines[i]);
            if (!code.get(offset) && def.matches()) {
                StringBuilder text = new StringBuilder(def.group(2).strip());
                while (i + 1 < lines.length && !lines[i + 1].isBlank()
                        && (lines[i + 1].startsWith("    ") || lines[i + 1].startsWith("\t"))) {
                    offset += lines[i].length() + 1;
                    i++;
                    text.append(' ').append(lines[i].strip());
                }
                definitions.putIfAbsent(def.group(1), text.toString());
                offset += lines[i].length() + 1;
                continue;
            }
            kept.add(lines[i]);
            offset += lines[i].length() + 1;
        }
        String text = String.join("\n", kept);

        // 2. References numbered by first use.
        BitSet textCode = WikiLinkResolver.codeRanges(text);
        Map<String, Integer> numbers = new LinkedHashMap<>();
        List<String> contents = new ArrayList<>();
        Matcher ref = FOOTNOTE_REFERENCE.matcher(text);
        StringBuilder out = new StringBuilder(text.length());
        while (ref.find()) {
            if (textCode.get(ref.start())) {
                continue;
            }
            int number;
            boolean first;
            if (ref.group(1) != null) {
                contents.add(ref.group(1).strip());
                number = contents.size();
                first = true;
            } else if (definitions.containsKey(ref.group(2))) {
                Integer known = numbers.get(ref.group(2));
                first = known == null;
                if (first) {
                    contents.add(definitions.get(ref.group(2)));
                    known = contents.size();
                    numbers.put(ref.group(2), known);
                }
                number = known;
            } else {
                continue;
            }
            ref.appendReplacement(out, Matcher.quoteReplacement("<sup class=\"footnote-ref\""
                    + (first ? " id=\"fnref-" + number + "\"" : "") + "><a href=\"#fn-" + number + "\">" + number
                    + "</a></sup>"));
        }
        ref.appendTail(out);
        if (contents.isEmpty()) {
            return new Footnotes(out.toString(), "");
        }
        StringBuilder html = new StringBuilder("<section class=\"footnotes\">\n<hr>\n<ol>\n");
        for (int n = 1; n <= contents.size(); n++) {
            html.append("<li id=\"fn-").append(n).append("\">").append(inlineHtml(contents.get(n - 1)))
                    .append(" <a href=\"#fnref-").append(n).append("\" class=\"footnote-backref\">").append(BACK_ARROW).append("</a></li>\n");
        }
        html.append("</ol>\n</section>\n");
        return new Footnotes(out.toString(), html.toString());
    }

    /** Replaces {@code ^id} at the end of a line (outside code) with an invisible anchor. */
    public static String blockAnchors(String markdown) {
        if (markdown == null || markdown.indexOf('^') < 0) {
            return markdown;
        }
        BitSet code = WikiLinkResolver.codeRanges(markdown);
        Matcher m = BLOCK_ID.matcher(markdown);
        StringBuilder out = new StringBuilder(markdown.length());
        while (m.find()) {
            int caret = markdown.indexOf('^', m.start());
            if (code.get(caret)) {
                continue;
            }
            m.appendReplacement(out, Matcher.quoteReplacement(
                    "<span class=\"block-anchor\" id=\"" + BLOCK_ID_PREFIX + m.group(1) + "\"></span>"));
        }
        m.appendTail(out);
        return out.toString();
    }

    /** Turns each {@code #tag} outside code, links, URLs and HTML into a link to that tag. */
    public static String tags(String markdown) {
        if (markdown == null || markdown.indexOf('#') < 0) {
            return markdown;
        }
        BitSet excluded = notTaggable(markdown);
        Matcher m = TAG.matcher(markdown);
        StringBuilder out = new StringBuilder(markdown.length());
        while (m.find()) {
            if (excluded.get(m.start())) {
                continue;
            }
            String name = m.group(1);
            m.appendReplacement(out, Matcher.quoteReplacement(
                    "<a class=\"tag\" href=\"" + TAG_PROTOCOL + name + "\">#" + name + "</a>"));
        }
        m.appendTail(out);
        return out.toString();
    }

    /**
     * The {@code #tags} written in a note's text, in order, without the {@code #}: the ones
     * {@link #tags} turns into links, so the vault indexes what the note shows as tags.
     */
    public static List<String> inlineTags(String markdown) {
        List<String> found = new ArrayList<>();
        if (markdown == null || markdown.indexOf('#') < 0) {
            return found;
        }
        BitSet excluded = notTaggable(markdown);
        Matcher m = TAG.matcher(markdown);
        while (m.find()) {
            if (!excluded.get(m.start())) {
                found.add(m.group(1));
            }
        }
        return found;
    }

    /** Code, links, URLs and HTML tags: where a {@code #} does not start a tag. */
    private static BitSet notTaggable(String markdown) {
        BitSet excluded = WikiLinkResolver.codeRanges(markdown);
        Matcher skip = NOT_TAGGABLE.matcher(markdown);
        while (skip.find()) {
            excluded.set(skip.start(), skip.end());
        }
        return excluded;
    }

    /** Comments, block anchors and tags: what an embedded note also needs. */
    public static String inline(String markdown) {
        return tags(blockAnchors(stripComments(markdown)));
    }

    /** One footnote's text as inline HTML (its paragraph tags dropped). */
    private static String inlineHtml(String markdown) {
        String html = MarkdownProcessor.markdownToHtml(markdown).strip();
        if (html.startsWith("<p>") && html.endsWith("</p>") && html.indexOf("<p>", 3) < 0) {
            html = html.substring(3, html.length() - 4);
        }
        return html;
    }
}
