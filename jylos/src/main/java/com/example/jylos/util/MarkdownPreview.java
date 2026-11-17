package com.example.jylos.util;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import javax.imageio.ImageIO;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import com.example.jylos.config.LoggerConfig;
import com.example.jylos.plugin.PreviewContext;
import com.example.jylos.plugin.PreviewEnhancer;
import com.example.jylos.service.LinkIndex;

/**
 * Builds the complete HTML document loaded into the JavaFX {@code WebView} preview.
 *
 * <p>The pipeline resolves Jylos-specific Markdown extensions first (wiki-links,
 * transclusion and rich links), delegates CommonMark rendering to
 * {@link MarkdownProcessor}, then injects preview-only assets such as syntax
 * highlighting, KaTeX and plugin enhancers.</p>
 *
 * @author Edu Díaz (RGiskard7)
 * @since 1.0.0
 */
public class MarkdownPreview {
    private static final Logger logger = LoggerConfig.getLogger(MarkdownPreview.class);
    private static final String HLJS_SCRIPT_RESOURCE =
            "/com/example/jylos/ui/preview/highlightjs/highlight.min.js";
    private static final String HLJS_LIGHT_CSS_RESOURCE =
            "/com/example/jylos/ui/preview/highlightjs/vs.min.css";
    private static final String HLJS_DARK_CSS_RESOURCE =
            "/com/example/jylos/ui/preview/highlightjs/vs2015.min.css";

    /**
     * Emoji rendering. Every emoji becomes an inline {@code <img>}, which renders everywhere,
     * PDF export included: the bundled colour Twemoji image of that emoji, or, for a symbol
     * shown as text (↩, ✔) and anything Twemoji lacks, one rasterised by Java2D from the
     * bundled monochrome Noto Emoji font in the theme's text colour. Where JavaFX's WebKit
     * draws colour emoji (macOS), the page script {@code colour-emoji.js} swaps the ones it
     * can draw back to text: the engine only draws a colour emoji alone in its own element,
     * with an emoji font, and does not shape sequences (skin tones, ZWJ, flags, keycaps),
     * which stay Twemoji images. Rendered emoji are cached per (emoji, colour).
     */
    private static final String EMOJI_FONT_RESOURCE =
            "/com/example/jylos/ui/preview/fonts/NotoEmoji-Regular.ttf";
    private static final String COLOUR_EMOJI_SCRIPT_RESOURCE = "/com/example/jylos/ui/preview/colour-emoji.js";
    /** Twemoji's 72 px images (CC-BY 4.0, see LICENSE-GRAPHICS.txt beside it), by file name. */
    private static final String TWEMOJI_RESOURCE = "/com/example/jylos/ui/preview/twemoji/twemoji-72.zip";
    private static final Font EMOJI_AWT_FONT = loadEmojiAwtFont();
    private static final Map<String, String> EMOJI_CACHE = new ConcurrentHashMap<>();
    private static final Pattern EMOJI_RUN = Pattern.compile(
            "[\\x{1F000}-\\x{1FAFF}\\x{2600}-\\x{27BF}\\x{2B00}-\\x{2BFF}\\x{2300}-\\x{23FF}"
            + "\\x{2190}-\\x{21FF}\\x{FE00}-\\x{FE0F}\\x{20E3}\\x{200D}\\x{1F1E6}-\\x{1F1FF}\\x{1F3FB}-\\x{1F3FF}]+");

    /**
     * What a reading view render needs besides the Markdown, the theme and the enhancers.
     *
     * @param baseDir                 folder to resolve relative image paths against, or
     *                                {@code null}; local images are inlined as {@code data:}
     *                                URIs, since the WebView loads content with an opaque
     *                                origin and cannot fetch {@code file://} resources (it also
     *                                makes an exported HTML self-contained)
     * @param embeddedContentResolver the Markdown of a note embedded with {@code ![[…]]}, by
     *                                its link as written, or {@code null} for none
     * @param context                 the note and theme the render belongs to, for plugin
     *                                enhancers and to resolve links from the note's folder;
     *                                {@code null} for none
     * @param readableLineLength      cap the body to a centered column
     * @param contentFontSize         body text size in pixels, or {@code 0} for the WebView's
     *                                default
     * @param imageContainmentRoot    bound of the local files an image may inline, normally
     *                                the vault root ({@code ../attachments/x.png} works,
     *                                {@code ../../.ssh/id_rsa} does not); {@code null} falls
     *                                back to {@code baseDir}
     * @param links                   the vault's link index, to tell links to existing notes
     *                                from links to notes to create; {@code null} for none
     * @param palette                 the colours of an external theme, painted over the
     *                                page's own; {@code null} to keep them
     */
    public record PreviewOptions(Path baseDir, Function<String, String> embeddedContentResolver,
            PreviewContext context, boolean readableLineLength, int contentFontSize,
            Path imageContainmentRoot, LinkIndex links, ThemePalette palette) {

        /** With the page's own colours. */
        public PreviewOptions(Path baseDir, Function<String, String> embeddedContentResolver,
                PreviewContext context, boolean readableLineLength, int contentFontSize,
                Path imageContainmentRoot, LinkIndex links) {
            this(baseDir, embeddedContentResolver, context, readableLineLength, contentFontSize,
                    imageContainmentRoot, links, null);
        }

        /** No images inlined, no embeds, no note, no links. */
        public static PreviewOptions none() {
            return new PreviewOptions(null, null, null, false, 0, null, null);
        }

        public PreviewOptions withBaseDir(Path dir) {
            return new PreviewOptions(dir, embeddedContentResolver, context, readableLineLength, contentFontSize,
                    imageContainmentRoot, links, palette);
        }

        public PreviewOptions withPalette(ThemePalette colours) {
            return new PreviewOptions(baseDir, embeddedContentResolver, context, readableLineLength, contentFontSize,
                    imageContainmentRoot, links, colours);
        }
    }

    public static String buildPreviewHtml(String markdownContent, boolean isDarkTheme, Collection<PreviewEnhancer> enhancers) {
        return buildPreviewHtml(markdownContent, isDarkTheme, enhancers, PreviewOptions.none());
    }

    /** Same, inlining the local images the note references, resolved against {@code baseDir}. */
    public static String buildPreviewHtml(String markdownContent, boolean isDarkTheme,
            Collection<PreviewEnhancer> enhancers, Path baseDir) {
        return buildPreviewHtml(markdownContent, isDarkTheme, enhancers, PreviewOptions.none().withBaseDir(baseDir));
    }

