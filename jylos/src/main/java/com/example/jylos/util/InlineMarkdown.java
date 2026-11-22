package com.example.jylos.util;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;

import org.commonmark.ext.gfm.strikethrough.Strikethrough;
import org.commonmark.ext.gfm.strikethrough.StrikethroughExtension;
import org.commonmark.node.Code;
import org.commonmark.node.Emphasis;
import org.commonmark.node.HardLineBreak;
import org.commonmark.node.Link;
import org.commonmark.node.Node;
import org.commonmark.node.SoftLineBreak;
import org.commonmark.node.StrongEmphasis;
import org.commonmark.node.Text;
import org.commonmark.parser.Parser;

/**
 * A line of Markdown, such as a heading's title, as the text it shows and how each piece of
 * it looks: bold, italic, code, struck through, highlighted ({@code ==text==}) or a link. The
 * marks are gone, raw HTML tags too, and a link shows what the reading view shows for it (a
 * wiki-link its display text, or the note's title and {@code › heading}). Blocks are not
 * read: the text is taken as one paragraph.
 *
 * @author Edu Díaz (RGiskard7)
 * @since 2.6.1
 */
public final class InlineMarkdown {

    private static final Parser PARSER = CalloutsAndHighlights.addTo(Parser.builder()
            .extensions(List.of(StrikethroughExtension.create())))
            .build();

    private static final String LEAD = "x ";

    private InlineMarkdown() {
    }

    public enum Style {
        BOLD, ITALIC, CODE, STRIKETHROUGH, HIGHLIGHT, LINK
    }

    /** A piece of the text that looks one way. */
    public record Run(String text, Set<Style> styles) {
        public Run {
            styles = Set.copyOf(styles);
        }
    }

    /** The pieces of {@code markdown}, in order; none for {@code null} or empty text. */
    public static List<Run> runs(String markdown) {
        List<Run> pieces = new ArrayList<>();
        if (markdown == null || markdown.isBlank()) {
            return pieces;
        }
        // Behind a word of its own the text is a paragraph: a title such as "1. Intro" or "- x"
        // is not a list here, and a mark at its start opens as it would in a heading.
        Node paragraph = PARSER.parse(LEAD + markdown.strip()).getFirstChild();
        if (paragraph != null) {
            walk(paragraph.getFirstChild(), EnumSet.noneOf(Style.class), pieces);
        }
        List<Run> merged = merge(pieces);
        if (!merged.isEmpty()) {
            Run first = merged.get(0);
            String text = first.text().substring(LEAD.length());
            if (text.isEmpty()) {
                merged.remove(0);
            } else {
                merged.set(0, new Run(text, first.styles()));
            }
        }
        List<Run> runs = new ArrayList<>();
        for (Run run : merged) {
            if (run.styles().contains(Style.CODE)) {
                runs.add(run);
            } else {
                splitWikiLinks(run, runs);
            }
        }
        return runs;
    }

    /** The text {@code markdown} shows, without its marks. */
    public static String plainText(String markdown) {
        StringBuilder text = new StringBuilder();
        runs(markdown).forEach(run -> text.append(run.text()));
        return text.toString();
    }

    private static void walk(Node first, EnumSet<Style> styles, List<Run> out) {
        for (Node node = first; node != null; node = node.getNext()) {
            if (node instanceof Text text) {
                out.add(new Run(text.getLiteral(), styles));
            } else if (node instanceof Code code) {
                out.add(new Run(code.getLiteral(), with(styles, Style.CODE)));
            } else if (node instanceof SoftLineBreak || node instanceof HardLineBreak) {
                out.add(new Run(" ", styles));
            } else if (node instanceof StrongEmphasis) {
                walk(node.getFirstChild(), with(styles, Style.BOLD), out);
            } else if (node instanceof Emphasis) {
                walk(node.getFirstChild(), with(styles, Style.ITALIC), out);
            } else if (node instanceof Strikethrough) {
                walk(node.getFirstChild(), with(styles, Style.STRIKETHROUGH), out);
            } else if (node instanceof CalloutsAndHighlights.Highlight) {
                walk(node.getFirstChild(), with(styles, Style.HIGHLIGHT), out);
            } else if (node instanceof Link) {
                walk(node.getFirstChild(), with(styles, Style.LINK), out);
            } else {
                // Anything else (a raw HTML tag has nothing inside: what it wraps comes as text).
                walk(node.getFirstChild(), styles, out);
            }
        }
    }

    private static EnumSet<Style> with(EnumSet<Style> styles, Style added) {
        EnumSet<Style> copy = EnumSet.copyOf(styles);
        copy.add(added);
        return copy;
    }

    /** Pieces next to each other that look the same, as one (the parser cuts text at {@code [}). */
    private static List<Run> merge(List<Run> pieces) {
        List<Run> merged = new ArrayList<>();
        for (Run piece : pieces) {
            int last = merged.size() - 1;
            if (last >= 0 && merged.get(last).styles().equals(piece.styles())) {
                merged.set(last, new Run(merged.get(last).text() + piece.text(), piece.styles()));
            } else if (!piece.text().isEmpty()) {
                merged.add(piece);
            }
        }
        return merged;
    }

    /** A {@code [[link]]} in the text becomes a link piece showing its label. */
    private static void splitWikiLinks(Run run, List<Run> out) {
        Matcher m = WikiLinkResolver.WIKI_LINK.matcher(run.text());
        int from = 0;
        while (m.find()) {
            String path = WikiLinkResolver.internalLinkPath(m.group(1).trim());
            if (path == null) {
                continue;
            }
            if (m.start() > from) {
                out.add(new Run(run.text().substring(from, m.start()), run.styles()));
            }
            String heading = m.group(2) != null ? m.group(2).trim() : null;
            String alias = m.group(3) != null ? m.group(3).trim() : null;
            EnumSet<Style> styles = run.styles().isEmpty() ? EnumSet.noneOf(Style.class) : EnumSet.copyOf(run.styles());
            styles.add(Style.LINK);
            out.add(new Run(WikiLinkResolver.wikiLinkLabel(path, heading, alias), styles));
            from = m.end();
        }
        if (from < run.text().length()) {
            out.add(new Run(run.text().substring(from), run.styles()));
        }
    }
}
