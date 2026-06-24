package com.example.jylos.util;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Note transclusion (embeds): expands {@code ![[Note]]} and {@code ![[Note#Heading]]}
 * in Markdown into the rendered content of the target note (or one section), inline,
 * Obsidian-style.
 *
 * <h3>How it integrates with the preview pipeline</h3>
 * Embeds must be expanded <b>before</b> {@link WikiLinkResolver} because {@code ![[…]]}
 * contains a {@code [[…]]} that the wiki-link resolver would otherwise rewrite. Each
 * embed is rendered to HTML up front and parked behind a private-use placeholder token
 * left in the Markdown stream ({@link #protect}); after CommonMark has run, the caller
 * swaps the tokens back for the HTML ({@link #restore}). This avoids the pitfalls of
 * inlining HTML blocks into Markdown (CommonMark would not re-parse their content, and
 * blank lines would split the block).
 *
 * <h3>Safety</h3>
 * Recursion is bounded by {@link #MAX_DEPTH} and a per-branch visited-set, so an embed
 * cycle ({@code A → B → A}) or a deep chain degrades to a small notice instead of
 * looping. Missing notes and missing sections render a muted placeholder.
 *
 * @author Edu Díaz (RGiskard7)
 * @since 2.2.0
 */
public final class Transclusion {

    private Transclusion() {
    }

    /** {@code ![[target]]} where target is {@code Note}, {@code Note#Heading} (alias after | ignored). */
    private static final Pattern EMBED = Pattern.compile("!\\[\\[([^\\[\\]\\n]+?)\\]\\]");
    private static final Pattern HEADING = Pattern.compile("^(#{1,6})\\s+(.*?)\\s*$");

    /** Placeholder delimiters: private-use code points that never occur in note text. */
    private static final char TOK_START = '';
    private static final char TOK_END = '';
    /** Matches a token, optionally wrapped in a lone {@code <p>…</p>} from CommonMark. */
    private static final Pattern TOKEN = Pattern.compile("(?:<p>)?(\\d+)(?:</p>)?");

    private static final int MAX_DEPTH = 3;

    /** The Markdown with embeds replaced by tokens, plus the token → HTML map for {@link #restore}. */
    public record Result(String markdown, Map<String, String> embeds) {
    }

    /**
     * Replaces every {@code ![[…]]} in {@code markdown} with a placeholder token and
     * builds the rendered HTML for each, ready to be re-injected after CommonMark.
     *
     * @param markdown       host Markdown (may be null)
     * @param contentByTitle resolves a note title to its raw Markdown (or null if absent)
     * @param knownTitles    titles used to resolve {@code [[wiki-links]]} inside embeds
     */
    public static Result protect(String markdown, Function<String, String> contentByTitle, Set<String> knownTitles) {
        Map<String, String> embeds = new HashMap<>();
        if (markdown == null || !markdown.contains("![[")) {
            return new Result(markdown == null ? "" : markdown, embeds);
        }
        int[] counter = {0};
        String out = expand(markdown, contentByTitle, knownTitles, 0, new HashSet<>(), embeds, counter);
        return new Result(out, embeds);
    }

    /** Swaps the placeholder tokens (optionally wrapped in a lone {@code <p>}) back for embed HTML. */
    public static String restore(String html, Map<String, String> embeds) {
        if (html == null || embeds.isEmpty() || html.indexOf(TOK_START) < 0) {
            return html;
        }
        Matcher m = TOKEN.matcher(html);
        StringBuilder sb = new StringBuilder(html.length() + 256);
        while (m.find()) {
            String token = TOK_START + m.group(1) + TOK_END;
            String embed = embeds.getOrDefault(token, "");
            m.appendReplacement(sb, Matcher.quoteReplacement(embed));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    private static String expand(String md, Function<String, String> resolver, Set<String> titles,
            int depth, Set<String> visited, Map<String, String> embeds, int[] counter) {
        if (md == null || !md.contains("![[")) {
            return md == null ? "" : md;
        }
        Matcher m = EMBED.matcher(md);
        StringBuilder sb = new StringBuilder(md.length() + 64);
        while (m.find()) {
            String html = renderEmbed(m.group(1).trim(), resolver, titles, depth, visited, embeds, counter);
            String token = "" + TOK_START + (counter[0]++) + TOK_END;
            embeds.put(token, html);
            m.appendReplacement(sb, Matcher.quoteReplacement(token));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    private static String renderEmbed(String ref, Function<String, String> resolver, Set<String> titles,
            int depth, Set<String> visited, Map<String, String> embeds, int[] counter) {
        // Split "Title#Heading" (alias after | is ignored for embeds).
        String titlePart = ref;
        String heading = null;
        int hash = ref.indexOf('#');
        if (hash >= 0) {
            titlePart = ref.substring(0, hash);
            heading = ref.substring(hash + 1).trim();
        }
        int pipe = titlePart.indexOf('|');
        if (pipe >= 0) {
            titlePart = titlePart.substring(0, pipe);
        }
        String title = WikiLinkResolver.extractTitle(titlePart.trim());
        String display = heading != null && !heading.isEmpty() ? title + " › " + heading : title;

        if (depth >= MAX_DEPTH) {
            return notice("embed-too-deep", display, "embeds nested too deep");
        }
        if (visited.contains(title.toLowerCase())) {
            return notice("embed-cycle", display, "circular embed");
        }
        String content = resolver.apply(titlePart.trim());
        if (content == null) {
            return notice("embed-missing", display, "note not found");
        }
        String section = content;
        if (heading != null && !heading.isEmpty()) {
            section = extractSection(content, heading);
            if (section == null) {
                return notice("embed-missing", display, "section not found");
            }
        }

        Set<String> nextVisited = new HashSet<>(visited);
        nextVisited.add(title.toLowerCase());
        String inner = expand(section, resolver, titles, depth + 1, nextVisited, embeds, counter);
        inner = WikiLinkResolver.resolve(inner, titles);
        String innerHtml = MarkdownProcessor.markdownToHtml(inner);
        innerHtml = restore(innerHtml, embeds);

        return "<div class=\"embed\">"
                + "<a class=\"embed-title\" href=\"" + WikiLinkResolver.PROTOCOL + urlEncode(title) + "\">"
                + escape(display) + "</a>"
                + "<div class=\"embed-body\">" + innerHtml + "</div>"
                + "</div>";
    }

    /**
     * Returns the Markdown of the section under {@code heading} (the heading line plus
     * everything until the next heading of the same or higher level), or {@code null}
     * if no such heading exists.
     */
    static String extractSection(String content, String heading) {
        String[] lines = content.split("\n", -1);
        int start = -1;
        int level = 0;
        for (int i = 0; i < lines.length; i++) {
            Matcher hm = HEADING.matcher(lines[i]);
            if (hm.matches() && hm.group(2).trim().equalsIgnoreCase(heading.trim())) {
                start = i;
                level = hm.group(1).length();
                break;
            }
        }
        if (start < 0) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        sb.append(lines[start]).append('\n');
        for (int j = start + 1; j < lines.length; j++) {
            Matcher hm = HEADING.matcher(lines[j]);
            if (hm.matches() && hm.group(1).length() <= level) {
                break;
            }
            sb.append(lines[j]).append('\n');
        }
        return sb.toString();
    }

    private static String notice(String cssClass, String display, String reason) {
        return "<div class=\"embed " + cssClass + "\">"
                + "<span class=\"embed-title\">" + escape(display) + "</span>"
                + "<span class=\"embed-note\">" + escape(reason) + "</span></div>";
    }

    private static String urlEncode(String value) {
        return java.net.URLEncoder.encode(value, java.nio.charset.StandardCharsets.UTF_8);
    }

    private static String escape(String text) {
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