    /**
     * The body of a note as the reading view renders it, without the page around it or what
     * plugins add: Obsidian syntax (comments hidden, footnotes, block anchors, and {@code #tags}
     * as links when {@code tags}), embeds, links, rich-link cards, inlined local images and
     * emoji. The PDF export prints the same body.
     */
    public static String renderBody(String markdownContent, boolean isDarkTheme, boolean tags,
            PreviewOptions options) {
        String raw = markdownContent == null ? "" : markdownContent;
        PreviewOptions o = options != null ? options : PreviewOptions.none();

        // Links resolve by path from the note shown, as in Obsidian, against the vault's link
        // index (kept warm by the caller's NoteService, not rebuilt on every keystroke).
        LinkIndex links = o.links() != null ? o.links()
                : LinkIndex.EMPTY;
        String sourceId = o.context() != null && o.context().note() != null ? o.context().note().getId() : null;
        Predicate<String> exists = link -> links.exists(link, sourceId);

        // Obsidian syntax resolved on the source (outside code): comments hidden, footnotes
        // numbered (their list is appended after rendering), ^block anchors, #tags as links.
        // Callouts and ==highlights== are handled inside CommonMark (CalloutsAndHighlights).
        raw = ReadingSyntax.stripComments(raw);
        ReadingSyntax.Footnotes footnotes = ReadingSyntax.footnotes(raw);
        raw = ReadingSyntax.blockAnchors(footnotes.markdown());
        if (tags) {
            raw = ReadingSyntax.tags(raw);
        }

        // Expand ![[transclusions]] FIRST: ![[X]] contains a [[X]] that WikiLinkResolver
        // would otherwise rewrite. Each embed is rendered now and parked behind a token,
        // re-injected after CommonMark (see Transclusion).
        Transclusion.Result embeds = Transclusion.protect(raw,
                title -> embedContentByTitle(title, o.embeddedContentResolver()), exists);
        raw = embeds.markdown();

        // [[WikiLinks]] are resolved in the raw Markdown, then passed through CommonMark,
        // which leaves raw HTML untouched (MarkdownProcessor deliberately does not escape it).
        raw = WikiLinkResolver.resolve(raw, exists);

        // ::: rich-link blocks become HTML cards (an HTML block, passed through verbatim).
        raw = RichLinks.render(raw);

        String html = MarkdownProcessor.markdownToHtml(raw) + footnotes.html();
        html = Transclusion.restore(html, embeds.embeds());
        html = embedLocalImages(html, o.baseDir(), o.imageContainmentRoot());
        return emojifyToImages(html, isDarkTheme);
    }

    /**
     * The reading view page of a note: its {@link #renderBody body} plus plugin enhancers,
     * syntax highlighting, math, styles and the page's scripts. Script a note carries in raw
     * HTML is neutralised by the page's Content-Security-Policy, not by escaping.
     */
    public static String buildPreviewHtml(String markdownContent, boolean isDarkTheme,
            Collection<PreviewEnhancer> enhancers, PreviewOptions options) {
        PreviewOptions o = options != null ? options : PreviewOptions.none();
        boolean readableLineLength = o.readableLineLength();
        int contentFontSize = o.contentFontSize();
        PreviewContext context = o.context();
        String raw = markdownContent == null ? "" : markdownContent;
        String html = renderBody(raw, isDarkTheme, true, o);
        // Per-note post-processing runs before code-block detection below: an enhancer
        // that replaces a fenced block with generated markup (e.g. a query result table)
        // must be able to remove the only <pre><code> in the document, so highlight.js
        // is not shipped for a preview that no longer has code in it.
        html = applyHtmlTransforms(html, enhancers, context);
        Injections injections = MarkdownPreview.collectInjections(enhancers);
        boolean codeBlocks = containsCodeBlocks(html);
        String highlightCss = codeBlocks
                ? (isDarkTheme ? highlightDarkCss() : highlightLightCss())
                : "";
        String highlightAsset = codeBlocks ? highlightJsScript() : "";

        String styleBlock = isDarkTheme ? MarkdownPreview.darkStyles(readableLineLength, contentFontSize)
                : MarkdownPreview.lightStyles(readableLineLength, contentFontSize);
        styleBlock = styleBlock + paletteStyles(o.palette());
        String highlightScript = codeBlocks ? MarkdownPreview.highlightScriptBlock() : "";

        // KaTeX math (offline) — only when the note actually contains math delimiters.
        boolean math = containsMath(raw);
        if (math) {
            styleBlock = styleBlock + "\n" + katexCss();
        }
        String mathScript = math ? katexScriptBlock() : "";

        // Only scripts Jylos itself emits — and those plugins inject, which are trusted
        // code — carry this per-render nonce. Script written into a note does not, so the
        // CSP below keeps it from running (inline handlers such as onerror= included).
        String nonce = newNonce();
        mathScript = withNonce(mathScript, nonce);
        String headInjections = withNonce(injections.head(), nonce);
        String bodyInjections = withNonce(injections.body(), nonce);

        return """
                <!DOCTYPE html>
                <html>
                <head>
                    <meta charset="UTF-8">
                    <meta http-equiv="Content-Type" content="text/html; charset=UTF-8">
                    <meta http-equiv="Content-Security-Policy" content="%s">
                    %s
                    <style>
                        %s
                        %s
                    </style>
                </head>
                <body>
                %s
                <script nonce="%s">
                    %s
                    %s
                    %s

                    // JavaFX's WebKit cannot decode WebP, which many pages serve even under a
                    // .png name. An image that fails is handed to Java (javaApp.convertImage),
                    // which answers with a PNG copy through imageConverted(); the ones that fail
                    // before the bridge is set wait for bridgeReady().
                    (function() {
                        var page = Math.random().toString(36).slice(2);
                        var next = 0, waiting = {}, early = [];
                        function convert(img) {
                            if (img.__jylosConverting || !img.getAttribute('src')) { return; }
                            if (!window.javaApp || !window.javaApp.convertImage) { early.push(img); return; }
                            img.__jylosConverting = true;
                            var id = page + ':' + (++next);
                            waiting[id] = img;
                            window.javaApp.convertImage(id, img.src);
                        }
                        window.__jylosImages = {
                            imageConverted: function(id, png) {
                                var img = waiting[id];
                                delete waiting[id];
                                if (img && png) { img.src = png; }
                            },
                            bridgeReady: function() {
                                var pending = early;
                                early = [];
                                pending.forEach(convert);
                            }
                        };
                        document.addEventListener('error', function(e) {
                            if (e.target && e.target.tagName === 'IMG') { convert(e.target); }
                        }, true);
                        Array.prototype.forEach.call(document.images, function(img) {
                            if (img.complete && img.naturalWidth === 0) { convert(img); }
                        });
                    })();

                    document.addEventListener('click', function(e) {
                        let target = e.target.closest('a');
                        if (!target) { return; }
                        let href = target.getAttribute('href');
                        if (!href) { return; }
                        if (href.startsWith('jylos://open-note/')) {
                            e.preventDefault();
                            let title = target.getAttribute('data-target');
                            if (!title) {
                                // fallback for pure markdown links [Note](jylos://...)
                                title = decodeURIComponent(href.substring('jylos://open-note/'.length));
                            }
                            // A link to a heading or block opens the note there.
                            let subpath = target.getAttribute('data-subpath');
                            if (subpath) {
                                title = title + '#' + subpath;
                            }
                            if (window.javaApp) {
                                window.javaApp.openNote(title);
                            }
                        } else if (href.startsWith('jylos://tag/')) {
                            e.preventDefault();
                            if (window.javaApp && window.javaApp.openTag) {
                                window.javaApp.openTag(decodeURIComponent(href.substring('jylos://tag/'.length)));
                            }
                        } else if (href.startsWith('http://') || href.startsWith('https://')) {
                            // Open external links (incl. rich-link cards) in the system browser
                            // instead of navigating the preview away from the note.
                            e.preventDefault();
                            if (window.javaApp) {
                                window.javaApp.openExternal(href);
                            }
                        } else if (href.startsWith('#') && href.length > 1) {
                            // Same-page anchors (e.g. a hand-written or TOC-plugin-generated
                            // table of contents): this page has no real URL (loaded via
                            // loadContent, not a file:// or http:// address), so WebKit's
                            // native fragment-jump can't be relied on. Do it ourselves.
                            e.preventDefault();
                            let anchorTarget = document.getElementById(decodeURIComponent(href.slice(1)));
                            if (anchorTarget) {
                                anchorTarget.scrollIntoView({ block: 'start' });
                            }
                        }
                    });
                </script>
                %s
                %s
                </body>
                </html>
                """.formatted(previewContentSecurityPolicy(nonce), headInjections, highlightCss, styleBlock, html,
                nonce, colourEmojiScript(), highlightAsset, highlightScript, mathScript, bodyInjections);
    }

