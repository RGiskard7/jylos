package com.example.jylos.tests;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.example.jylos.util.MarkdownPreview;

/**
 * The reading view renders Obsidian syntax end to end: callouts, highlights, hidden comments,
 * footnotes listed at the end, block anchors and embeds of a block, clickable tags, and links
 * that open another note at a heading or jump within the note.
 */
class PreviewReadingSyntaxTest {

    private static final String NOTE = String.join("\n",
            "# Nota",
            "",
            "> [!tip] Consejo",
            "> Algo ==importante== aquí.",
            "",
            "Visible %%secreto%% sigue.",
            "",
            "Dato con nota[^1] y etiqueta #proyecto.",
            "",
            "Un párrafo marcado ^marca",
            "",
            "![[Otra#^blk]]",
            "",
            "[[#^marca|saltar]] y [[Otra#Detalle]]",
            "",
            "[^1]: La nota al pie.");

    @Test
    void obsidianSyntaxRendersEndToEnd() {
        String html = MarkdownPreview.buildPreviewHtml(NOTE, false, List.of(), new MarkdownPreview.PreviewOptions(null, link -> "Contenido\nbloque incrustado %%oculto en la otra%% #etiqueta ^blk\n", null, false, 0, null, null));
        String body = html.substring(html.indexOf("<body>"));

        assertTrue(body.contains("data-callout=\"tip\""), "callout");
        assertTrue(body.contains("<mark>importante</mark>"), "highlight");
        assertFalse(body.contains("secreto"), "comment hidden");
        assertTrue(body.contains("Visible  sigue."), "text around the comment stays");
        assertTrue(body.contains("<sup class=\"footnote-ref\" id=\"fnref-1\"><a href=\"#fn-1\">1</a></sup>"), "footnote ref");
        assertTrue(body.contains("<li id=\"fn-1\">La nota al pie."), "footnote listed");
        int content = body.indexOf("Un párrafo marcado");
        assertTrue(content >= 0 && body.indexOf("<section class=\"footnotes\">") > content, "footnotes after the content");
        assertFalse(body.contains("class=\"emoji\""), "the note has no emoji: the back link must not turn into one");
        assertTrue(body.contains("<a class=\"tag\" href=\"jylos://tag/proyecto\">#proyecto</a>"), "tag");
        assertTrue(body.contains("id=\"blockref-marca\""), "block anchor");
        assertFalse(body.contains("^marca"), "block marker hidden");
        assertTrue(body.contains("Contenido<br>bloque incrustado"), "block embedded: " + body);
        assertFalse(body.contains("oculto en la otra"), "a comment in the embedded note is hidden too");
        assertTrue(body.contains("href=\"jylos://tag/etiqueta\""), "a tag in the embedded note is a link too");
        assertTrue(body.contains("href=\"#blockref-marca\">saltar</a>"), "same-note link");
        assertTrue(body.contains("data-subpath=\"Detalle\""), "link to a heading of another note");
    }

    @Test
    void theClickScriptPassesTheSubpathAndOpensTags() {
        String html = MarkdownPreview.buildPreviewHtml("x", false, List.of());
        assertTrue(html.contains("getAttribute('data-subpath')"), "subpath forwarded on click");
        assertTrue(html.contains("jylos://tag/") && html.contains("window.javaApp.openTag("), "tag clicks reach the app");
    }

    @Test
    void stylesCoverTheNewSyntaxInBothThemes() {
        for (boolean dark : new boolean[] { false, true }) {
            String html = MarkdownPreview.buildPreviewHtml("x", dark, List.of());
            for (String rule : List.of(".callout {", ".callout-orange", ".footnotes", "a.tag", ".callout-title")) {
                assertTrue(html.contains(rule), (dark ? "dark" : "light") + " misses " + rule);
            }
        }
    }
}
