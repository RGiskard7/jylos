package com.example.jylos.plugin.builtin.dataview;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** HTML escaping/unescaping shared by the renderer and the block extractor. */
final class Html {

    private static final Pattern WIKI_LINK = Pattern.compile("\\[\\[([^\\[\\]\\n]+?)\\]\\]");
    private static final Pattern BOLD = Pattern.compile("\\*\\*(.+?)\\*\\*");
    private static final Pattern ITALIC = Pattern.compile("(?<![*\\w])\\*([^*\\n]+?)\\*(?!\\*)");
    private static final Pattern CODE = Pattern.compile("`([^`\\n]+?)`");
    private static final Pattern STRIKE = Pattern.compile("~~(.+?)~~");

    private Html() {
    }

    /**
     * Renders a fragment of raw Markdown that never went through the main CommonMark
     * pass — task text and list items pulled straight from note source.
     *
     * <p>Escaping happens first and the inline patterns are applied to the escaped text,
     * so note content can never inject markup: the only tags in the result are the ones
     * produced here.</p>
     */
    static String inline(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        String out = escape(text);
        out = WIKI_LINK.matcher(out).replaceAll(match -> {
            String inner = match.group(1);
            String[] parts = inner.split("\\|", 2);
            String target = parts[0].split("#", 2)[0].trim();
            String display = parts.length > 1 ? parts[1].trim() : target;
            return Matcher.quoteReplacement(new Link(target, display).toHtml());
        });
        out = CODE.matcher(out).replaceAll("<code>$1</code>");
        out = BOLD.matcher(out).replaceAll("<strong>$1</strong>");
        out = ITALIC.matcher(out).replaceAll("<em>$1</em>");
        out = STRIKE.matcher(out).replaceAll("<del>$1</del>");
        return out;
    }

    /** Escapes text for safe insertion into element content or a double-quoted attribute. */
    static String escape(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        StringBuilder out = new StringBuilder(value.length() + 16);
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '&' -> out.append("&amp;");
                case '<' -> out.append("&lt;");
                case '>' -> out.append("&gt;");
                case '"' -> out.append("&quot;");
                case '\'' -> out.append("&#39;");
                default -> out.append(c);
            }
        }
        return out.toString();
    }

    /**
     * Reverses the escaping CommonMark applied to fenced code-block content.
     *
     * <p>A query is authored as plain text but reaches the enhancer already rendered
     * into {@code <pre><code>}, where {@code WHERE x > 3} has become
     * {@code WHERE x &gt; 3}. Parsing without undoing that would reject every
     * comparison operator in every query.</p>
     */
    static String unescape(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        return value.replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace("&#39;", "'")
                .replace("&apos;", "'")
                .replace("&nbsp;", " ")
                // Ampersand last: undoing it first would let "&amp;lt;" collapse into "<".
                .replace("&amp;", "&");
    }
}