    private static final SecureRandom NONCE_RANDOM = new SecureRandom();

    /** A fresh, unguessable CSP nonce for one rendered page. */
    private static String newNonce() {
        byte[] bytes = new byte[18];
        NONCE_RANDOM.nextBytes(bytes);
        return Base64.getEncoder().encodeToString(bytes);
    }

    /**
     * Policy for the reading preview and HTML export. Scripts run only with the page's
     * nonce; the page cannot open connections, frames, objects or forms, so even an
     * injected script would have no channel to exfiltrate note content. Inline styles and
     * remote images (a normal part of Markdown notes) stay allowed.
     */
    private static String previewContentSecurityPolicy(String nonce) {
        return "default-src 'none'; script-src 'nonce-" + nonce + "'; style-src 'unsafe-inline'; "
                + "img-src data: https: http:; font-src data:; media-src data: https: http:; "
                + "connect-src 'none'; frame-src 'none'; object-src 'none'; form-action 'none'; base-uri 'none'";
    }

    /** Adds {@code nonce} to every {@code <script>} tag of trusted, Jylos- or plugin-generated markup. */
    private static String withNonce(String trustedHtml, String nonce) {
        if (trustedHtml == null || trustedHtml.isEmpty()) {
            return trustedHtml;
        }
        return trustedHtml.replaceAll("(?i)<script(?=[\\s>])", "<script nonce=\"" + nonce + "\"");
    }

    /**
     * Resolves a note title to raw Markdown for {@code ![[embeds]]}. The caller provides
     * the resolution strategy so preview rendering stays independent from global state.
     */
    private static String embedContentByTitle(String title, Function<String, String> embeddedContentResolver) {
        if (embeddedContentResolver == null || title == null || title.isBlank()) {
            return null;
        }
        return embeddedContentResolver.apply(title);
    }

    public static String buildEmptyHtml(boolean isDarkTheme) {
        return buildEmptyHtml(isDarkTheme, null);
    }

    /** The empty page, in an external theme's colours when {@code palette} is given. */
    public static String buildEmptyHtml(boolean isDarkTheme, ThemePalette palette) {
        String fg = palette != null ? palette.muted() : isDarkTheme ? "#B3B3B3" : "#71717A";
        String bg = palette != null ? palette.background() : isDarkTheme ? "#1E1E1E" : "#FFFFFF";
        String thumb = palette != null ? palette.border() : isDarkTheme ? "#505050" : "#CBD5E0";
        return """
                <!DOCTYPE html>
                <html>
                <head>
                    <meta charset="UTF-8">
                    <meta http-equiv="Content-Type" content="text/html; charset=UTF-8">
                    <style>
                        html, body { color: %s; background-color: %s; margin: 0; padding: 0; width: 100%%; height: 100%%; }
                        body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif; padding: 20px; }
                        ::-webkit-scrollbar { width: 8px; height: 8px; }
                        ::-webkit-scrollbar-track { background: %s; }
                        ::-webkit-scrollbar-thumb { background: %s; border-radius: 10px; min-height: 32px; }
                    </style>
                </head>
                <body></body>
                </html>
                """.formatted(fg, bg, bg, thumb);
    }

    /**
     * Chains every enhancer's {@link PreviewEnhancer#transformHtml} over the rendered
     * body, in registration order, so each sees the previous one's output. A throwing
     * or {@code null}-returning enhancer leaves the HTML as it was rather than losing
     * the note's content.
     */
    private static String applyHtmlTransforms(String html, Collection<PreviewEnhancer> enhancers,
            PreviewContext context) {
        if (context == null || enhancers == null || enhancers.isEmpty()) {
            return html;
        }
        String current = html;
        for (PreviewEnhancer enhancer : enhancers) {
            if (enhancer == null) {
                continue;
            }
            try {
                String transformed = enhancer.transformHtml(context, current);
                if (transformed != null) {
                    current = transformed;
                }
            } catch (Exception e) {
                logger.warning("Preview enhancer HTML transform failed: " + e.getMessage());
            }
        }
        return current;
    }

    private static Injections collectInjections(Collection<PreviewEnhancer> enhancers) {
        if (enhancers == null || enhancers.isEmpty()) {
            return new Injections("", "");
        }
        StringBuilder head = new StringBuilder();
        StringBuilder body = new StringBuilder();

        for (PreviewEnhancer enhancer : enhancers) {
            if (enhancer == null) {
                continue;
            }
            try {
                String headInjection = enhancer.getHeadInjections();
                if (headInjection != null && !headInjection.isBlank()) {
                    head.append(headInjection).append("\n");
                }
            } catch (Exception e) {
                logger.warning("Preview enhancer head injection failed: " + e.getMessage());
            }
            try {
                String bodyInjection = enhancer.getBodyInjections();
                if (bodyInjection != null && !bodyInjection.isBlank()) {
                    body.append(bodyInjection).append("\n");
                }
            } catch (Exception e) {
                logger.warning("Preview enhancer body injection failed: " + e.getMessage());
            }
        }
        return new Injections(head.toString(), body.toString());
    }

    /** Centered content column width (Obsidian's own default), shared by editor and preview. */
    private static final int READABLE_LINE_LENGTH_PX = 700;

    /** Centered content column, applied only when the reader opts into it — plain string
     * concatenation (not a formatted text block) because the CSS below is full of literal
     * {@code %} signs that a {@code .formatted()} call would misparse as format specifiers. */
    private static String readableLineLengthRule() {
        return "body { max-width: " + READABLE_LINE_LENGTH_PX
                + "px; margin-left: auto; margin-right: auto; }";
    }

    /**
     * Explicit body text size, independent from the readable-line-length column width.
     * Headings/lists/blockquotes/table cells inherit it (they carry no {@code font-size}
     * of their own, or use relative {@code em} units off it); code blocks keep their own
     * fixed monospace size on purpose, same convention as most editors — a "text size"
     * control for prose shouldn't also resize copy-pasted code samples.
     */
    private static String contentFontSizeRule(int contentFontSize) {
        return contentFontSize > 0 ? "body { font-size: " + contentFontSize + "px; }" : "";
    }

