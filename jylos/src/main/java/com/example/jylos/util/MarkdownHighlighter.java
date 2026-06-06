package com.example.jylos.util;

import java.util.Collection;
import java.util.Collections;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.fxmisc.richtext.model.StyleSpans;
import org.fxmisc.richtext.model.StyleSpansBuilder;

/**
 * Computes syntax-highlighting style spans for Markdown shown in the editor
 * {@link org.fxmisc.richtext.CodeArea}.
 *
 * <p>The highlighting is intentionally <em>flat</em> (non-overlapping), which is the
 * common approach for live editor highlighters and keeps the single-pass regex fast.
 * Alternative ordering matters: longer/greedier constructs are listed before the
 * shorter ones they could otherwise shadow (fenced code before inline code, bold
 * before italic, {@code ~~strike~~} before italic {@code _}).</p>
 *
 * <p>Each matched range is tagged with one CSS style class (see {@code .md-*} rules in
 * the theme stylesheets) and applied via {@code CodeArea.setStyleSpans}.</p>
 *
 * @author Edu Díaz (RGiskard7)
 * @since 2.0.0
 */
public final class MarkdownHighlighter {

    private MarkdownHighlighter() {
        // utility class
    }

    /** Single combined pattern; group order encodes precedence (see class doc). */
    private static final Pattern PATTERN = Pattern.compile(
            "(?<CODEBLOCK>```[\\s\\S]*?```)"
            + "|(?<HEADING>(?m)^[ \\t]{0,3}#{1,6}[ \\t].*$)"
            + "|(?<QUOTE>(?m)^[ \\t]{0,3}>.*$)"
            + "|(?<LIST>(?m)^[ \\t]*(?:[-*+]|\\d+\\.)[ \\t]+)"
            + "|(?<WIKILINK>\\[\\[[^\\]\\n]+\\]\\])"
            + "|(?<MDLINK>\\[[^\\]\\n]+\\]\\([^)\\n]+\\))"
            + "|(?<BOLD>\\*\\*[^*\\n]+\\*\\*|__[^_\\n]+__)"
            + "|(?<STRIKE>~~[^~\\n]+~~)"
            + "|(?<ITALIC>\\*[^*\\n]+\\*|_[^_\\n]+_)"
            + "|(?<CODE>`[^`\\n]+`)"
    );

    /**
     * Builds the style spans for {@code text}. Always returns spans whose total length
     * equals {@code text.length()} (required by {@code setStyleSpans}).
     */
    public static StyleSpans<Collection<String>> computeHighlighting(String text) {
        String src = text != null ? text : "";
        Matcher matcher = PATTERN.matcher(src);
        StyleSpansBuilder<Collection<String>> spans = new StyleSpansBuilder<>();
        int lastEnd = 0;
        while (matcher.find()) {
            String styleClass = styleClassOf(matcher);
            if (styleClass == null) {
                continue;
            }
            spans.add(Collections.emptyList(), matcher.start() - lastEnd);
            spans.add(Collections.singleton(styleClass), matcher.end() - matcher.start());
            lastEnd = matcher.end();
        }
        spans.add(Collections.emptyList(), src.length() - lastEnd);
        return spans.create();
    }

    private static String styleClassOf(Matcher m) {
        if (m.group("CODEBLOCK") != null) return "md-codeblock";
        if (m.group("HEADING") != null)   return "md-heading";
        if (m.group("QUOTE") != null)      return "md-quote";
        if (m.group("LIST") != null)       return "md-list";
        if (m.group("WIKILINK") != null)   return "md-wikilink";
        if (m.group("MDLINK") != null)     return "md-link";
        if (m.group("BOLD") != null)       return "md-bold";
        if (m.group("STRIKE") != null)     return "md-strike";
        if (m.group("ITALIC") != null)     return "md-italic";
        if (m.group("CODE") != null)       return "md-code";
        return null;
    }
}
