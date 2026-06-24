package com.example.jylos.util;

import org.commonmark.node.BlockQuote;
import org.commonmark.node.BulletList;
import org.commonmark.node.Code;
import org.commonmark.node.Emphasis;
import org.commonmark.node.FencedCodeBlock;
import org.commonmark.node.HardLineBreak;
import org.commonmark.node.Heading;
import org.commonmark.node.IndentedCodeBlock;
import org.commonmark.node.Link;
import org.commonmark.node.ListItem;
import org.commonmark.node.Node;
import org.commonmark.node.OrderedList;
import org.commonmark.node.Paragraph;
import org.commonmark.node.SoftLineBreak;
import org.commonmark.node.StrongEmphasis;
import org.commonmark.node.Text;
import org.commonmark.parser.Parser;

import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.scene.text.FontPosture;
import javafx.scene.text.FontWeight;
import javafx.scene.text.TextFlow;

/**
 * A tiny Markdown renderer that turns text into laid-out, <em>styled</em> JavaFX nodes
 * (headings, bold/italic, inline code, lists, quotes) — a lightweight "reading view".
 *
 * <p>It exists for places where a {@link javafx.scene.web.WebView} is the wrong tool —
 * notably the file/text nodes of the canvas viewer, which live on a zoom/pan surface
 * where heavyweight WebViews compose poorly. It reuses the bundled CommonMark parser
 * and walks the AST into {@link TextFlow}s, so there is no second Markdown
 * implementation.</p>
 *
 * @author Edu Díaz (RGiskard7)
 * @since 2.2.0
 */
public final class MarkdownMini {

    private static final Parser PARSER = Parser.builder().build();
    private static final double BASE_FONT = 13;

    private MarkdownMini() {
    }

    /** Renders {@code markdown} into a vertical stack of styled blocks. */
    public static Region render(String markdown) {
        VBox container = new VBox(6);
        container.getStyleClass().add("md-mini");
        if (markdown == null || markdown.isBlank()) {
            return container;
        }
        Node doc = PARSER.parse(markdown);
        for (Node block = doc.getFirstChild(); block != null; block = block.getNext()) {
            javafx.scene.Node rendered = renderBlock(block);
            if (rendered != null) {
                container.getChildren().add(rendered);
            }
        }
        return container;
    }

    private static javafx.scene.Node renderBlock(Node block) {
        if (block instanceof Heading h) {
            TextFlow flow = new TextFlow();
            flow.getStyleClass().add("md-mini-heading");
            double size = switch (h.getLevel()) {
                case 1 -> BASE_FONT + 7;
                case 2 -> BASE_FONT + 4;
                case 3 -> BASE_FONT + 2;
                default -> BASE_FONT + 1;
            };
            appendInlines(flow, h.getFirstChild(), FontWeight.BOLD, FontPosture.REGULAR, size, false);
            return flow;
        }
        if (block instanceof Paragraph p) {
            TextFlow flow = new TextFlow();
            flow.getStyleClass().add("md-mini-paragraph");
            appendInlines(flow, p.getFirstChild(), FontWeight.NORMAL, FontPosture.REGULAR, BASE_FONT, false);
            return flow;
        }
        if (block instanceof BulletList || block instanceof OrderedList) {
            return renderList(block);
        }
        if (block instanceof FencedCodeBlock fc) {
            return codeBlock(fc.getLiteral());
        }
        if (block instanceof IndentedCodeBlock ic) {
            return codeBlock(ic.getLiteral());
        }
        if (block instanceof BlockQuote) {
            VBox quote = new VBox(4);
            quote.getStyleClass().add("md-mini-quote");
            for (Node child = block.getFirstChild(); child != null; child = child.getNext()) {
                javafx.scene.Node r = renderBlock(child);
                if (r != null) {
                    quote.getChildren().add(r);
                }
            }
            return quote;
        }
        // Unknown / unsupported block: render its inline text if any.
        TextFlow flow = new TextFlow();
        appendInlines(flow, block.getFirstChild(), FontWeight.NORMAL, FontPosture.REGULAR, BASE_FONT, false);
        return flow.getChildren().isEmpty() ? null : flow;
    }