    /**
     * An external theme's colours over the page's own: background, text, headings, links,
     * quotes, code, tables, cards and embeds. Empty without a palette.
     */
    static String paletteStyles(ThemePalette p) {
        if (p == null) {
            return "";
        }
        return """

                /* theme palette */
                html, body { background-color: %1$s; color: %2$s; }
                ::-webkit-scrollbar-track { background: %1$s; }
                ::-webkit-scrollbar-thumb { background: %3$s; }
                ::-webkit-scrollbar-thumb:hover { background: %4$s; }
                h1, h2, h3, h4, h5, h6, strong, table th { color: %2$s; }
                ul, ol, table td, .rich-link-title { color: %2$s; }
                h1, h2 { border-bottom-color: %3$s; }
                hr { border-top-color: %3$s; }
                img, pre, table th, table td, a.rich-link-card { border-color: %3$s; }
                code:not(pre code) { background-color: %5$s; color: %6$s; }
                pre { background-color: %5$s; }
                blockquote { border-left-color: %6$s; color: %4$s; background-color: %5$s; }
                table th { background-color: %5$s; }
                table td { background-color: %1$s; }
                a, a.wikilink, .embed-title { color: %6$s; }
                a.wikilink { border-bottom-color: %6$s; }
                a:hover, a.wikilink:hover { color: %2$s; }
                a.rich-link-card { background-color: %5$s; }
                a.rich-link-card:hover { border-color: %6$s; background-color: %7$s; }
                .rich-link-desc, .footnotes { color: %4$s; }
                .rich-link-site { color: %8$s; }
                .embed { border-left-color: %3$s; background-color: %5$s; }
                """.formatted(p.background(), p.text(), p.border(), p.muted(), p.surface(), p.accent(), p.hover(),
                p.faint());
    }

    private static String darkStyles(boolean readableLineLength, int contentFontSize) {
        String base = """
                html { background-color: #1E1E1E; margin: 0; padding: 0; }
                body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, 'Helvetica Neue', Arial, sans-serif; padding: 20px; line-height: 1.6; color: #E0E0E0; background-color: #1E1E1E; -webkit-font-smoothing: antialiased; text-rendering: optimizeLegibility; overflow-x: hidden; }
                ::-webkit-scrollbar { width: 8px; height: 8px; }
                ::-webkit-scrollbar-track { background: #1E1E1E; }
                ::-webkit-scrollbar-thumb { background: #505050; border-radius: 10px; min-height: 32px; }
                ::-webkit-scrollbar-thumb:hover { background: #606060; }
                body > :first-child { margin-top: 0; }
                body > :last-child { margin-bottom: 0; }
                h1, h2, h3, h4, h5, h6 { margin-top: 1.5em; margin-bottom: 0.5em; font-weight: 600; color: #FFFFFF; }
                h1 { font-size: 2em; border-bottom: 2px solid #3a3a3a; padding-bottom: 0.3em; }
                h2 { font-size: 1.5em; border-bottom: 1px solid #3a3a3a; padding-bottom: 0.3em; }
                h3 { font-size: 1.25em; }
                code:not(pre code) { background-color: #2d2d2d; color: #ce9178; padding: 2px 6px; border-radius: 4px; font-family: 'JetBrains Mono', 'Fira Code', 'Consolas', monospace; font-size: 0.9em; }
                pre { background-color: #1e1e1e; border: 1px solid #3a3a3a; border-radius: 6px; margin: 1em 0; overflow-x: auto; position: relative; }
                pre code { font-family: 'JetBrains Mono', 'Fira Code', 'Consolas', monospace; font-size: 13px; line-height: 1.5; padding: 16px !important; display: block; background: transparent !important; color: inherit; }
                .hljs { background: transparent !important; }
                blockquote { border-left: 4px solid #818CF8; margin: 0; padding-left: 20px; color: #B3B3B3; background-color: #252525; padding: 10px 20px; border-radius: 4px; }
                ul, ol { margin: 1em 0; padding-left: 2em; color: #E0E0E0; }
                li { margin: 0.5em 0; }
                table { border-collapse: collapse; width: 100%; margin: 1em 0; }
                table th, table td { border: 1px solid #3a3a3a; padding: 10px; text-align: left; }
                table th { background-color: #252525; font-weight: 600; color: #FFFFFF; }
                table td { background-color: #1E1E1E; color: #E0E0E0; }
                a { color: #818CF8; text-decoration: none; }
                a:hover { color: #A5B4FC; text-decoration: underline; }
                img { max-width: 100%; height: auto; border-radius: 6px; border: 1px solid #3a3a3a; }
                img.emoji { height: 1.05em; width: auto; max-width: none; vertical-align: -0.15em; margin: 0 0.04em; border: none; border-radius: 0; display: inline; }
                .emoji-glyph { font-family: "Apple Color Emoji", "Segoe UI Emoji", "Noto Color Emoji", sans-serif; }
                hr { border: none; border-top: 1px solid #3a3a3a; margin: 2em 0; }
                strong { color: #FFFFFF; font-weight: 600; }
                mark { background-color: #564a00; color: #ffd700; padding: 1px 3px; border-radius: 2px; }
                a.wikilink { color: #818CF8; text-decoration: none; border-bottom: 1px dashed #818CF8; cursor: pointer; }
                a.wikilink:hover { color: #A5B4FC; border-bottom-style: solid; }
                a.wikilink-new { color: #ef4444; border-bottom-color: #ef4444; }
                a.wikilink-new:hover { color: #f87171; border-bottom-color: #f87171; }
                .rich-link { margin: 1em 0; }
                a.rich-link-card { display: flex; align-items: stretch; text-decoration: none; border: 1px solid #3a3a3a; border-radius: 8px; overflow: hidden; background-color: #252525; transition: border-color 0.15s, background-color 0.15s; }
                a.rich-link-card:hover { border-color: #818CF8; background-color: #2a2a2a; text-decoration: none; }
                .rich-link-thumb { flex: 0 0 120px; background-size: cover; background-position: center; background-repeat: no-repeat; border: none; border-radius: 0; }
                .rich-link-body { display: flex; flex-direction: column; gap: 4px; padding: 12px 14px; min-width: 0; }
                .rich-link-title { color: #E0E0E0; font-weight: 600; font-size: 0.95em; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
                .rich-link-desc { color: #B3B3B3; font-size: 0.85em; line-height: 1.4; display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical; overflow: hidden; }
                .rich-link-site { color: #777777; font-size: 0.78em; margin-top: 2px; }
                .embed { border-left: 3px solid #3a3a3a; background-color: #232323; border-radius: 0 6px 6px 0; margin: 1em 0; padding: 4px 16px 8px 16px; }
                .embed-title { display: block; font-size: 0.8em; font-weight: 600; color: #818CF8; text-decoration: none; margin: 6px 0; text-transform: uppercase; letter-spacing: 0.03em; }
                .embed-title:hover { text-decoration: underline; }
                .embed-body > :first-child { margin-top: 0; }
                .embed-body > :last-child { margin-bottom: 0; }
                .embed-note { color: #f87171; font-size: 0.8em; font-style: italic; }
                .embed-missing, .embed-cycle, .embed-too-deep { border-left-color: #ef4444; background-color: #2a1f1f; }
                """;
        return base
                + readingSyntaxStyles(true)
                + (readableLineLength ? readableLineLengthRule() : "")
                + contentFontSizeRule(contentFontSize);
    }

