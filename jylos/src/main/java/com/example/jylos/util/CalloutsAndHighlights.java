package com.example.jylos.util;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.commonmark.node.AbstractVisitor;
import org.commonmark.node.BlockQuote;
import org.commonmark.node.CustomBlock;
import org.commonmark.node.CustomNode;
import org.commonmark.node.HardLineBreak;
import org.commonmark.node.Node;
import org.commonmark.node.Nodes;
import org.commonmark.node.Paragraph;
import org.commonmark.node.SoftLineBreak;
import org.commonmark.node.SourceSpans;
import org.commonmark.node.Text;
import org.commonmark.parser.Parser;
import org.commonmark.parser.delimiter.DelimiterProcessor;
import org.commonmark.parser.delimiter.DelimiterRun;
import org.commonmark.renderer.NodeRenderer;
import org.commonmark.renderer.html.HtmlNodeRendererContext;
import org.commonmark.renderer.html.HtmlRenderer;
import org.commonmark.renderer.html.HtmlWriter;

/**
 * Two pieces of Obsidian syntax handled inside CommonMark, so code spans and code blocks are
 * left alone for free and every render (preview, embeds, export) gets them:
 * <ul>
 *   <li>{@code ==text==} renders as {@code <mark>}.</li>
 *   <li>A quote whose first line is {@code [!type] Title} is a <em>callout</em>: a coloured
 *       box with an icon and a title. {@code [!type]-} starts folded and {@code [!type]+}
 *       foldable but open; both use {@code <details>}, so folding needs no script. Aliases
 *       of a type ({@code faq} for {@code question}…) share its colour and icon; an unknown
 *       type looks like a note.</li>
 * </ul>
 */
public final class CalloutsAndHighlights {

    private CalloutsAndHighlights() {
    }

    /** {@code [!type]} plus an optional fold sign, then the title. */
    private static final Pattern CALLOUT_HEADER = Pattern.compile("^\\[!([\\p{L}\\p{N}_-]+)\\]([+-]?)[ \\t]*");

    /** One look per canonical type: colour class and icon. */
    private record Look(String colour, String icon) {
    }

    private static final Map<String, Look> LOOKS = Map.ofEntries(
            Map.entry("note", new Look("blue", "<path d=\"M12 20h9\"/><path d=\"M16.5 3.5a2.121 2.121 0 0 1 3 3L7 19l-4 1 1-4L16.5 3.5z\"/>")),
            Map.entry("abstract", new Look("cyan", "<path d=\"M16 4h2a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2H6a2 2 0 0 1-2-2V6a2 2 0 0 1 2-2h2\"/><rect x=\"8\" y=\"2\" width=\"8\" height=\"4\" rx=\"1\" ry=\"1\"/>")),
            Map.entry("info", new Look("blue", "<circle cx=\"12\" cy=\"12\" r=\"10\"/><line x1=\"12\" y1=\"16\" x2=\"12\" y2=\"12\"/><line x1=\"12\" y1=\"8\" x2=\"12.01\" y2=\"8\"/>")),
            Map.entry("todo", new Look("blue", "<path d=\"M22 11.08V12a10 10 0 1 1-5.93-9.14\"/><polyline points=\"22 4 12 14.01 9 11.01\"/>")),
            Map.entry("tip", new Look("cyan", "<polygon points=\"13 2 3 14 12 14 11 22 21 10 12 10 13 2\"/>")),
            Map.entry("success", new Look("green", "<polyline points=\"20 6 9 17 4 12\"/>")),
            Map.entry("question", new Look("orange", "<circle cx=\"12\" cy=\"12\" r=\"10\"/><path d=\"M9.09 9a3 3 0 0 1 5.83 1c0 2-3 3-3 3\"/><line x1=\"12\" y1=\"17\" x2=\"12.01\" y2=\"17\"/>")),
            Map.entry("warning", new Look("orange", "<path d=\"M10.29 3.86L1.82 18a2 2 0 0 0 1.71 3h16.94a2 2 0 0 0 1.71-3L13.71 3.86a2 2 0 0 0-3.42 0z\"/><line x1=\"12\" y1=\"9\" x2=\"12\" y2=\"13\"/><line x1=\"12\" y1=\"17\" x2=\"12.01\" y2=\"17\"/>")),
            Map.entry("failure", new Look("red", "<line x1=\"18\" y1=\"6\" x2=\"6\" y2=\"18\"/><line x1=\"6\" y1=\"6\" x2=\"18\" y2=\"18\"/>")),
            Map.entry("danger", new Look("red", "<polygon points=\"7.86 2 16.14 2 22 7.86 22 16.14 16.14 22 7.86 22 2 16.14 2 7.86 7.86 2\"/><line x1=\"12\" y1=\"8\" x2=\"12\" y2=\"12\"/><line x1=\"12\" y1=\"16\" x2=\"12.01\" y2=\"16\"/>")),
            Map.entry("bug", new Look("red", "<circle cx=\"12\" cy=\"12\" r=\"10\"/><line x1=\"12\" y1=\"8\" x2=\"12\" y2=\"12\"/><line x1=\"12\" y1=\"16\" x2=\"12.01\" y2=\"16\"/>")),
            Map.entry("example", new Look("purple", "<line x1=\"8\" y1=\"6\" x2=\"21\" y2=\"6\"/><line x1=\"8\" y1=\"12\" x2=\"21\" y2=\"12\"/><line x1=\"8\" y1=\"18\" x2=\"21\" y2=\"18\"/><line x1=\"3\" y1=\"6\" x2=\"3.01\" y2=\"6\"/><line x1=\"3\" y1=\"12\" x2=\"3.01\" y2=\"12\"/><line x1=\"3\" y1=\"18\" x2=\"3.01\" y2=\"18\"/>")),
            Map.entry("quote", new Look("gray", "<path d=\"M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z\"/>")));