    private static javafx.scene.Node renderList(Node list) {
        VBox box = new VBox(2);
        box.getStyleClass().add("md-mini-list");
        boolean ordered = list instanceof OrderedList;
        int index = 1;
        for (Node item = list.getFirstChild(); item != null; item = item.getNext()) {
            if (!(item instanceof ListItem)) {
                continue;
            }
            TextFlow flow = new TextFlow();
            flow.getStyleClass().add("md-mini-paragraph");
            String marker = ordered ? (index++ + ". ") : "•  ";
            flow.getChildren().add(styledText(marker, FontWeight.NORMAL, FontPosture.REGULAR, BASE_FONT, false));
            // A list item wraps its content in paragraphs; flatten their inlines.
            for (Node child = item.getFirstChild(); child != null; child = child.getNext()) {
                appendInlines(flow, child.getFirstChild(), FontWeight.NORMAL, FontPosture.REGULAR, BASE_FONT, false);
            }
            box.getChildren().add(flow);
        }
        return box;
    }

    private static javafx.scene.Node codeBlock(String literal) {
        javafx.scene.text.Text text = new javafx.scene.text.Text(literal == null ? "" : literal.stripTrailing());
        text.getStyleClass().add("md-mini-code");
        text.setFont(Font.font("monospace", BASE_FONT - 1));
        TextFlow flow = new TextFlow(text);
        flow.getStyleClass().add("md-mini-codeblock");
        return flow;
    }

    /** Walks inline nodes, appending styled {@link javafx.scene.text.Text} runs to {@code flow}. */
    private static void appendInlines(TextFlow flow, Node first, FontWeight weight, FontPosture posture,
            double size, boolean mono) {
        for (Node n = first; n != null; n = n.getNext()) {
            if (n instanceof Text t) {
                flow.getChildren().add(styledText(t.getLiteral(), weight, posture, size, mono));
            } else if (n instanceof StrongEmphasis) {
                appendInlines(flow, n.getFirstChild(), FontWeight.BOLD, posture, size, mono);
            } else if (n instanceof Emphasis) {
                appendInlines(flow, n.getFirstChild(), weight, FontPosture.ITALIC, size, mono);
            } else if (n instanceof Code c) {
                javafx.scene.text.Text run = styledText(c.getLiteral(), weight, posture, size, true);
                run.getStyleClass().add("md-mini-inline-code");
                flow.getChildren().add(run);
            } else if (n instanceof Link link) {
                javafx.scene.text.Text run = styledText(linkText(link), weight, posture, size, mono);
                run.getStyleClass().add("md-mini-link");
                flow.getChildren().add(run);
            } else if (n instanceof SoftLineBreak || n instanceof HardLineBreak) {
                flow.getChildren().add(styledText("\n", weight, posture, size, mono));
            } else if (n.getFirstChild() != null) {
                appendInlines(flow, n.getFirstChild(), weight, posture, size, mono);
            }
        }
    }

    private static String linkText(Link link) {
        StringBuilder sb = new StringBuilder();
        for (Node n = link.getFirstChild(); n != null; n = n.getNext()) {
            if (n instanceof Text t) {
                sb.append(t.getLiteral());
            }
        }
        return sb.length() > 0 ? sb.toString() : link.getDestination();
    }

    private static javafx.scene.text.Text styledText(String content, FontWeight weight, FontPosture posture,
            double size, boolean mono) {
        javafx.scene.text.Text text = new javafx.scene.text.Text(content);
        text.getStyleClass().add("md-mini-run");
        text.setFont(mono ? Font.font("monospace", weight, posture, size)
                : Font.font("System", weight, posture, size));
        return text;
    }

}