    private static String lightStyles(boolean readableLineLength, int contentFontSize) {
        String base = """
                html { background-color: #FFFFFF; margin: 0; padding: 0; }
                body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, 'Helvetica Neue', Arial, sans-serif; padding: 20px; line-height: 1.6; color: #24292e; background-color: #FFFFFF; -webkit-font-smoothing: antialiased; text-rendering: optimizeLegibility; overflow-x: hidden; }
                ::-webkit-scrollbar { width: 8px; height: 8px; }
                ::-webkit-scrollbar-track { background: #FFFFFF; }
                ::-webkit-scrollbar-thumb { background: #CBD5E0; border-radius: 10px; min-height: 32px; }
                ::-webkit-scrollbar-thumb:hover { background: #94A3B8; }
                body > :first-child { margin-top: 0; }
                body > :last-child { margin-bottom: 0; }
                h1, h2, h3, h4, h5, h6 { margin-top: 1.5em; margin-bottom: 0.5em; font-weight: 600; color: #24292e; }
                h1 { font-size: 2em; border-bottom: 2px solid #eaecef; padding-bottom: 0.3em; }
                h2 { font-size: 1.5em; border-bottom: 1px solid #eaecef; padding-bottom: 0.3em; }
                h3 { font-size: 1.25em; }
                code:not(pre code) { background-color: #f0f0f0; color: #d63384; padding: 2px 6px; border-radius: 4px; font-family: 'JetBrains Mono', 'Fira Code', 'Consolas', monospace; font-size: 0.9em; }
                pre { background-color: #f8f8f8; border: 1px solid #e1e4e8; border-radius: 6px; margin: 1em 0; overflow-x: auto; position: relative; }
                pre code { font-family: 'JetBrains Mono', 'Fira Code', 'Consolas', monospace; font-size: 13px; line-height: 1.5; padding: 16px !important; display: block; background: transparent !important; color: inherit; }
                .hljs { background: transparent !important; }
                blockquote { border-left: 4px solid #6366F1; margin: 0; padding-left: 20px; color: #57606a; background-color: #f6f8fa; padding: 10px 20px; border-radius: 4px; }
                ul, ol { margin: 1em 0; padding-left: 2em; color: #24292e; }
                li { margin: 0.5em 0; }
                table { border-collapse: collapse; width: 100%; margin: 1em 0; }
                table th, table td { border: 1px solid #e1e4e8; padding: 10px; text-align: left; }
                table th { background-color: #f6f8fa; font-weight: 600; color: #24292e; }
                table td { background-color: #FFFFFF; color: #24292e; }
                a { color: #0969da; text-decoration: none; }
                a:hover { color: #0550ae; text-decoration: underline; }
                img { max-width: 100%; height: auto; border-radius: 6px; border: 1px solid #e1e4e8; }
                img.emoji { height: 1.05em; width: auto; max-width: none; vertical-align: -0.15em; margin: 0 0.04em; border: none; border-radius: 0; display: inline; }
                .emoji-glyph { font-family: "Apple Color Emoji", "Segoe UI Emoji", "Noto Color Emoji", sans-serif; }
                hr { border: none; border-top: 1px solid #e1e4e8; margin: 2em 0; }
                strong { color: #24292e; font-weight: 600; }
                mark { background-color: #fff8c5; padding: 1px 3px; border-radius: 2px; }
                a.wikilink { color: #6366F1; text-decoration: none; border-bottom: 1px dashed #6366F1; cursor: pointer; }
                a.wikilink:hover { color: #4338CA; border-bottom-style: solid; }
                a.wikilink-new { color: #dc2626; border-bottom-color: #dc2626; }
                a.wikilink-new:hover { color: #b91c1c; border-bottom-color: #b91c1c; }
                .rich-link { margin: 1em 0; }
                a.rich-link-card { display: flex; align-items: stretch; text-decoration: none; border: 1px solid #e1e4e8; border-radius: 8px; overflow: hidden; background-color: #f6f8fa; transition: border-color 0.15s, background-color 0.15s; }
                a.rich-link-card:hover { border-color: #6366F1; background-color: #f0f1ff; text-decoration: none; }
                .rich-link-thumb { flex: 0 0 120px; background-size: cover; background-position: center; background-repeat: no-repeat; border: none; border-radius: 0; }
                .rich-link-body { display: flex; flex-direction: column; gap: 4px; padding: 12px 14px; min-width: 0; }
                .rich-link-title { color: #24292e; font-weight: 600; font-size: 0.95em; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
                .rich-link-desc { color: #57606a; font-size: 0.85em; line-height: 1.4; display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical; overflow: hidden; }
                .rich-link-site { color: #6b6e74; font-size: 0.78em; margin-top: 2px; }
                .embed { border-left: 3px solid #d0d7de; background-color: #f6f8fa; border-radius: 0 6px 6px 0; margin: 1em 0; padding: 4px 16px 8px 16px; }
                .embed-title { display: block; font-size: 0.8em; font-weight: 600; color: #6366F1; text-decoration: none; margin: 6px 0; text-transform: uppercase; letter-spacing: 0.03em; }
                .embed-title:hover { text-decoration: underline; }
                .embed-body > :first-child { margin-top: 0; }
                .embed-body > :last-child { margin-bottom: 0; }
                .embed-note { color: #d1242f; font-size: 0.8em; font-style: italic; }
                .embed-missing, .embed-cycle, .embed-too-deep { border-left-color: #d1242f; background-color: #fff0f0; }
                """;
        return base
                + readingSyntaxStyles(false)
                + (readableLineLength ? readableLineLengthRule() : "")
                + contentFontSizeRule(contentFontSize);
    }

