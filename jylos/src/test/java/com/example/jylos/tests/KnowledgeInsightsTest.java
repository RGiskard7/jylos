package com.example.jylos.tests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.example.jylos.graph.GraphData;
import com.example.jylos.graph.GraphEdge;
import com.example.jylos.graph.GraphNode;
import com.example.jylos.insights.BrokenLinkInfo;
import com.example.jylos.insights.GraphAnalysisService;
import com.example.jylos.insights.GraphAnalysisService.GraphAnalysis;
import com.example.jylos.insights.KnowledgeInsightsService;
import com.example.jylos.insights.NoteConnectivityInfo;

/**
 * Unit tests for the Knowledge Insights analytics. These exercise the pure, service-free
 * {@link GraphAnalysisService#analyze(GraphData)} over hand-built graphs and the
 * documented {@link KnowledgeInsightsService#healthScore} formula — no UI, no DAOs.
 */
class KnowledgeInsightsTest {

    private static GraphNode note(String id) {
        return new GraphNode(id, id, GraphNode.Type.NOTE, "", 0);
    }

    private static GraphNode ghost(String id, String label) {
        return new GraphNode(id, label, GraphNode.Type.GHOST, "", 0);
    }

    private static GraphEdge link(String source, String target) {
        return new GraphEdge(source, target, GraphEdge.Type.LINK);
    }

    @Test
    void detectsOrphanNotes() {
        // A → B; C is connected to nothing.
        GraphData graph = new GraphData(
                List.of(note("A"), note("B"), note("C")),
                List.of(link("A", "B")));

        GraphAnalysis a = GraphAnalysisService.analyze(graph);

        assertEquals(3, a.totalNotes());
        assertEquals(1, a.totalLinks());
        assertEquals(1, a.orphans().size(), "only C is an orphan");
        assertEquals("C", a.orphans().get(0).noteId());
    }

    @Test
    void detectsBrokenLinks() {
        // A links to a non-existent note (a ghost node).
        GraphData graph = new GraphData(
                List.of(note("A"), ghost("ghost:missing", "Missing Note")),
                List.of(link("A", "ghost:missing")));

        GraphAnalysis a = GraphAnalysisService.analyze(graph);

        assertEquals(1, a.brokenLinks().size());
        BrokenLinkInfo broken = a.brokenLinks().get(0);
        assertEquals("A", broken.sourceNoteId());
        assertEquals("Missing Note", broken.targetTitle());
        assertEquals(0, a.totalLinks(), "a link to a ghost is not a resolved link");
    }

    @Test
    void ranksMostConnectedNotesByTotalDegree() {
        // B → A, C → A, A → D : A has the most connections (in 2 + out 1 = 3).
        GraphData graph = new GraphData(
                List.of(note("A"), note("B"), note("C"), note("D")),
                List.of(link("B", "A"), link("C", "A"), link("A", "D")));

        GraphAnalysis a = GraphAnalysisService.analyze(graph);
        List<NoteConnectivityInfo> ranked = a.connectivity();

        assertEquals("A", ranked.get(0).noteId(), "A is the most connected");
        assertEquals(2, ranked.get(0).inbound());
        assertEquals(1, ranked.get(0).outbound());
        assertEquals(3, ranked.get(0).total());
        assertTrue(ranked.get(1).total() <= ranked.get(0).total(), "sorted descending");
    }

    @Test
    void healthScorePenalizesOrphansUntaggedAndBrokenLinks() {
        // 10 notes, 5 orphans (-20), 10 untagged (-20), 3 broken links (-15) → 45.
        KnowledgeInsightsService.Health h = KnowledgeInsightsService.healthScore(10, 5, 10, 3);
        assertEquals(20, h.orphanPenalty());
        assertEquals(20, h.untaggedPenalty());
        assertEquals(15, h.brokenPenalty());
        assertEquals(45, h.score());
    }

    @Test
    void healthScoreClampsAndHandlesEmptyVault() {
        assertEquals(100, KnowledgeInsightsService.healthScore(0, 0, 0, 0).score(), "empty vault is healthy");

        // Everything wrong: full orphans (-40) + full untagged (-20) + many broken (cap -25) = 15.
        KnowledgeInsightsService.Health worst = KnowledgeInsightsService.healthScore(4, 4, 4, 99);
        assertEquals(25, worst.brokenPenalty(), "broken penalty is capped at 25");
        assertEquals(15, worst.score());
        assertTrue(worst.score() >= 0, "never below 0");
    }

    @Test
    void brokenPenaltyIsFivePerLink() {
        assertEquals(10, KnowledgeInsightsService.healthScore(10, 0, 0, 2).brokenPenalty());
        assertFalse(KnowledgeInsightsService.healthScore(10, 0, 0, 0).brokenPenalty() > 0);
    }
}
