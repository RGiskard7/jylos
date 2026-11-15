package com.example.jylos.util;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.BitSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Note transclusion (embeds): expands {@code ![[Note]]} and {@code ![[Note#Heading]]}
 * in Markdown into the rendered content of the target note (or one section), inline,
 * Obsidian-style.
 *
 * <h2>How it integrates with the preview pipeline</h2>
 * Embeds must be expanded <b>before</b> {@link WikiLinkResolver} because {@code ![[…]]}
 * contains a {@code [[…]]} that the wiki-link resolver would otherwise rewrite. Each
 * embed is rendered to HTML up front and parked behind a private-use placeholder token
 * left in the Markdown stream ({@link #protect}); after CommonMark has run, the caller
 * swaps the tokens back for the HTML ({@link #restore}). This avoids the pitfalls of
 * inlining HTML blocks into Markdown (CommonMark would not re-parse their content, and
 * blank lines would split the block).
 *
 * <h2>Safety</h2>
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

    /** Placeholder delimiters: private-use code points that never occur in note text. */
    private static final char TOK_START = '';
    private static final char TOK_END = '';
    /** Matches a token, optionally wrapped in a lone {@code <p>…</p>} from CommonMark. */
    private static final Pattern TOKEN = Pattern.compile("(?:<p>)?(\\d+)(?:</p>)?");

    private static final int MAX_DEPTH = 3;

    /**
     * The Markdown with embeds replaced by tokens, plus the token to HTML map for
     * {@link #restore}.
     *
     * @param markdown host Markdown containing placeholder tokens
     * @param embeds token to rendered HTML map
     */
    public record Result(String markdown, Map<String, String> embeds) {
    }

    /**
     * Replaces every {@code ![[…]]} in {@code markdown} with a placeholder token and
     * builds the rendered HTML for each, ready to be re-injected after CommonMark.
     *
     * @param markdown       host Markdown (may be null)
     * @param contentByTitle resolves a note title to its raw Markdown (or null if absent)
     * @param knownTitles    titles used to resolve {@code [[wiki-links]]} inside embeds
     * @deprecated tells links apart by title only; use {@link #protect(String, Function, Predicate)}
     *             with the vault's link index.
     */
    @Deprecated
    public static Result protect(String markdown, Function<String, String> contentByTitle, Set<String> knownTitles) {
        return protect(markdown, contentByTitle,
                link -> knownTitles != null && knownTitles.stream()
                        .anyMatch(t -> t.equalsIgnoreCase(WikiLinkResolver.extractTitle(link))));
    }

    /**
     * Same as {@link #protect(String, Function, Set)}, with {@code exists} telling whether a
     * {@code [[wiki-link]]} inside an embed points to a note. Embeds inside code are text.
     *
     * @param markdown       host Markdown (may be null)
     * @param contentByTitle resolves a link (as written, e.g. {@code Carpeta/Nota}) to the
     *                       note's raw Markdown (or null if absent)
     * @param exists         whether a link inside an embed points to an existing note
     */
    public static Result protect(String markdown, Function<String, String> contentByTitle, Predicate<String> exists) {
        Map<String, String> embeds = new HashMap<>();
        if (markdown == null || !markdown.contains("![[")) {
            return new Result(markdown == null ? "" : markdown, embeds);
        }
        int[] counter = {0};
        String out = expand(markdown, contentByTitle, exists, 0, new HashSet<>(), embeds, counter);
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

    private static String expand(String md, Function<String, String> resolver, Predicate<String> titles,
            int depth, Set<String> visited, Map<String, String> embeds, int[] counter) {
        if (md == null || !md.contains("![[")) {
            return md == null ? "" : md;
        }
        BitSet code = WikiLinkResolver.codeRanges(md);
        Matcher m = EMBED.matcher(md);
        StringBuilder sb = new StringBuilder(md.length() + 64);
        while (m.find()) {
            if (code.get(m.start())) {
                m.appendReplacement(sb, Matcher.quoteReplacement(m.group(0)));
                continue;
            }
            String html = renderEmbed(m.group(1).trim(), resolver, titles, depth, visited, embeds, counter);
            String token = "" + TOK_START + (counter[0]++) + TOK_END;
            embeds.put(token, html);
            m.appendReplacement(sb, Matcher.quoteReplacement(token));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    private static String renderEmbed(String ref, Function<String, String> resolver, Predicate<String> titles,
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
        String link = titlePart.trim();
        String title = WikiLinkResolver.extractTitle(link);
        String display = heading != null && !heading.isEmpty() ? title + " › " + heading : title;

        if (depth >= MAX_DEPTH) {
            return notice("embed-too-deep", display, "embeds nested too deep");
        }
        if (visited.contains(link.toLowerCase())) {
            return notice("embed-cycle", display, "circular embed");
        }
        String content = resolver.apply(link);
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
        nextVisited.add(link.toLowerCase());
        // Comments, block anchors and tags inside the embedded note, as in the note itself.
        String inner = expand(ReadingSyntax.inline(section), resolver, titles, depth + 1, nextVisited, embeds, counter);
        inner = WikiLinkResolver.resolve(inner, titles);
        String innerHtml = MarkdownProcessor.markdownToHtml(inner);
        innerHtml = restore(innerHtml, embeds);

        return "<div class=\"embed\">"
                + "<a class=\"embed-title\" href=\"" + WikiLinkResolver.PROTOCOL + urlEncode(link)
                + "\" data-target=\"" + MarkdownProcessor.escapeHtml(link)
                + (heading != null && !heading.isEmpty() ? "\" data-subpath=\"" + MarkdownProcessor.escapeHtml(heading) : "") + "\">"
                + MarkdownProcessor.escapeHtml(display) + "</a>"
                + "<div class=\"embed-body\">" + innerHtml + "</div>"
                + "</div>";
    }

    /**
     * Returns the Markdown of the section under {@code heading} (the heading line plus
     * everything until the next heading of the same or higher level), or {@code null}
     * if no such heading exists.
     */
    static String extractSection(String content, String heading) {
        if (heading != null && heading.strip().startsWith("^")) {
            return extractBlock(content, heading.strip().substring(1));
        }
        // The headings a link jumps to (NoteAnchors), so an embed and a link agree.
        List<NoteAnchors.Heading> headings = NoteAnchors.headings(content);
        for (int i = 0; i < headings.size(); i++) {
            NoteAnchors.Heading found = headings.get(i);
            if (!found.title().equalsIgnoreCase(heading.strip())) {
                continue;
            }
            int end = content.length();
            for (NoteAnchors.Heading next : headings.subList(i + 1, headings.size())) {
                if (next.level() <= found.level()) {
                    end = next.offset();
                    break;
                }
            }
            String section = content.substring(found.offset(), end);
            return section.endsWith("\n") ? section : section + "\n";
        }
        return null;
    }

    private static final Pattern LIST_ITEM = Pattern.compile("^\\s*([-*+]|\\d+[.)])\\s.*");

    /**
     * The block marked {@code ^id}, without the marker: the paragraph whose line ends with it,
     * just the list item when that line is one, or the block above when the id stands on its
     * own line (how tables and quotes are marked). {@code null} when no block has that id.
     */
    static String extractBlock(String content, String id) {
        if (id.isEmpty()) {
            return null;
        }
        // The block a link jumps to (NoteAnchors): a marker inside code marks nothing.
        int at = NoteAnchors.blockLine(content, id);
        if (at < 0) {
            return null;
        }
        String[] lines = content.split("\n", -1);
        Pattern marker = Pattern.compile("(?:^|[ \\t]+)\\^" + Pattern.quote(id) + "[ \\t]*$");
        int i = (int) content.substring(0, at).chars().filter(c -> c == '\n').count();
        Matcher m = marker.matcher(lines[i]);
        if (m.find()) {
            String rest = lines[i].substring(0, m.start());
            int from;
            int to;
            if (rest.isBlank()) {
                to = i - 1;
                while (to >= 0 && lines[to].isBlank()) {
                    to--;
                }
                if (to < 0) {
                    return null;
                }
                from = to;
                while (from - 1 >= 0 && !lines[from - 1].isBlank()) {
                    from--;
                }
            } else if (LIST_ITEM.matcher(lines[i]).matches()) {
                from = i;
                to = i;
            } else {
                from = i;
                while (from - 1 >= 0 && !lines[from - 1].isBlank()) {
                    from--;
                }
                to = i;
            }
            StringBuilder sb = new StringBuilder();
            for (int j = from; j <= to; j++) {
                sb.append(j == i ? rest : lines[j]).append('\n');
            }
            return sb.toString();
        }
        return null;
    }

    private static String notice(String cssClass, String display, String reason) {
        return "<div class=\"embed " + cssClass + "\">"
                + "<span class=\"embed-title\">" + MarkdownProcessor.escapeHtml(display) + "</span>"
                + "<span class=\"embed-note\">" + MarkdownProcessor.escapeHtml(reason) + "</span></div>";
    }

    private static String urlEncode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

}