    /**
     * Callouts, footnotes, tags, block anchors and links within the note. Callout colours
     * follow Obsidian's (RGB per family), as a tinted background and a coloured title.
     */
    private static String readingSyntaxStyles(boolean dark) {
        String[][] colours = {
                { "blue", "8, 109, 221" }, { "cyan", "0, 191, 188" }, { "green", "8, 185, 78" },
                { "orange", "236, 117, 0" }, { "red", "233, 49, 71" }, { "purple", "120, 82, 238" },
                { "gray", "158, 158, 158" } };
        StringBuilder css = new StringBuilder();
        css.append(".callout { margin: 1em 0; padding: 10px 14px; border-radius: 6px; overflow: hidden; ")
                .append("--callout-rgb: 8, 109, 221; background-color: rgba(var(--callout-rgb), ")
                .append(dark ? "0.12" : "0.08").append("); border-left: 3px solid rgb(var(--callout-rgb)); }\n");
        for (String[] colour : colours) {
            css.append(".callout-").append(colour[0]).append(" { --callout-rgb: ").append(colour[1]).append("; }\n");
        }
        css.append(".callout-title { display: flex; align-items: center; gap: 6px; font-weight: 600; ")
                .append("color: rgb(var(--callout-rgb)); }\n")
                .append("summary.callout-title { cursor: pointer; list-style: none; }\n")
                .append("summary.callout-title::-webkit-details-marker { display: none; }\n")
                .append("summary.callout-title::after { content: '\\203A'; margin-left: 4px; transition: transform 0.15s; }\n")
                .append("details.callout[open] > summary.callout-title::after { transform: rotate(90deg); }\n")
                .append(".callout-icon { display: inline-flex; }\n")
                .append(".callout-content { margin-top: 6px; }\n")
                .append(".callout-content > :first-child { margin-top: 0; }\n")
                .append(".callout-content > :last-child { margin-bottom: 0; }\n")
                .append(".footnotes { font-size: 0.9em; margin-top: 2em; color: ").append(dark ? "#B3B3B3" : "#57606a")
                .append("; }\n")
                .append(".footnotes hr { border: none; border-top: 1px solid ").append(dark ? "#3a3a3a" : "#d0d7de")
                .append("; }\n")
                .append("sup.footnote-ref a, a.footnote-backref { text-decoration: none; color: ")
                .append(dark ? "#818CF8" : "#6366F1").append("; }\n")
                .append("a.tag { text-decoration: none; padding: 1px 8px; border-radius: 10px; font-size: 0.9em; ")
                .append(dark ? "color: #A5B4FC; background-color: rgba(129, 140, 248, 0.15);"
                        : "color: #4F46E5; background-color: rgba(99, 102, 241, 0.1);")
                .append(" }\n")
                .append("a.tag:hover { text-decoration: underline; }\n")
                .append("a.wikilink-local { border-bottom-style: dotted; }\n")
                .append(".block-anchor { display: inline-block; width: 0; height: 0; }\n");
        return css.toString();
    }

    private record Injections(String head, String body) {
    }

    /** Loads the bundled emoji font as an AWT font (sized for crisp rasterisation). */
    private static Font loadEmojiAwtFont() {
        try (InputStream in = MarkdownPreview.class.getResourceAsStream(EMOJI_FONT_RESOURCE)) {
            if (in == null) {
                logger.warning("Emoji font asset not found — emojis may not render in the preview");
                return null;
            }
            return Font.createFont(Font.TRUETYPE_FONT, in).deriveFont(64f);
        } catch (Exception e) {
            logger.warning("Could not load emoji font: " + e.getMessage());
            return null;
        }
    }

    /**
     * Replaces emoji runs with inline {@code <img class="emoji">} data URIs rasterised
     * from the bundled emoji font, tinted to match the theme's text colour. Images
     * always render — in the WebView and in PDF export (openhtmltopdf) — unlike
     * {@code @font-face} emoji. Reused by {@code NoteExporter} for PDF.
     */
    public static String emojifyToImages(String bodyHtml, boolean dark) {
        if (EMOJI_AWT_FONT == null || bodyHtml == null || bodyHtml.isEmpty()) {
            return bodyHtml;
        }
        Matcher m = EMOJI_RUN.matcher(bodyHtml);
        if (!m.find()) {
            return bodyHtml;
        }
        String colorKey = dark ? "d" : "l";
        StringBuilder out = new StringBuilder(bodyHtml.length() + 256);
        m.reset();
        while (m.find()) {
            StringBuilder replacement = new StringBuilder();
            for (String emoji : emojiClusters(m.group())) {
                String dataUri = EMOJI_CACHE.computeIfAbsent(colorKey + "|" + emoji, k -> renderEmoji(emoji, dark));
                if (dataUri == null) {
                    replacement.append(emoji);
                    continue;
                }
                String escaped = numericCharacterReferences(emoji);
                replacement.append("<img class=\"emoji\" alt=\"").append(escaped).append('"');
                String kind = emojiKind(emoji);
                if (!kind.equals("sequence")) {
                    replacement.append(" data-emoji=\"").append(escaped)
                            .append("\" data-emoji-kind=\"").append(kind).append('"');
                }
                replacement.append(" src=\"").append(dataUri).append("\"/>");
            }
            m.appendReplacement(out, Matcher.quoteReplacement(replacement.toString()));
        }
        m.appendTail(out);
        return out.toString();
    }

    /**
     * Splits an emoji run into the emoji it shows: a character plus its variation selector,
     * skin tone or keycap mark, characters joined by ZWJ, and flags (pairs of regional
     * indicators). Same rules as {@code emojiClusters} in the editor's {@code emoji.js}.
     */
    static List<String> emojiClusters(String run) {
        List<String> clusters = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean joinNext = false;
        boolean openFlag = false;
        for (int i = 0; i < run.length(); ) {
            int cp = run.codePointAt(i);
            i += Character.charCount(cp);
            boolean pairsFlag = openFlag && isRegionalIndicator(cp);
            boolean extendsPrevious = cp == 0xFE0E || cp == 0xFE0F || cp == 0x20E3 || cp == 0x200D
                    || Character.isEmojiModifier(cp);
            if (!current.isEmpty() && (joinNext || extendsPrevious || pairsFlag)) {
                current.appendCodePoint(cp);
            } else {
                if (!current.isEmpty()) {
                    clusters.add(current.toString());
                }
                current = new StringBuilder().appendCodePoint(cp);
            }
            joinNext = cp == 0x200D;
            openFlag = isRegionalIndicator(cp) && !pairsFlag;
        }
        if (!current.isEmpty()) {
            clusters.add(current.toString());
        }
        return clusters;
    }

    private static boolean isRegionalIndicator(int cp) {
        return cp >= 0x1F1E6 && cp <= 0x1F1FF;
    }

    /**
     * {@code colour}: one emoji the engine can draw in colour; {@code text}: a symbol shown
     * as text by default, such as ↩ or ✔; {@code sequence}: anything it cannot draw whole.
     * Same rules as {@code emojiKind} in the editor's {@code emoji.js}.
     */
    static String emojiKind(String emoji) {
        int[] cps = emoji.codePoints().toArray();
        if (cps.length == 1) {
            return Character.isEmojiPresentation(cps[0]) ? "colour" : "text";
        }
        if (cps.length == 2 && cps[1] == 0xFE0F && Character.isEmoji(cps[0])) {
            return "colour";
        }
        return "sequence";
    }

    /**
     * Twemoji's file name for an emoji: its code points in lower-case hex joined by
     * {@code -}, without the variation selector U+FE0F unless the emoji joins characters
     * with ZWJ (U+200D). ❤️ is {@code 2764}, 👍🏽 {@code 1f44d-1f3fd}.
     */
    static String twemojiName(String emoji) {
        boolean joined = emoji.indexOf('\u200D') >= 0;
        StringBuilder name = new StringBuilder();
        emoji.codePoints().filter(cp -> joined || cp != 0xFE0F).forEach(cp -> {
            if (!name.isEmpty()) {
                name.append('-');
            }
            name.append(Integer.toHexString(cp));
        });
        return name.toString();
    }

