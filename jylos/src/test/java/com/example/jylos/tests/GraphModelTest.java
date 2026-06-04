package com.example.jylos.tests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.example.jylos.graph.GraphData;
import com.example.jylos.graph.GraphEdge;
import com.example.jylos.graph.GraphNode;
import com.example.jylos.util.WikiLinkResolver;

/**
 * Tests for the graph model: link extraction (the basis of note→note edges) and
 * the dependency-free JSON serialization fed to the WebView renderer.
 */
class GraphModelTest {

    // ── WikiLinkResolver.extractLinkTargets ──────────────────────────────

    @Test
    void extractsWikiLinkTargets() {
        List<String> targets = WikiLinkResolver.extractLinkTargets(
                "See [[Alpha]] and [[folder/Beta.md]] plus [[Gamma#Heading|alias]].");
        assertEquals(List.of("Alpha", "Beta", "Gamma"), targets);
    }

    @Test
    void extractsMarkdownInternalLinksButIgnoresExternal() {
        List<String> targets = WikiLinkResolver.extractLinkTargets(
                "[doc](Alpha.md) and [web](https://example.com) and [enc](Note%20Two)");
        assertEquals(List.of("Alpha", "Note Two"), targets);
    }

    @Test
    void ignoresWikiLinksToExternalUrls() {
        List<String> targets = WikiLinkResolver.extractLinkTargets(
                "[[My Note]] [[https://example.com]] [[www.site.org/page]]");
        assertEquals(List.of("My Note"), targets);
    }

    @Test
    void inlineHashtagsAreNotGraphLinks() {
        List<String> targets = WikiLinkResolver.extractLinkTargets(
                "#project #idea\n\nSee [[Other Note]] for more.");
        assertEquals(List.of("Other Note"), targets);
    }

    @Test
    void ignoresAnchorsTitlesAndNonNoteFiles() {
        // Pasted web-article noise must NOT become graph links (Obsidian behavior).
        List<String> targets = WikiLinkResolver.extractLinkTargets(
                "[Contents](#SEC_Contents \"Table of contents\") "
                + "[pic](image.png) [doc](paper.pdf) [site](https://x.com) "
                + "[real](Real%20Note.md) [bare](Some Note)");
        // Only the .md link and the bare note name survive; the bare one keeps its
        // first whitespace-delimited token, matching link URL semantics.
        assertTrue(targets.contains("Real Note"), "got: " + targets);
        assertFalse(targets.contains("SEC_Contents"), "anchors must be ignored: " + targets);
        assertFalse(targets.contains("image"), "images must be ignored: " + targets);
        assertFalse(targets.contains("paper"), "non-note files must be ignored: " + targets);
    }

    @Test
    void deduplicatesPreservingOrder() {
        List<String> targets = WikiLinkResolver.extractLinkTargets("[[Alpha]] [[alpha]] [[Beta]] [[Alpha]]");
        // extractTitle keeps original case; dedup is exact-string, so "Alpha" and "alpha" both survive once
        assertEquals(List.of("Alpha", "alpha", "Beta"), targets);
    }

    @Test
    void handlesNullAndEmpty() {
        assertTrue(WikiLinkResolver.extractLinkTargets(null).isEmpty());
        assertTrue(WikiLinkResolver.extractLinkTargets("").isEmpty());
        assertTrue(WikiLinkResolver.extractLinkTargets("no links here").isEmpty());
    }

    // ── GraphData JSON ───────────────────────────────────────────────────

    @Test
    void serializesNodesAndEdgesToJson() {
        GraphData data = new GraphData(
                List.of(
                        new GraphNode("n1", "Alpha", GraphNode.Type.NOTE, "f1", 1),
                        new GraphNode("tag:work", "#work", GraphNode.Type.TAG, "work", 1)),
                List.of(new GraphEdge("n1", "tag:work", GraphEdge.Type.TAG)));

        String json = data.toJson();
        assertTrue(json.contains("\"id\":\"n1\""));
        assertTrue(json.contains("\"label\":\"Alpha\""));
        assertTrue(json.contains("\"type\":\"note\""));
        assertTrue(json.contains("\"type\":\"tag\""));
        assertTrue(json.contains("\"degree\":1"));
        assertTrue(json.contains("\"source\":\"n1\""));
        assertTrue(json.contains("\"target\":\"tag:work\""));
    }

    @Test
    void escapesSpecialCharactersInJson() {
        GraphData data = new GraphData(
                List.of(new GraphNode("id\"1", "Line\nBreak \"quote\"", GraphNode.Type.NOTE, "", 0)),
                List.of());
        String json = data.toJson();
        assertTrue(json.contains("\\\"quote\\\""), "Quotes must be escaped: " + json);
        assertTrue(json.contains("\\n"), "Newlines must be escaped: " + json);
        assertFalse(json.contains("\"id\"1\""), "Raw unescaped quote leaked: " + json);
    }

    @Test
    void emptyGraphIsValidJson() {
        assertEquals("{\"nodes\":[],\"edges\":[]}", GraphData.empty().toJson());
    }
}