    /** Obsidian's alternative names for the canonical types. */
    private static final Map<String, String> ALIASES = Map.ofEntries(
            Map.entry("summary", "abstract"), Map.entry("tldr", "abstract"),
            Map.entry("hint", "tip"), Map.entry("important", "tip"),
            Map.entry("check", "success"), Map.entry("done", "success"),
            Map.entry("help", "question"), Map.entry("faq", "question"),
            Map.entry("caution", "warning"), Map.entry("attention", "warning"),
            Map.entry("fail", "failure"), Map.entry("missing", "failure"),
            Map.entry("error", "danger"), Map.entry("cite", "quote"));

    /** Adds {@code ==highlight==} and callouts to a parser. */
    public static Parser.Builder addTo(Parser.Builder parser) {
        return parser.customDelimiterProcessor(new HighlightDelimiterProcessor())
                .postProcessor(CalloutsAndHighlights::toCallouts);
    }

    /** Adds the HTML for highlights and callouts to a renderer. */
    public static HtmlRenderer.Builder addTo(HtmlRenderer.Builder renderer) {
        return renderer.nodeRendererFactory(HtmlNodes::new);
    }

    // ── highlight ──────────────────────────────────────────────────────────

    /** {@code ==text==}. */
    static final class Highlight extends CustomNode {
    }

    private static final class HighlightDelimiterProcessor implements DelimiterProcessor {
        @Override
        public char getOpeningCharacter() {
            return '=';
        }

        @Override
        public char getClosingCharacter() {
            return '=';
        }

        @Override
        public int getMinLength() {
            return 2;
        }

        @Override
        public int process(DelimiterRun openingRun, DelimiterRun closingRun) {
            if (openingRun.length() < 2 || closingRun.length() < 2) {
                return 0;
            }
            Text opener = openingRun.getOpener();
            Highlight highlight = new Highlight();
            SourceSpans spans = new SourceSpans();
            spans.addAllFrom(openingRun.getOpeners(2));
            for (Node node : Nodes.between(opener, closingRun.getCloser())) {
                highlight.appendChild(node);
                spans.addAll(node.getSourceSpans());
            }
            spans.addAllFrom(closingRun.getClosers(2));
            highlight.setSourceSpans(spans.getSourceSpans());
            opener.insertAfter(highlight);
            return 2;
        }
    }

    // ── callouts ───────────────────────────────────────────────────────────

    /** A callout box: its first child is the {@link CalloutTitle}, the rest is the content. */
    static final class Callout extends CustomBlock {
        final String type;
        final String fold;

        Callout(String type, String fold) {
            this.type = type;
            this.fold = fold;
        }
    }

    /** The inline content of a callout's title. */
    static final class CalloutTitle extends CustomBlock {
    }

