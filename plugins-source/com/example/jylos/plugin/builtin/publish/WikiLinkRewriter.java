package com.example.jylos.plugin.builtin.publish;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.example.jylos.util.MarkdownProcessor;

/**
 * Rewrites the {@code jylos://open-note/<title>[|<heading>]} anchors that
 * {@code com.example.jylos.util.WikiLinkResolver} already produced in a note's
 * rendered HTML (before {@link MarkdownProcessor} ever saw it — this class only
 * touches the final HTML, not the raw Markdown) into real relative links pointing
 * at another exported page.
 *
 * <p>Deliberately does not re-implement wiki-link syntax matching: the app's own
 * {@code WikiLinkResolver} already found every {@code [[wiki]]} and
 * {@code [label](note)} reference and turned it into one of these anchors — with
 * the correct {@code wikilink}/{@code wikilink-new} class already applied — before
 * the page's HTML reached this class. Reusing that output, instead of
 * re-recognizing link syntax from scratch, guarantees the exported site resolves
 * links exactly the same way the live app's preview does.</p>
 */
final class WikiLinkRewriter {

    private static final Pattern OPEN_NOTE_HREF = Pattern.compile(
            "href=\"jylos://open-note/([^\"|]+)(?:\\|([^\"]+))?\"");

    private final Function<String, String> hrefFor;

    /**
     * @param hrefFor given a wiki-link's decoded target title, returns the href
     *         (already relative to the note currently being rendered) it should
     *         point to, or {@code null} if the target is not part of this export
     *         (does not exist, or was excluded — e.g. a private note).
     */
    WikiLinkRewriter(Function<String, String> hrefFor) {
        this.hrefFor = hrefFor;
    }

    /**
     * Rewrites every {@code jylos://open-note/...} href in {@code html}. A link
     * whose target is not part of this export becomes a dead {@code href="#"} —
     * its existing {@code wikilink}/{@code wikilink-new} class is left untouched
     * either way, so a resolved link still looks resolved and a broken one still
     * looks broken.
     */
    String rewrite(String html) {
        if (html == null || html.isEmpty()) {
            return "";
        }
        Matcher m = OPEN_NOTE_HREF.matcher(html);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            String title = decode(m.group(1));
            String heading = m.group(2) != null ? decode(m.group(2)) : null;
            String targetHref = hrefFor.apply(title);

            String replacement;
            if (targetHref == null) {
                replacement = "href=\"#\"";
            } else {
                String anchor = heading != null && !heading.isEmpty()
                        ? "#" + MarkdownProcessor.slugifyHeading(heading)
                        : "";
                replacement = "href=\"" + targetHref + anchor + "\"";
            }
            m.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    private static String decode(String raw) {
        try {
            return URLDecoder.decode(raw, StandardCharsets.UTF_8);
        } catch (Exception e) {
            return raw;
        }
    }
}
