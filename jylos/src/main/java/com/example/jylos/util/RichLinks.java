package com.example.jylos.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

/**
 * Rich links: paste a URL and store it as a small visual "card" (title, site,
 * description, thumbnail) instead of a bare link. Inspired by Glyphary's
 * {@code ::: rich-link} container.
 *
 * <p>This class owns the on-disk Markdown format and the preview rendering, and
 * is deliberately free of any network I/O so it stays pure and unit-testable —
 * fetching the page is {@link com.example.jylos.service.RichLinkService}'s job.</p>
 *
 * <h3>Stored format</h3>
 * <pre>
 * ::: rich-link
 * url: https://example.com/article
 * title: Article title
 * description: One-line summary
 * image: https://example.com/og.png
 * siteName: Example
 * :::
 * </pre>
 * Only {@code url} is required; blank fields are omitted. The block is plain text,
 * so it round-trips through any Markdown tool and degrades to readable text
 * elsewhere.
 *
 * @author Edu Díaz (RGiskard7)
 * @since 2.2.0
 */
public final class RichLinks {

    private RichLinks() {
    }

    /** Metadata backing a rich-link card. Only {@link #url} is guaranteed non-blank. */
    public record RichLink(String url, String title, String description, String image, String siteName) {
    }

    /** Matches a whole {@code ::: rich-link … :::} block (its inner lines are group 1). */
    private static final Pattern BLOCK = Pattern.compile(
            "(?m)^:::[ \\t]*rich-link[ \\t]*\\r?\\n([\\s\\S]*?)\\r?\\n:::[ \\t]*$");

    // ── Markdown generation ─────────────────────────────────────────────────

    /** Builds the {@code ::: rich-link} block for {@code link}, omitting blank fields. */
    public static String toMarkdown(RichLink link) {
        StringBuilder sb = new StringBuilder("::: rich-link\n");
        sb.append("url: ").append(oneLine(link.url())).append('\n');
        appendField(sb, "title", link.title());
        appendField(sb, "description", link.description());
        appendField(sb, "image", link.image());
        appendField(sb, "siteName", link.siteName());
        sb.append(":::");
        return sb.toString();
    }

    private static void appendField(StringBuilder sb, String key, String value) {
        String v = oneLine(value);
        if (!v.isEmpty()) {
            sb.append(key).append(": ").append(v).append('\n');
        }
    }

    /** Collapses newlines so each stored field stays on a single line. */
    private static String oneLine(String value) {
        return value == null ? "" : value.replaceAll("\\s*\\r?\\n\\s*", " ").trim();
    }

    // ── OpenGraph parsing ───────────────────────────────────────────────────

    /**
     * Extracts rich-link metadata from a fetched HTML page, preferring OpenGraph
     * tags and falling back to {@code <title>} / meta description / the host name.
     * Pure (no network), so it is unit-testable.
     *
     * @param html the page HTML (may be empty)
     * @param url  the page URL (used as a fallback and to derive the site name)
     */
    public static RichLink parseMetadata(String html, String url) {
        Document doc = Jsoup.parse(html == null ? "" : html);
        String title = firstNonBlank(
                metaContent(doc, "og:title"),
                doc.title());
        String description = firstNonBlank(
                metaContent(doc, "og:description"),
                metaNamed(doc, "description"));
        String image = metaContent(doc, "og:image");
        String siteName = firstNonBlank(
                metaContent(doc, "og:site_name"),
                hostOf(url));
        return new RichLink(url, oneLine(title), oneLine(description), oneLine(image), oneLine(siteName));
    }

    private static String metaContent(Document doc, String property) {
        var el = doc.selectFirst("meta[property=" + property + "]");
        if (el == null) {
            el = doc.selectFirst("meta[name=" + property + "]"); // some sites use name=
        }
        return el != null ? el.attr("content") : "";
    }

    private static String metaNamed(Document doc, String name) {
        var el = doc.selectFirst("meta[name=" + name + "]");
        return el != null ? el.attr("content") : "";
    }