    /** Twemoji's images, read from the bundled zip the first time an emoji is drawn. */
    private static final class Twemoji {
        static final Map<String, byte[]> IMAGES = load();

        private static Map<String, byte[]> load() {
            Map<String, byte[]> images = new HashMap<>();
            try (InputStream in = MarkdownPreview.class.getResourceAsStream(TWEMOJI_RESOURCE)) {
                if (in == null) {
                    logger.warning("Twemoji images not found — emoji fall back to monochrome");
                    return images;
                }
                try (ZipInputStream zip = new ZipInputStream(in)) {
                    for (ZipEntry entry; (entry = zip.getNextEntry()) != null; ) {
                        String name = entry.getName();
                        if (name.endsWith(".png")) {
                            images.put(name.substring(0, name.length() - 4), zip.readAllBytes());
                        }
                    }
                }
            } catch (IOException e) {
                logger.warning("Could not read the Twemoji images: " + e.getMessage());
            }
            return images;
        }
    }

    /**
     * Every character as {@code &#x…;}: the page is loaded with {@code loadContent}, which
     * garbles characters outside the BMP written as they are.
     */
    private static String numericCharacterReferences(String text) {
        StringBuilder out = new StringBuilder();
        text.codePoints().forEach(cp -> out.append("&#x").append(Integer.toHexString(cp).toUpperCase(Locale.ROOT))
                .append(';'));
        return out.toString();
    }

    /**
     * Rasterises a single emoji run to a transparent PNG data URI, for callers outside
     * the Preview pipeline (the CodeMirror editor bridge, via {@code EditorBridge}).
     * Same cache as {@link #emojifyToImages}, so a run already seen by either caller is
     * a cache hit for the other.
     *
     * @return the data URI, or {@code null} if {@code run} is not a recognised emoji
     *         run or the bundled emoji font could not be loaded
     */
    public static String rasterizeEmojiRun(String run, boolean dark) {
        if (EMOJI_AWT_FONT == null || run == null || run.isEmpty() || !EMOJI_RUN.matcher(run).matches()) {
            return null;
        }
        String colorKey = dark ? "d" : "l";
        return EMOJI_CACHE.computeIfAbsent(colorKey + "|" + run, k -> renderEmoji(run, dark));
    }

