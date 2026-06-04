package com.example.jylos.util;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Resolves Obsidian-style internal links in Markdown before HTML rendering.
 *
 * <h3>Supported link formats</h3>
 * <table>
 *   <tr><th>Syntax</th><th>Description</th></tr>
 *   <tr><td>{@code [[Note Title]]}</td><td>basic wiki-link</td></tr>
 *   <tr><td>{@code [[Note Title.md]]}</td><td>.md extension stripped automatically</td></tr>
 *   <tr><td>{@code [[folder/Note Title]]}</td><td>path prefix ignored for resolution</td></tr>
 *   <tr><td>{@code [[Note Title#Heading]]}</td><td>heading anchor added to href</td></tr>
 *   <tr><td>{@code [[Note Title|Display Text]]}</td><td>custom display label</td></tr>
 *   <tr><td>{@code [[folder/Note#Heading|Alias]]}</td><td>full combined form</td></tr>
 *   <tr><td>{@code [label](Note Title.md)}</td><td>Markdown internal link (no protocol)</td></tr>
 *   <tr><td>{@code [label](Note%20Title)}</td><td>URL-encoded Markdown link</td></tr>
 * </table>
 *
 * <h3>Resolution rules</h3>
 * <ul>
 *   <li>Links to existing notes render as {@code <a class="wikilink">}.</li>
 *   <li>Links to non-existent notes add the {@code wikilink-new} class so the
 *       user can see they're broken.</li>
 *   <li>Heading anchors are appended after the note title in the href so the
 *       preview can scroll to the right section.</li>
 * </ul>
 *
 * @author Edu Díaz (RGiskard7)
 * @since 1.3.0
 */
public final class WikiLinkResolver {

    /** Custom protocol intercepted by the WebView location listener. */
    public static final String PROTOCOL = "jylos://open-note/";

    /**
     * Full Obsidian wiki-link pattern: {@code [[path#heading|alias]]}
     * <ol>
     *   <li>path — required, may contain {@code /} and end with {@code .md}</li>
     *   <li>heading — optional, everything after {@code #} until {@code |} or {@code ]]}</li>
     *   <li>alias — optional, everything after {@code |} until {@code ]]}</li>
     * </ol>
     */
    private static final Pattern WIKI_LINK = Pattern.compile(
            "\\[\\[([^\\[\\]|#\\n]+?)(?:#([^\\[\\]|\\n]+?))?(?:\\|([^\\[\\]\\n]+?))?\\]\\]");

    /**
     * Markdown-style internal link: {@code [label](url)} where {@code url} has
     * no recognised external protocol.
     *
     * <p>Negative lookahead excludes http, https, ftp, file, mailto, and any
     * URL that contains {@code ://}.</p>
     */
    private static final Pattern MARKDOWN_INTERNAL = Pattern.compile(
            "\\[([^\\[\\]]+)\\]\\((?!(?:https?|ftp|file|mailto):)([^()\\n]+?)\\)");

    private WikiLinkResolver() {
    }

    // ------------------------------------------------------------------
    // Public API
    // ------------------------------------------------------------------

    /**
     * Resolves all internal links ({@code [[wiki]]} and {@code [label](path)})
     * in {@code markdown} and returns the modified Markdown with raw HTML anchors.
     *
     * @param markdown    raw Markdown content (may be null)
     * @param knownTitles set of existing note titles for styling
     * @return Markdown with internal links replaced by HTML anchors
     */
    public static String resolve(String markdown, Set<String> knownTitles) {
        if (markdown == null || markdown.isEmpty()) {
            return markdown != null ? markdown : "";
        }

        // 1. Resolve [[wiki-links]] first
        String result = resolveWikiLinks(markdown, knownTitles);

        // 2. Then resolve Markdown internal links [label](path)
        result = resolveMarkdownInternalLinks(result, knownTitles);

        return result;
    }

    /**
     * Extracts the bare titles of every internal link found in {@code markdown},
     * covering both {@code [[wiki-links]]} and {@code [label](internal-path)}.
     *
     * <p>Used by the graph view to derive note→note edges with exactly the same
     * link semantics the preview uses, so the graph is 100% consistent with the
     * rendered wiki-links.</p>
     *
     * <p>The returned titles are normalised via {@link #extractTitle(String)}
     * (extension stripped, last path segment, heading/alias discarded) and
     * de-duplicated while preserving first-seen order. External links (http,
     * https, ftp, file, mailto) are ignored.</p>
     *
     * @param markdown raw Markdown content (may be null)
     * @return ordered, de-duplicated list of linked note titles (never null)
     */
    public static List<String> extractLinkTargets(String markdown) {
        if (markdown == null || markdown.isEmpty()) {
            return new ArrayList<>();
        }
        Set<String> targets = new LinkedHashSet<>();

        Matcher wiki = WIKI_LINK.matcher(markdown);
        while (wiki.find()) {
            String title = internalNoteTarget(wiki.group(1).trim());
            if (title != null) {
                targets.add(title);
            }
        }

        Matcher md = MARKDOWN_INTERNAL.matcher(markdown);
        while (md.find()) {
            String url = md.group(2).trim();
            if (url.startsWith(PROTOCOL)) {
                continue;
            }
            String title = internalNoteTarget(url);
            if (title != null) {
                targets.add(title);
            }
        }

        return new ArrayList<>(targets);
    }

    /**
     * Returns the bare note title for an internal reference, or {@code null} if
     * the string is not a note link (graph + preview use this to ignore noise).
     *
     * <p>Rejects external URLs ({@code http://}, {@code www.}), in-document
     * anchors ({@code #section}), {@code mailto:}/{@code tel:}, attachment paths
     * ({@code .png}, {@code .pdf}, …), and hashtag-only fragments. Accepts
     * {@code [[wiki]]} paths and {@code [label](Note.md)} / {@code [label](folder/Note)}
     * style targets.</p>
     */
    public static String internalNoteTarget(String rawReference) {
        if (rawReference == null) {
            return null;
        }
        String ref = rawReference.trim();
        if (ref.isEmpty()) {
            return null;
        }
        if (ref.isEmpty()
                || ref.startsWith("#")
                || ref.startsWith("mailto:")
                || ref.startsWith("tel:")
                || ref.startsWith(PROTOCOL)
                || ref.contains("://")) {
            return null;
        }
        int hash = ref.indexOf('#');
        if (hash >= 0) {
            ref = ref.substring(0, hash);
        }
        if (ref.isEmpty()) {
            return null;
        }
        String decoded = urlDecode(ref);
        if (decoded.matches("(?i)^(https?://|www\\.).*")) {
            return null;
        }
        String last = decoded;
        int slash = Math.max(last.lastIndexOf('/'), last.lastIndexOf('\\'));
        if (slash >= 0) {
            last = last.substring(slash + 1);
        }
        int dot = last.lastIndexOf('.');
        if (dot > 0 && !last.substring(dot + 1).equalsIgnoreCase("md")) {
            return null;
        }
        String title = extractTitle(decoded);
        return title.isBlank() ? null : title;
    }

    /** @see #internalNoteTarget(String) */
    static String markdownNoteTarget(String rawUrl) {
        return internalNoteTarget(rawUrl);
    }

    // ------------------------------------------------------------------
    // Wiki-link resolution
    // ------------------------------------------------------------------

    private static String resolveWikiLinks(String markdown, Set<String> knownTitles) {
        Matcher m = WIKI_LINK.matcher(markdown);
        StringBuilder sb = new StringBuilder();

        while (m.find()) {
            String rawPath  = m.group(1).trim();
            String heading  = m.group(2) != null ? m.group(2).trim() : null;
            String alias    = m.group(3) != null ? m.group(3).trim() : null;

            String title = internalNoteTarget(rawPath);
            if (title == null) {
                m.appendReplacement(sb, Matcher.quoteReplacement(m.group(0)));
                continue;
            }

            // Determine display text
            String label;
            if (alias != null && !alias.isEmpty()) {
                label = alias;
            } else if (heading != null && !heading.isEmpty()) {
                label = title + " › " + heading;
            } else {
                label = title;
            }

            boolean exists = noteExists(title, knownTitles);
            String cssClass = exists ? "wikilink" : "wikilink wikilink-new";

            // href carries note title (+ optional heading after |)
            String href = PROTOCOL + encodeTitle(title)
                    + (heading != null && !heading.isEmpty() ? "|" + encodeTitle(heading) : "");

            String replacement = buildAnchor(cssClass, href, title, label);
            m.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    // ------------------------------------------------------------------
    // Markdown internal-link resolution
    // ------------------------------------------------------------------

    private static String resolveMarkdownInternalLinks(String markdown, Set<String> knownTitles) {
        Matcher m = MARKDOWN_INTERNAL.matcher(markdown);
        StringBuilder sb = new StringBuilder();

        while (m.find()) {
            String label = m.group(1);
            String url   = m.group(2).trim();

            // Skip if URL already points to our protocol (already resolved as wiki-link)
            if (url.startsWith(PROTOCOL)) {
                m.appendReplacement(sb, Matcher.quoteReplacement(m.group(0)));
                continue;
            }

            // Only internal note references qualify (rejects anchors, URLs, images…).
            String title = markdownNoteTarget(url);
            if (title == null || !noteExists(title, knownTitles)) {
                // Not an internal link to a known note — leave the Markdown unchanged.
                m.appendReplacement(sb, Matcher.quoteReplacement(m.group(0)));
                continue;
            }

            String href = PROTOCOL + encodeTitle(title);
            String replacement = buildAnchor("wikilink", href, title, label);
            m.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    /**
     * Extracts the bare note title from a path-like string:
     * strips the {@code .md} extension and returns only the last path segment.
     *
     * <p>This method is {@code public} so callers and tests can use it as a
     * standalone normalisation utility.</p>
     *
     * <p>Examples:
     * <ul>
     *   <li>{@code "Three laws of motion.md"} → {@code "Three laws of motion"}</li>
     *   <li>{@code "folder/sub/Note"} → {@code "Note"}</li>
     *   <li>{@code "Daily Notes/2026-05-31.md"} → {@code "2026-05-31"}</li>
     * </ul>
     */
    public static String extractTitle(String path) {
        if (path == null) return "";
        String t = path.trim();
        // Strip .md extension (case-insensitive)
        if (t.toLowerCase().endsWith(".md")) {
            t = t.substring(0, t.length() - 3);
        }
        // Use last path segment (supports both / and \)
        int slash = Math.max(t.lastIndexOf('/'), t.lastIndexOf('\\'));
        if (slash >= 0 && slash < t.length() - 1) {
            t = t.substring(slash + 1);
        }
        return t.trim();
    }

    private static boolean noteExists(String title, Set<String> knownTitles) {
        if (knownTitles == null || title.isBlank()) return false;
        return knownTitles.stream().anyMatch(t -> t.equalsIgnoreCase(title));
    }

    private static String buildAnchor(String cssClass, String href, String dataTarget, String label) {
        return "<a class=\"" + cssClass
                + "\" href=\"" + href
                + "\" data-target=\"" + escapeHtml(dataTarget)
                + "\">" + escapeHtml(label) + "</a>";
    }

    private static String encodeTitle(String title) {
        return title.replace(" ",  "%20")
                    .replace("#",  "%23")
                    .replace("&",  "%26")
                    .replace("\"", "%22")
                    .replace("'",  "%27");
    }

    private static String urlDecode(String url) {
        try {
            return URLDecoder.decode(url, StandardCharsets.UTF_8);
        } catch (Exception e) {
            return url;
        }
    }

    private static String escapeHtml(String text) {
        return text.replace("&",  "&amp;")
                   .replace("<",  "&lt;")
                   .replace(">",  "&gt;")
                   .replace("\"", "&quot;");
    }
}
