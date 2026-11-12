package com.example.jylos.util;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Resolves Obsidian-style internal links in Markdown before HTML rendering.
 *
 * <h2>Supported link formats</h2>
 * <table>
 *   <caption>Internal link syntaxes supported by the preview renderer</caption>
 *   <tr><th>Syntax</th><th>Description</th></tr>
 *   <tr><td>{@code [[Note Title]]}</td><td>basic wiki-link</td></tr>
 *   <tr><td>{@code [[Note Title.md]]}</td><td>.md extension stripped automatically</td></tr>
 *   <tr><td>{@code [[folder/Note Title]]}</td><td>path used to tell same-named notes apart</td></tr>
 *   <tr><td>{@code [[Note Title#Heading]]}</td><td>heading anchor added to href</td></tr>
 *   <tr><td>{@code [[Note Title|Display Text]]}</td><td>custom display label</td></tr>
 *   <tr><td>{@code [[folder/Note#Heading|Alias]]}</td><td>full combined form</td></tr>
 *   <tr><td>{@code [label](Note Title.md)}</td><td>Markdown internal link (no protocol)</td></tr>
 *   <tr><td>{@code [label](Note%20Title)}</td><td>URL-encoded Markdown link</td></tr>
 * </table>
 *
 * <h2>Resolution rules</h2>
 * <ul>
 *   <li>A link is kept as written (folder path included, heading and display text
 *       dropped) and handed to an existence test, which resolves it the way Obsidian does
 *       (see {@code service.LinkResolver}).</li>
 *   <li>Links inside code (fenced blocks and inline code spans) are text, not links:
 *       they are neither rendered nor extracted.</li>
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
    /** A link within the same note: {@code [[#Heading]]}, {@code [[#^block|label]]}. */
    private static final Pattern LOCAL_LINK = Pattern.compile("\\[\\[#([^\\[\\]|\\n]+?)(?:\\|([^\\[\\]\\n]+?))?\\]\\]");

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
        return resolve(markdown, link -> noteExists(extractTitle(link), knownTitles));
    }

    /**
     * Resolves all internal links in {@code markdown}, asking {@code exists} whether each
     * link (as written, see {@link #internalLinkPath(String)}) points to a note. Links
     * inside code are left untouched.
     *
     * @param markdown raw Markdown content (may be null)
     * @param exists   whether a link points to an existing note
     * @return Markdown with internal links replaced by HTML anchors
     */
    public static String resolve(String markdown, Predicate<String> exists) {
        if (markdown == null || markdown.isEmpty()) {
            return markdown != null ? markdown : "";
        }

        // 1. Resolve [[wiki-links]] first, then [[#links]] within the note
        String result = resolveWikiLinks(markdown, exists);
        result = resolveLocalLinks(result);

        // 2. Then resolve Markdown internal links [label](path). The code ranges are
        //    recomputed: the first pass changed the text.
        result = resolveMarkdownInternalLinks(result, exists);

        return result;
    }

    /**
     * Every internal note link in {@code markdown}, as written: folder path kept, heading,
     * display text and {@code .md} dropped, URL-decoded ({@code [[A/Nota#H|x]]} gives
     * {@code A/Nota}). Links inside code are skipped; external links, anchors and
     * attachments are not note links. De-duplicated, in order of appearance.
     *
     * @param markdown raw Markdown content (may be null)
     * @return ordered, de-duplicated list of links (never null)
     */
    public static List<String> extractRawLinks(String markdown) {
        Set<String> links = new LinkedHashSet<>();
        for (LinkMatch match : findLinks(markdown)) {
            links.add(match.path());
        }
        return new ArrayList<>(links);
    }

    /**
     * An internal note link found in Markdown: where it is ({@code [start, end)} in the text)
     * and the link as written ({@link #internalLinkPath(String)}).
     */
    public record LinkMatch(int start, int end, String path) {
    }

    /**
     * Every internal note link in {@code markdown} with its position, in order of
     * appearance and without de-duplicating. Same rules as {@link #extractRawLinks(String)}
     * (links in code, external links, anchors, images and attachments are skipped).
     *
     * @param markdown raw Markdown content (may be null)
     * @return the links found (never null)
     */
    public static List<LinkMatch> findLinks(String markdown) {
        if (markdown == null || markdown.isEmpty()) {
            return new ArrayList<>();
        }
        BitSet code = codeRanges(markdown);
        java.util.TreeMap<Integer, LinkMatch> byPosition = new java.util.TreeMap<>();

        Matcher wiki = WIKI_LINK.matcher(markdown);
        while (wiki.find()) {
            if (code.get(wiki.start())) {
                continue;
            }
            String path = internalLinkPath(wiki.group(1).trim());
            if (path != null) {
                byPosition.put(wiki.start(), new LinkMatch(wiki.start(), wiki.end(), path));
            }
        }

        Matcher md = MARKDOWN_INTERNAL.matcher(markdown);
        while (md.find()) {
            if (code.get(md.start()) || isImage(markdown, md.start())) {
                continue;
            }
            String url = md.group(2).trim();
            if (url.startsWith(PROTOCOL)) {
                continue;
            }
            String path = internalLinkPath(url);
            if (path != null) {
                byPosition.putIfAbsent(md.start(), new LinkMatch(md.start(), md.end(), path));
            }
        }
        return new ArrayList<>(byPosition.values());
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
        Set<String> titles = new LinkedHashSet<>();
        for (String link : extractRawLinks(markdown)) {
            String title = extractTitle(link);
            if (!title.isBlank()) {
                titles.add(title);
            }
        }
        return new ArrayList<>(titles);
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
        String path = internalLinkPath(rawReference);
        if (path == null) {
            return null;
        }
        String title = extractTitle(path);
        return title.isBlank() ? null : title;
    }

    /**
     * The link as written for an internal note reference, or {@code null} when it is not
     * a note link: URL-decoded, heading and {@code .md} dropped, folder path kept
     * ({@code A/Mi%20nota.md#H} gives {@code A/Mi nota}). Same rejections as
     * {@link #internalNoteTarget(String)}.
     */
    public static String internalLinkPath(String rawReference) {
        if (rawReference == null) {
            return null;
        }
        String ref = rawReference.trim();
        if (ref.isEmpty()
                || ref.startsWith("#")
                || ref.startsWith("mailto:")
                || ref.startsWith("tel:")
                || ref.startsWith(PROTOCOL)
                || ref.contains("://")) {
            return null;
        }
        int pipe = ref.indexOf('|');
        if (pipe >= 0) {
            ref = ref.substring(0, pipe);
        }
        int hash = ref.indexOf('#');
        if (hash >= 0) {
            ref = ref.substring(0, hash);
        }
        if (ref.isBlank()) {
            return null;
        }
        String decoded = urlDecode(ref).trim().replace('\\', '/');
        if (decoded.matches("(?i)^(https?://|www\\.).*")) {
            return null;
        }
        String last = decoded.substring(decoded.lastIndexOf('/') + 1);
        int dot = last.lastIndexOf('.');
        if (dot > 0 && !last.substring(dot + 1).equalsIgnoreCase("md")) {
            return null;
        }
        if (decoded.toLowerCase(java.util.Locale.ROOT).endsWith(".md")) {
            decoded = decoded.substring(0, decoded.length() - 3).trim();
        }
        return extractTitle(decoded).isBlank() ? null : decoded;
    }

    /**
     * The heading or block part of a link ({@code Nota#Sección|texto} → {@code Sección},
     * {@code Nota#^id} → {@code ^id}), or {@code null} when it names the whole note.
     */
    public static String subpathOf(String rawLink) {
        if (rawLink == null) {
            return null;
        }
        String link = rawLink;
        int pipe = link.indexOf('|');
        if (pipe >= 0) {
            link = link.substring(0, pipe);
        }
        int hash = link.indexOf('#');
        if (hash < 0) {
            return null;
        }
        String subpath = link.substring(hash + 1).strip();
        return subpath.isEmpty() ? null : subpath;
    }

    /**
     * The characters of {@code markdown} that are code: fenced blocks ({@code ```} or
     * {@code ~~~}, fence lines included; an unclosed fence runs to the end) and inline
     * code spans (a run of backticks up to the next run of the same length).
     */
    public static BitSet codeRanges(String markdown) {
        BitSet code = new BitSet(markdown.length());
        int n = markdown.length();
        int lineStart = 0;
        char fenceChar = 0;
        int fenceLen = 0;
        int fenceFrom = -1;
        while (lineStart < n) {
            int lineEnd = markdown.indexOf('\n', lineStart);
            if (lineEnd < 0) {
                lineEnd = n;
            }
            int i = lineStart;
            int indent = 0;
            while (i < lineEnd && markdown.charAt(i) == ' ' && indent < 4) {
                i++;
                indent++;
            }
            int run = 0;
            char c = i < lineEnd ? markdown.charAt(i) : 0;
            if (indent < 4 && (c == '`' || c == '~')) {
                while (i + run < lineEnd && markdown.charAt(i + run) == c) {
                    run++;
                }
            }
            if (fenceFrom < 0) {
                boolean opens = run >= 3
                        && (c == '~' || markdown.substring(i + run, lineEnd).indexOf('`') < 0);
                if (opens) {
                    fenceChar = c;
                    fenceLen = run;
                    fenceFrom = lineStart;
                }
            } else if (c == fenceChar && run >= fenceLen
                    && markdown.substring(i + run, lineEnd).isBlank()) {
                code.set(fenceFrom, Math.min(n, lineEnd + 1));
                fenceFrom = -1;
            }
            lineStart = lineEnd + 1;
        }
        if (fenceFrom >= 0) {
            code.set(fenceFrom, n);
        }

        int i = 0;
        while (i < n) {
            if (code.get(i) || markdown.charAt(i) != '`') {
                i++;
                continue;
            }
            int run = 0;
            while (i + run < n && markdown.charAt(i + run) == '`') {
                run++;
            }
            int close = closingRun(markdown, i + run, run, code);
            if (close < 0) {
                i += run;
                continue;
            }
            code.set(i, close + run);
            i = close + run;
        }
        return code;
    }

    /** Start of the next run of exactly {@code len} backticks from {@code from}, outside fences. */
    private static int closingRun(String markdown, int from, int len, BitSet fenced) {
        int n = markdown.length();
        int j = from;
        while (j < n) {
            if (fenced.get(j)) {
                return -1;
            }
            if (markdown.charAt(j) != '`') {
                j++;
                continue;
            }
            int run = 0;
            while (j + run < n && markdown.charAt(j + run) == '`') {
                run++;
            }
            if (run == len) {
                return j;
            }
            j += run;
        }
        return -1;
    }

    /** True when the {@code [} at {@code bracket} is the start of an image ({@code ![…](…)}). */
    private static boolean isImage(String markdown, int bracket) {
        return bracket > 0 && markdown.charAt(bracket - 1) == '!';
    }

    /** @see #internalNoteTarget(String) */
    static String markdownNoteTarget(String rawUrl) {
        return internalNoteTarget(rawUrl);
    }

    // ------------------------------------------------------------------
    // Wiki-link resolution
    // ------------------------------------------------------------------

    private static String resolveWikiLinks(String markdown, Predicate<String> exists) {
        BitSet code = codeRanges(markdown);
        Matcher m = WIKI_LINK.matcher(markdown);
        StringBuilder sb = new StringBuilder();

        while (m.find()) {
            String rawPath  = m.group(1).trim();
            String heading  = m.group(2) != null ? m.group(2).trim() : null;
            String alias    = m.group(3) != null ? m.group(3).trim() : null;

            String path = code.get(m.start()) ? null : internalLinkPath(rawPath);
            if (path == null) {
                m.appendReplacement(sb, Matcher.quoteReplacement(m.group(0)));
                continue;
            }
            String title = extractTitle(path);

            // Determine display text
            String label;
            if (alias != null && !alias.isEmpty()) {
                label = alias;
            } else if (heading != null && !heading.isEmpty()) {
                label = title + " › " + heading;
            } else {
                label = title;
            }

            boolean found = exists != null && exists.test(path);
            String cssClass = found ? "wikilink" : "wikilink wikilink-new";

            // href carries the link (+ optional heading after |); data-target the link as written
            String href = PROTOCOL + encodeTitle(path)
                    + (heading != null && !heading.isEmpty() ? "|" + encodeTitle(heading) : "");

            String replacement = buildAnchor(cssClass, href, path, heading, label);
            m.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    /**
     * {@code [[#Heading]]} and {@code [[#^block]]}: a jump within the rendered note, to the
     * heading's id ({@link MarkdownProcessor#slugifyHeading}) or the block's anchor
     * ({@link ReadingSyntax#blockAnchors}).
     */
    private static String resolveLocalLinks(String markdown) {
        if (!markdown.contains("[[#")) {
            return markdown;
        }
        BitSet code = codeRanges(markdown);
        Matcher m = LOCAL_LINK.matcher(markdown);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            String target = m.group(1).trim();
            if (code.get(m.start()) || target.isEmpty()) {
                m.appendReplacement(sb, Matcher.quoteReplacement(m.group(0)));
                continue;
            }
            String alias = m.group(2) != null ? m.group(2).trim() : "";
            String anchor = target.startsWith("^")
                    ? ReadingSyntax.BLOCK_ID_PREFIX + target.substring(1)
                    : MarkdownProcessor.slugifyHeading(target);
            String label = alias.isEmpty() ? target : alias;
            m.appendReplacement(sb, Matcher.quoteReplacement("<a class=\"wikilink wikilink-local\" href=\"#"
                    + escapeHtml(anchor) + "\">" + escapeHtml(label) + "</a>"));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    // ------------------------------------------------------------------
    // Markdown internal-link resolution
    // ------------------------------------------------------------------

    private static String resolveMarkdownInternalLinks(String markdown, Predicate<String> exists) {
        BitSet code = codeRanges(markdown);
        Matcher m = MARKDOWN_INTERNAL.matcher(markdown);
        StringBuilder sb = new StringBuilder();

        while (m.find()) {
            String label = m.group(1);
            String url   = m.group(2).trim();

            // Skip code, images, and URLs already pointing to our protocol (resolved wiki-links)
            if (code.get(m.start()) || isImage(markdown, m.start()) || url.startsWith(PROTOCOL)) {
                m.appendReplacement(sb, Matcher.quoteReplacement(m.group(0)));
                continue;
            }

            // Only internal note references qualify (rejects anchors, URLs, images…).
            String path = internalLinkPath(url);
            if (path == null || exists == null || !exists.test(path)) {
                // Not an internal link to a known note — leave the Markdown unchanged.
                m.appendReplacement(sb, Matcher.quoteReplacement(m.group(0)));
                continue;
            }

            String href = PROTOCOL + encodeTitle(path);
            int hash = url.indexOf('#');
            String subpath = hash >= 0 ? urlDecode(url.substring(hash + 1)).trim() : null;
            String replacement = buildAnchor("wikilink", href, path, subpath, label);
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

    /**
     * The anchor of a link to another note. {@code data-target} is the note as written;
     * {@code data-subpath}, when the link names a heading or a block ({@code ^id}), is that
     * part, so a click opens the note there.
     */
    private static String buildAnchor(String cssClass, String href, String dataTarget, String subpath, String label) {
        return "<a class=\"" + cssClass
                + "\" href=\"" + href
                + "\" data-target=\"" + escapeHtml(dataTarget)
                + (subpath != null && !subpath.isBlank() ? "\" data-subpath=\"" + escapeHtml(subpath) : "")
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