    /**
     * An emoji as a PNG data URI: its colour Twemoji image, unless it is a symbol shown as text
     * or Twemoji has none; then rasterised from the monochrome font, tinted for the theme.
     */
    private static String renderEmoji(String run, boolean dark) {
        if (!"text".equals(emojiKind(run))) {
            byte[] png = Twemoji.IMAGES.get(twemojiName(run));
            if (png != null) {
                return "data:image/png;base64," + Base64.getEncoder().encodeToString(png);
            }
        }
        try {
            BufferedImage probe = new BufferedImage(1, 1,
                    BufferedImage.TYPE_INT_ARGB);
            Graphics2D pg = probe.createGraphics();
            pg.setFont(EMOJI_AWT_FONT);
            FontMetrics fm = pg.getFontMetrics();
            int width = Math.max(1, fm.stringWidth(run));
            int ascent = fm.getAscent();
            int height = Math.max(1, ascent + fm.getDescent());
            pg.dispose();

            int pad = 4;
            BufferedImage img = new BufferedImage(
                    width + pad * 2, height + pad, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = img.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                    RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setFont(EMOJI_AWT_FONT);
            g.setColor(dark ? new Color(0xE0, 0xE0, 0xE0) : new Color(0x24, 0x29, 0x2e));
            g.drawString(run, pad, pad + ascent);
            g.dispose();

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(img, "png", baos);
            return "data:image/png;base64," + Base64.getEncoder().encodeToString(baos.toByteArray());
        } catch (Exception e) {
            logger.fine("Could not rasterise emoji: " + e.getMessage());
            return null;
        }
    }

    /** Max size of an image to inline as a data URI (skip larger to keep HTML sane). */
    private static final long MAX_INLINE_IMAGE_BYTES = 12L * 1024 * 1024;

    /**
     * Resolves an image reference for an embedded WebView editor.
     *
     * <p>Remote and data URIs are returned unchanged. Relative vault paths are
     * resolved against {@code baseDir} and converted to a data URI because pages
     * loaded with {@link javafx.scene.web.WebEngine#loadContent(String)} cannot
     * reliably fetch local files. Invalid, missing or oversized files return an
     * empty string so callers can display a non-destructive fallback.</p>
     *
     * @param source raw Markdown image source
     * @param baseDir directory of the note containing the reference
     * @return a WebView-safe URI, or an empty string when it cannot be resolved
     */
    public static String resolveImageSource(String source, Path baseDir) {
        return resolveImageSource(source, baseDir, null);
    }

    /**
     * Same as {@link #resolveImageSource(String, java.nio.file.Path)}, but a local file is
     * only inlined when it lies inside {@code containmentRoot} (normally the vault root).
     * A {@code null} root contains the lookup to {@code baseDir}. The note's Markdown picks
     * the path, so without this bound {@code ../../.ssh/id_rsa} would be read into the page.
     */
    public static String resolveImageSource(String source, Path baseDir,
            Path containmentRoot) {
        String value = source != null ? source.trim() : "";
        if (value.isEmpty()) {
            return "";
        }
        if (value.startsWith("data:") || value.startsWith("http://") || value.startsWith("https://")) {
            return value;
        }
        if (baseDir == null || value.startsWith("file:")) {
            return "";
        }
        try {
            String decoded = WikiLinkResolver.urlDecode(value);
            Path imagePath = baseDir.resolve(decoded).normalize();
            Path bound = containmentRoot != null ? containmentRoot : baseDir;
            if (!PathContainment.isInside(bound, imagePath)) {
                logger.fine("Refusing to inline image outside the vault: " + value);
                return "";
            }
            if (!Files.isRegularFile(imagePath)
                    || Files.size(imagePath) > MAX_INLINE_IMAGE_BYTES) {
                return "";
            }
            byte[] bytes = Files.readAllBytes(imagePath);
            return "data:" + guessImageMime(imagePath.getFileName().toString()) + ";base64,"
                    + Base64.getEncoder().encodeToString(bytes);
        } catch (Exception e) {
            logger.fine("Could not resolve image '" + value + "': " + e.getMessage());
            return "";
        }
    }

    /**
     * Rewrites local {@code <img src="relative">} references to base64 {@code data:}
     * URIs resolved against {@code baseDir}. Remote ({@code http(s)}), {@code data:}
     * and {@code file:} sources are left untouched.
     */
    private static String embedLocalImages(String bodyHtml, Path baseDir,
            Path containmentRoot) {
        if (baseDir == null || bodyHtml == null || !bodyHtml.contains("<img")) {
            return bodyHtml;
        }
        try {
            Document doc = Jsoup.parseBodyFragment(bodyHtml);
            boolean changed = false;
            for (Element img : doc.select("img[src]")) {
                String src = img.attr("src").trim();
                if (src.isEmpty() || src.startsWith("data:") || src.startsWith("http://")
                        || src.startsWith("https://") || src.startsWith("file:")) {
                    continue;
                }
                try {
                    String resolved = resolveImageSource(src, baseDir, containmentRoot);
                    if (resolved.isEmpty()) {
                        continue;
                    }
                    img.attr("src", resolved);
                    changed = true;
                } catch (Exception perImage) {
                    logger.fine("Could not inline image '" + src + "': " + perImage.getMessage());
                }
            }
            return changed ? doc.body().html() : bodyHtml;
        } catch (Exception e) {
            logger.warning("Image inlining failed: " + e.getMessage());
            return bodyHtml;
        }
    }

    private static String guessImageMime(String fileName) {
        String ext = AttachmentType.extensionOf(fileName);
        return switch (ext) {
            case "jpg", "jpeg" -> "image/jpeg";
            case "gif" -> "image/gif";
            case "bmp" -> "image/bmp";
            case "webp" -> "image/webp";
            case "svg" -> "image/svg+xml";
            default -> "image/png";
        };
    }

    private static String loadResourceText(String resourcePath) {
        try (InputStream in = MarkdownPreview.class.getResourceAsStream(resourcePath)) {
            if (in == null) {
                logger.warning("Preview asset not found: " + resourcePath);
                return "";
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            logger.warning("Failed to read preview asset " + resourcePath + ": " + e.getMessage());
            return "";
        }
    }

    /** Swaps emoji images back to text where the engine draws colour emoji. */
    private static String colourEmojiScript() {
        return colourEmojiOverride(System.getProperty("os.name", "")) + loadResourceText(COLOUR_EMOJI_SCRIPT_RESOURCE);
    }

    /**
     * Script that tells a page (the reading view, the editor) whether to show emoji as text.
     * Only macOS's engine has been seen to draw emoji text in colour, so it alone keeps the
     * page's own check (drawing one on a canvas). Elsewhere the answer is no, without relying on
     * a check never tried there, and every emoji stays an image, Twemoji's where it has one.
     */
    public static String colourEmojiOverride(String osName) {
        boolean mac = osName != null && osName.toLowerCase(Locale.ROOT).contains("mac");
        return mac ? "" : "window.__jylosColourEmoji = false;\n";
    }

    private static String highlightScriptBlock() {
        return """
                if (typeof hljs !== 'undefined' && hljs && hljs.highlightElement) {
                    document.querySelectorAll('pre code').forEach(function(block) {
                        hljs.highlightElement(block);
                    });
                }
                """;
    }

    private static boolean containsCodeBlocks(String html) {
        return html != null && html.contains("<pre><code");
    }

    private static String highlightJsScript() {
        return HighlightAssets.SCRIPT;
    }

    private static String highlightLightCss() {
        return HighlightAssets.LIGHT_CSS;
    }

    private static String highlightDarkCss() {
        return HighlightAssets.DARK_CSS;
    }

    /** Lazy holder: avoid loading highlight.js assets for notes without code blocks. */
    private static final class HighlightAssets {
        private static final String SCRIPT = loadResourceText(HLJS_SCRIPT_RESOURCE);
        private static final String LIGHT_CSS = loadResourceText(HLJS_LIGHT_CSS_RESOURCE);
        private static final String DARK_CSS = loadResourceText(HLJS_DARK_CSS_RESOURCE);
    }

    // ── KaTeX (offline math rendering) ───────────────────────────────────────

    private static final String KATEX_DIR = "/com/example/jylos/ui/preview/katex/";
    /** Matches a likely math delimiter so KaTeX is only injected when needed. */
    private static final Pattern MATH_HINT = Pattern.compile(
            "\\$\\$[\\s\\S]+?\\$\\$|\\$[^\\$\\n]+\\$|\\\\\\([\\s\\S]+?\\\\\\)|\\\\\\[[\\s\\S]+?\\\\\\]");

    private static boolean containsMath(String markdown) {
        return markdown != null && MATH_HINT.matcher(markdown).find();
    }

    /** Whether {@code markdown} has something that looks like math ({@code $…$}, {@code $$…$$}, {@code \(…\)}). */
    public static boolean mightContainMath(String markdown) {
        return containsMath(markdown);
    }

    /** The bundled KaTeX script, for the editor's Live Preview. */
    public static String katexScript() {
        return katexJs();
    }

    /** The bundled KaTeX stylesheet with its fonts inlined, for the editor's Live Preview. */
    public static String katexStylesheet() {
        return katexCss();
    }

    private static String katexCss() {
        return KatexAssets.CSS;
    }

    private static String katexJs() {
        return KatexAssets.JS;
    }

    /** Lazy holder: KaTeX is sizeable, so load it only when math delimiters exist. */
    private static final class KatexAssets {
        private static final String CSS = buildKatexCss();
        private static final String JS = loadResourceText(KATEX_DIR + "katex.min.js")
                + "\n"
                + loadResourceText(KATEX_DIR + "auto-render.min.js");
    }

    /** KaTeX stylesheet with its woff2 fonts inlined as data URIs (WebView is opaque-origin). */
    private static String buildKatexCss() {
        String css = loadResourceText(KATEX_DIR + "katex.min.css");
        if (css.isEmpty()) {
            return "";
        }
        // Keep only the woff2 source (drop woff/ttf alternatives we don't bundle)...
        css = css.replaceAll(",url\\(fonts/[^)]+\\.woff\\) format\\(\"woff\"\\)", "");
        css = css.replaceAll(",url\\(fonts/[^)]+\\.ttf\\) format\\(\"truetype\"\\)", "");
        // ...then inline each woff2 file as a data URI.
        Matcher m = Pattern.compile("url\\(fonts/([^)]+\\.woff2)\\)").matcher(css);
        StringBuilder out = new StringBuilder(css.length() + 350_000);
        while (m.find()) {
            String dataUri = katexFontDataUri(m.group(1));
            m.appendReplacement(out, Matcher.quoteReplacement(
                    dataUri != null ? "url(" + dataUri + ")" : m.group()));
        }
        m.appendTail(out);
        return out.toString();
    }

    private static String katexFontDataUri(String fileName) {
        try (InputStream in = MarkdownPreview.class.getResourceAsStream(KATEX_DIR + "fonts/" + fileName)) {
            if (in == null) {
                return null;
            }
            return "data:font/woff2;base64," + Base64.getEncoder().encodeToString(in.readAllBytes());
        } catch (Exception e) {
            logger.fine("Could not inline KaTeX font " + fileName + ": " + e.getMessage());
            return null;
        }
    }

    /** Script that loads KaTeX and renders $…$ / $$…$$ / \\(…\\) / \\[…\\] in the body. */
    private static String katexScriptBlock() {
        String js = katexJs();
        if (js.isBlank()) {
            return "";
        }
        return "<script>" + js + "</script>\n"
                + "<script>try{renderMathInElement(document.body,{delimiters:["
                + "{left:'$$',right:'$$',display:true},"
                + "{left:'$',right:'$',display:false},"
                + "{left:'\\\\[',right:'\\\\]',display:true},"
                + "{left:'\\\\(',right:'\\\\)',display:false}],throwOnError:false});}catch(e){}</script>";
    }
}