    /** Bare host name (no {@code www.}) of a URL, or "" if it cannot be parsed. */
    public static String hostOf(String url) {
        try {
            String host = java.net.URI.create(url.trim()).getHost();
            if (host == null) {
                return "";
            }
            return host.startsWith("www.") ? host.substring(4) : host;
        } catch (Exception e) {
            return "";
        }
    }

    // ── Preview rendering ───────────────────────────────────────────────────

    /**
     * Replaces every {@code ::: rich-link} block in {@code markdown} with the HTML
     * for a card, leaving the rest untouched. Runs before CommonMark in the preview
     * pipeline; the emitted card is a {@code <div>} HTML block so CommonMark passes
     * it through verbatim.
     */
    public static String render(String markdown) {
        if (markdown == null || !markdown.contains("rich-link")) {
            return markdown;
        }
        Matcher m = BLOCK.matcher(markdown);
        StringBuilder out = new StringBuilder(markdown.length() + 256);
        while (m.find()) {
            RichLink link = parseBlock(m.group(1));
            String card = link == null ? m.group() : cardHtml(link);
            // Surround with blank lines so CommonMark treats it as a standalone HTML block.
            m.appendReplacement(out, Matcher.quoteReplacement("\n" + card + "\n"));
        }
        m.appendTail(out);
        return out.toString();
    }

    /** Parses the {@code key: value} lines of a block; returns {@code null} if there is no usable URL. */
    private static RichLink parseBlock(String body) {
        String url = "";
        String title = "";
        String description = "";
        String image = "";
        String siteName = "";
        for (String line : body.split("\\r?\\n")) {
            int colon = line.indexOf(':');
            if (colon < 0) {
                continue;
            }
            String key = line.substring(0, colon).trim().toLowerCase(java.util.Locale.ROOT);
            String value = line.substring(colon + 1).trim();
            switch (key) {
                case "url" -> url = value;
                case "title" -> title = value;
                case "description" -> description = value;
                case "image" -> image = value;
                case "sitename" -> siteName = value;
                default -> { /* ignore unknown keys */ }
            }
        }
        if (!isHttpUrl(url)) {
            return null;
        }
        return new RichLink(url, title, description, image, siteName);
    }

    /** Builds the card HTML, escaping all text and only honouring {@code http(s)} URLs/images. */
    private static String cardHtml(RichLink link) {
        String safeUrl = escape(link.url());
        String title = escape(firstNonBlank(link.title(), link.url()));
        String description = escape(link.description());
        String site = escape(firstNonBlank(link.siteName(), hostOf(link.url())));

        StringBuilder card = new StringBuilder();
        card.append("<div class=\"rich-link\">");
        card.append("<a class=\"rich-link-card\" href=\"").append(safeUrl).append("\">");
        if (isHttpUrl(link.image())) {
            card.append("<span class=\"rich-link-thumb\" style=\"background-image:url('")
                    .append(escape(link.image())).append("')\"></span>");
        }
        card.append("<span class=\"rich-link-body\">");
        card.append("<span class=\"rich-link-title\">").append(title).append("</span>");
        if (!description.isEmpty()) {
            card.append("<span class=\"rich-link-desc\">").append(description).append("</span>");
        }
        if (!site.isEmpty()) {
            card.append("<span class=\"rich-link-site\">").append(site).append("</span>");
        }
        card.append("</span></a></div>");
        return card.toString();
    }

    private static boolean isHttpUrl(String url) {
        if (url == null) {
            return false;
        }
        String u = url.trim().toLowerCase(java.util.Locale.ROOT);
        return u.startsWith("http://") || u.startsWith("https://");
    }

    private static String firstNonBlank(String... values) {
        for (String v : values) {
            if (v != null && !v.isBlank()) {
                return v.trim();
            }
        }
        return "";
    }

    /** Minimal HTML-attribute/text escaping for values that originate from the web. */
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
