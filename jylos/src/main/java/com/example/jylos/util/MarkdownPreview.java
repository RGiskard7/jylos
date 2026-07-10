package com.example.jylos.util;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Set;
import java.util.function.Function;
import java.util.logging.Logger;

import com.example.jylos.config.LoggerConfig;
import com.example.jylos.plugin.PreviewEnhancer;
import com.example.jylos.service.NoteTitleIndex;

/**
 * Builds preview HTML for notes with safe enhancer handling.
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
     * Emoji rendering: JavaFX's WebKit renders neither the OS colour-emoji fonts nor a
     * bundled {@code @font-face} font for supplementary-plane emoji (they show as tofu).
     * Java2D (AWT) <em>can</em> rasterise them from the bundled monochrome Noto Emoji
     * font, so we replace emoji characters with inline {@code <img>} data URIs, which
     * always render. Rendered emoji are cached per (run, colour).
     */
    private static final String EMOJI_FONT_RESOURCE =
            "/com/example/jylos/ui/preview/fonts/NotoEmoji-Regular.ttf";
    private static final java.awt.Font EMOJI_AWT_FONT = loadEmojiAwtFont();
    private static final java.util.Map<String, String> EMOJI_CACHE = new java.util.concurrent.ConcurrentHashMap<>();
    private static final java.util.regex.Pattern EMOJI_RUN = java.util.regex.Pattern.compile(
            "[\\x{1F000}-\\x{1FAFF}\\x{2600}-\\x{27BF}\\x{2B00}-\\x{2BFF}\\x{2300}-\\x{23FF}"
            + "\\x{2190}-\\x{21FF}\\x{FE00}-\\x{FE0F}\\x{20E3}\\x{200D}\\x{1F1E6}-\\x{1F1FF}\\x{1F3FB}-\\x{1F3FF}]+");

    public static String buildPreviewHtml(String markdownContent, boolean isDarkTheme, Collection<PreviewEnhancer> enhancers) {
        return buildPreviewHtml(markdownContent, isDarkTheme, enhancers, null, null);
    }

    /**
     * Builds preview HTML, additionally embedding local images referenced by the note
     * (relative {@code ![](img.png)} paths) as {@code data:} URIs resolved against
     * {@code baseDir}. This is required because the {@code WebView} loads content with
     * an opaque origin and cannot fetch {@code file://} resources; it also makes the
     * exported HTML self-contained.
     *
     * @param baseDir folder to resolve relative image paths against, or {@code null}
     */
    public static String buildPreviewHtml(String markdownContent, boolean isDarkTheme,
            Collection<PreviewEnhancer> enhancers, java.nio.file.Path baseDir) {
        return buildPreviewHtml(markdownContent, isDarkTheme, enhancers, baseDir, null);
    }

    public static String buildPreviewHtml(String markdownContent, boolean isDarkTheme,
            Collection<PreviewEnhancer> enhancers, java.nio.file.Path baseDir,
            Function<String, String> embeddedContentResolver) {
        String raw = markdownContent != null ? markdownContent : "";

        // Resolve [[WikiLinks]] in the raw Markdown source FIRST, then pass
        // through CommonMark.  Because escapeHtml is enabled in
        // MarkdownProcessor, the injected <a> tags would be escaped.
        // Solution: resolve WikiLinks as Markdown link syntax instead.
        //
        // The set of known titles comes from NoteTitleIndex, a warm cache invalidated
        // by note events. This avoids re-scanning the whole note store on every
        // keystroke-driven render (perf P1).
        Set<String> knownTitles = NoteTitleIndex.getInstance().titles();

        // Expand ![[transclusions]] FIRST: ![[X]] contains a [[X]] that WikiLinkResolver
        // would otherwise rewrite. Each embed is rendered now and parked behind a token,
        // re-injected after CommonMark (see Transclusion).
        Transclusion.Result embeds = Transclusion.protect(raw,
                title -> embedContentByTitle(title, embeddedContentResolver), knownTitles);
        raw = embeds.markdown();

        raw = WikiLinkResolver.resolve(raw, knownTitles);

        // Expand ::: rich-link blocks into HTML cards before CommonMark (the card is
        // an HTML block, which CommonMark passes through verbatim).
        raw = RichLinks.render(raw);

        String html = MarkdownProcessor.markdownToHtml(raw);
        html = Transclusion.restore(html, embeds.embeds());
        html = embedLocalImages(html, baseDir);
        html = emojifyToImages(html, isDarkTheme);
        Injections injections = MarkdownPreview.collectInjections(enhancers);
        boolean codeBlocks = containsCodeBlocks(html);
        String highlightCss = codeBlocks
                ? (isDarkTheme ? highlightDarkCss() : highlightLightCss())
                : "";
        String highlightAsset = codeBlocks ? highlightJsScript() : "";

        String styleBlock = isDarkTheme ? MarkdownPreview.darkStyles() : MarkdownPreview.lightStyles();
        String highlightScript = codeBlocks ? MarkdownPreview.highlightScriptBlock() : "";

        // KaTeX math (offline) — only when the note actually contains math delimiters.
        boolean math = containsMath(raw);
        if (math) {
            styleBlock = styleBlock + "\n" + katexCss();
        }
        String mathScript = math ? katexScriptBlock() : "";

        return """
                <!DOCTYPE html>
                <html>
                <head>
                    <meta charset="UTF-8">
                    <meta http-equiv="Content-Type" content="text/html; charset=UTF-8">
                    %s
                    <style>
                        %s
                        %s
                    </style>
                </head>
                <body>
                %s
                <script>
                    %s
                    %s
                    
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
                            if (window.javaApp) {
                                window.javaApp.openNote(title);
                            }
                        } else if (href.startsWith('http://') || href.startsWith('https://')) {
                            // Open external links (incl. rich-link cards) in the system browser
                            // instead of navigating the preview away from the note.
                            e.preventDefault();
                            if (window.javaApp) {
                                window.javaApp.openExternal(href);
                            }
                        }
                    });
                </script>
                %s
                %s
                </body>
                </html>
                """.formatted(injections.head(), highlightCss, styleBlock, html, highlightAsset,
                highlightScript, mathScript, injections.body());
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
        String fg = isDarkTheme ? "#B3B3B3" : "#71717A";
        String bg = isDarkTheme ? "#1E1E1E" : "#FFFFFF";
        return """
                <!DOCTYPE html>
                <html>
                <head>
                    <meta charset="UTF-8">
                    <meta http-equiv="Content-Type" content="text/html; charset=UTF-8">
                    <style>
                        html, body { color: %s; background-color: %s; margin: 0; padding: 0; width: 100%%; height: 100%%; }
                        body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif; padding: 20px; }
                    </style>
                </head>
                <body></body>
                </html>
                """.formatted(fg, bg);
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

    private static String darkStyles() {
        return """
                html { background-color: #1E1E1E; margin: 0; padding: 0; }
                body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, 'Helvetica Neue', Arial, sans-serif; padding: 20px; line-height: 1.6; color: #E0E0E0; background-color: #1E1E1E; -webkit-font-smoothing: antialiased; text-rendering: optimizeLegibility; overflow-x: hidden; }
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
    }

    private static String lightStyles() {
        return """
                html { background-color: #FFFFFF; margin: 0; padding: 0; }
                body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, 'Helvetica Neue', Arial, sans-serif; padding: 20px; line-height: 1.6; color: #24292e; background-color: #FFFFFF; -webkit-font-smoothing: antialiased; text-rendering: optimizeLegibility; overflow-x: hidden; }
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
    }

    private record Injections(String head, String body) {
    }

    /** Loads the bundled emoji font as an AWT font (sized for crisp rasterisation). */
    private static java.awt.Font loadEmojiAwtFont() {
        try (InputStream in = MarkdownPreview.class.getResourceAsStream(EMOJI_FONT_RESOURCE)) {
            if (in == null) {
                logger.warning("Emoji font asset not found — emojis may not render in the preview");
                return null;
            }
            return java.awt.Font.createFont(java.awt.Font.TRUETYPE_FONT, in).deriveFont(64f);
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
        java.util.regex.Matcher m = EMOJI_RUN.matcher(bodyHtml);
        if (!m.find()) {
            return bodyHtml;
        }
        String colorKey = dark ? "d" : "l";
        StringBuilder out = new StringBuilder(bodyHtml.length() + 256);
        m.reset();
        while (m.find()) {
            String run = m.group();
            String dataUri = EMOJI_CACHE.computeIfAbsent(colorKey + "|" + run, k -> renderEmoji(run, dark));
            String replacement = dataUri == null ? run
                    : "<img class=\"emoji\" alt=\"emoji\" src=\"" + dataUri + "\"/>";
            m.appendReplacement(out, java.util.regex.Matcher.quoteReplacement(replacement));
        }
        m.appendTail(out);
        return out.toString();
    }

    /** Rasterises an emoji run to a transparent PNG data URI, tinted for the theme. */
    private static String renderEmoji(String run, boolean dark) {
        try {
            java.awt.image.BufferedImage probe = new java.awt.image.BufferedImage(1, 1,
                    java.awt.image.BufferedImage.TYPE_INT_ARGB);
            java.awt.Graphics2D pg = probe.createGraphics();
            pg.setFont(EMOJI_AWT_FONT);
            java.awt.FontMetrics fm = pg.getFontMetrics();
            int width = Math.max(1, fm.stringWidth(run));
            int ascent = fm.getAscent();
            int height = Math.max(1, ascent + fm.getDescent());
            pg.dispose();

            int pad = 4;
            java.awt.image.BufferedImage img = new java.awt.image.BufferedImage(
                    width + pad * 2, height + pad, java.awt.image.BufferedImage.TYPE_INT_ARGB);
            java.awt.Graphics2D g = img.createGraphics();
            g.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING,
                    java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(java.awt.RenderingHints.KEY_TEXT_ANTIALIASING,
                    java.awt.RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setFont(EMOJI_AWT_FONT);
            g.setColor(dark ? new java.awt.Color(0xE0, 0xE0, 0xE0) : new java.awt.Color(0x24, 0x29, 0x2e));
            g.drawString(run, pad, pad + ascent);
            g.dispose();

            java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
            javax.imageio.ImageIO.write(img, "png", baos);
            return "data:image/png;base64," + java.util.Base64.getEncoder().encodeToString(baos.toByteArray());
        } catch (Exception e) {
            logger.fine("Could not rasterise emoji: " + e.getMessage());
            return null;
        }
    }

    /** Max size of an image to inline as a data URI (skip larger to keep HTML sane). */
    private static final long MAX_INLINE_IMAGE_BYTES = 12L * 1024 * 1024;

    /**
     * Rewrites local {@code <img src="relative">} references to base64 {@code data:}
     * URIs resolved against {@code baseDir}. Remote ({@code http(s)}), {@code data:}
     * and {@code file:} sources are left untouched.
     */
    private static String embedLocalImages(String bodyHtml, java.nio.file.Path baseDir) {
        if (baseDir == null || bodyHtml == null || !bodyHtml.contains("<img")) {
            return bodyHtml;
        }
        try {
            org.jsoup.nodes.Document doc = org.jsoup.Jsoup.parseBodyFragment(bodyHtml);
            boolean changed = false;
            for (org.jsoup.nodes.Element img : doc.select("img[src]")) {
                String src = img.attr("src").trim();
                if (src.isEmpty() || src.startsWith("data:") || src.startsWith("http://")
                        || src.startsWith("https://") || src.startsWith("file:")) {
                    continue;
                }
                try {
                    String decoded = java.net.URLDecoder.decode(src, StandardCharsets.UTF_8);
                    java.nio.file.Path imgPath = baseDir.resolve(decoded).normalize();
                    if (!java.nio.file.Files.isRegularFile(imgPath)
                            || java.nio.file.Files.size(imgPath) > MAX_INLINE_IMAGE_BYTES) {
                        continue;
                    }
                    byte[] bytes = java.nio.file.Files.readAllBytes(imgPath);
                    String mime = guessImageMime(imgPath.getFileName().toString());
                    img.attr("src", "data:" + mime + ";base64,"
                            + java.util.Base64.getEncoder().encodeToString(bytes));
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

    // ── KaTeX (offline math rendering) ──────────────────────────────────────────

    private static final String KATEX_DIR = "/com/example/jylos/ui/preview/katex/";
    /** Matches a likely math delimiter so KaTeX is only injected when needed. */
    private static final java.util.regex.Pattern MATH_HINT = java.util.regex.Pattern.compile(
            "\\$\\$[\\s\\S]+?\\$\\$|\\$[^\\$\\n]+\\$|\\\\\\([\\s\\S]+?\\\\\\)|\\\\\\[[\\s\\S]+?\\\\\\]");

    private static boolean containsMath(String markdown) {
        return markdown != null && MATH_HINT.matcher(markdown).find();
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
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("url\\(fonts/([^)]+\\.woff2)\\)").matcher(css);
        StringBuilder out = new StringBuilder(css.length() + 350_000);
        while (m.find()) {
            String dataUri = katexFontDataUri(m.group(1));
            m.appendReplacement(out, java.util.regex.Matcher.quoteReplacement(
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
            return "data:font/woff2;base64," + java.util.Base64.getEncoder().encodeToString(in.readAllBytes());
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