    /** Turns every quote that starts with {@code [!type]} into a {@link Callout}. */
    private static Node toCallouts(Node document) {
        List<BlockQuote> quotes = new ArrayList<>();
        document.accept(new AbstractVisitor() {
            @Override
            public void visit(BlockQuote blockQuote) {
                quotes.add(blockQuote);
                visitChildren(blockQuote);
            }
        });
        for (BlockQuote quote : quotes) {
            toCallout(quote);
        }
        return document;
    }

    private static void toCallout(BlockQuote quote) {
        if (!(quote.getFirstChild() instanceof Paragraph first) || !(first.getFirstChild() instanceof Text text)) {
            return;
        }
        Matcher header = CALLOUT_HEADER.matcher(text.getLiteral());
        if (!header.find()) {
            return;
        }
        Callout callout = new Callout(header.group(1).toLowerCase(Locale.ROOT), header.group(2));
        CalloutTitle title = new CalloutTitle();
        callout.appendChild(title);

        String rest = text.getLiteral().substring(header.end());
        if (rest.isEmpty()) {
            text.unlink();
        } else {
            text.setLiteral(rest);
        }
        // The title is the rest of the first line; what follows the line break is content.
        Node node = first.getFirstChild();
        while (node != null && !(node instanceof SoftLineBreak) && !(node instanceof HardLineBreak)) {
            Node next = node.getNext();
            title.appendChild(node);
            node = next;
        }
        if (node != null) {
            node.unlink();
        }
        if (first.getFirstChild() == null) {
            first.unlink();
        }
        Node child = quote.getFirstChild();
        while (child != null) {
            Node next = child.getNext();
            callout.appendChild(child);
            child = next;
        }
        quote.insertBefore(callout);
        quote.unlink();
    }

    private static Look lookOf(String type) {
        Look look = LOOKS.get(ALIASES.getOrDefault(type, type));
        return look != null ? look : LOOKS.get("note");
    }

    private static String defaultTitle(String type) {
        return type.isEmpty() ? "" : Character.toUpperCase(type.charAt(0)) + type.substring(1);
    }

    private static final class HtmlNodes implements NodeRenderer {
        private final HtmlNodeRendererContext context;
        private final HtmlWriter html;

        HtmlNodes(HtmlNodeRendererContext context) {
            this.context = context;
            this.html = context.getWriter();
        }

        @Override
        public Set<Class<? extends Node>> getNodeTypes() {
            return Set.of(Highlight.class, Callout.class, CalloutTitle.class);
        }

        @Override
        public void render(Node node) {
            if (node instanceof Highlight) {
                html.raw("<mark>");
                renderChildren(node);
                html.raw("</mark>");
            } else if (node instanceof Callout callout) {
                renderCallout(callout);
            }
        }

        private void renderCallout(Callout callout) {
            boolean foldable = !callout.fold.isEmpty();
            String box = foldable ? "details" : "div";
            Look look = lookOf(callout.type);
            html.line();
            html.raw("<" + box + " class=\"callout callout-" + look.colour() + "\" data-callout=\""
                    + MarkdownProcessor.escapeHtml(callout.type) + "\"" + ("+".equals(callout.fold) ? " open" : "") + ">");
            html.line();
            html.raw("<" + (foldable ? "summary" : "div") + " class=\"callout-title\">");
            html.raw("<span class=\"callout-icon\"><svg viewBox=\"0 0 24 24\" width=\"16\" height=\"16\" fill=\"none\" "
                    + "stroke=\"currentColor\" stroke-width=\"2\" stroke-linecap=\"round\" stroke-linejoin=\"round\">"
                    + look.icon() + "</svg></span>");
            html.raw("<span class=\"callout-title-inner\">");
            Node title = callout.getFirstChild();
            if (title instanceof CalloutTitle && title.getFirstChild() != null) {
                renderChildren(title);
            } else {
                html.text(defaultTitle(callout.type));
            }
            html.raw("</span>");
            html.raw("</" + (foldable ? "summary" : "div") + ">");
            html.line();
            Node content = title instanceof CalloutTitle ? title.getNext() : title;
            if (content != null) {
                html.raw("<div class=\"callout-content\">");
                html.line();
                for (Node child = content; child != null; child = child.getNext()) {
                    context.render(child);
                }
                html.raw("</div>");
                html.line();
            }
            html.raw("</" + box + ">");
            html.line();
        }

        private void renderChildren(Node parent) {
            Node child = parent.getFirstChild();
            while (child != null) {
                Node next = child.getNext();
                context.render(child);
                child = next;
            }
        }

    }
}
