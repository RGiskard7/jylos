package com.example.jylos.insights;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.example.jylos.graph.GraphBuilder;
import com.example.jylos.graph.GraphData;
import com.example.jylos.graph.GraphEdge;
import com.example.jylos.graph.GraphNode;
import com.example.jylos.service.NoteService;
import com.example.jylos.service.TagService;

/**
 * Structural analysis of the knowledge graph: connectivity per note, orphan notes
 * and broken links.
 *
 * <p>It reuses {@link GraphBuilder} (which derives note→note edges with
 * {@link com.example.jylos.util.WikiLinkResolver}) so there is <em>no second,
 * parallel link-resolution logic</em> — the analysis sees exactly the same links the
 * graph and the preview do. The pure {@link #analyze(GraphData)} overload makes the
 * logic unit-testable without any services.</p>
 *
 * <h3>Definitions</h3>
 * <ul>
 *   <li><b>Resolved link</b>: a {@code LINK} edge whose source and target are both
 *       existing notes. Connectivity counts only these.</li>
 *   <li><b>Broken link</b>: a {@code LINK} edge from a note to a "ghost" node (a link
 *       target with no matching note).</li>
 *   <li><b>Orphan</b>: a note with no resolved links in or out. A note that only links
 *       to non-existent notes is <em>not</em> counted twice — it surfaces under broken
 *       links, and is an orphan only if it has no resolved connections.</li>
 * </ul>
 *
 * @author Edu Díaz (RGiskard7)
 * @since 2.1.0
 */
public final class GraphAnalysisService {

    private final GraphBuilder graphBuilder;

    public GraphAnalysisService(NoteService noteService, TagService tagService) {
        this.graphBuilder = new GraphBuilder(noteService, tagService);
    }

    /** Builds the vault graph (links only, ghosts on) and analyses it. */
    public GraphAnalysis analyze() {
        // includeTags=false (links only), includeGhosts=true (to detect broken links),
        // includeOrphans=true (we need orphans, that's the whole point).
        GraphData graph = graphBuilder.buildGlobalGraph(new GraphBuilder.Options(false, true, true));
        return analyze(graph);
    }

    /**
     * Pure structural analysis of an already-built graph. Side-effect free and
     * service-free so it can be unit-tested with a hand-built {@link GraphData}.
     */
    public static GraphAnalysis analyze(GraphData graph) {
        Map<String, GraphNode> byId = new LinkedHashMap<>();
        Map<String, String> noteTitle = new LinkedHashMap<>();
        for (GraphNode n : graph.nodes()) {
            byId.put(n.id(), n);
            if (n.type() == GraphNode.Type.NOTE) {
                noteTitle.put(n.id(), n.label());
            }
        }

        Map<String, Integer> inbound = new HashMap<>();
        Map<String, Integer> outbound = new HashMap<>();
        List<BrokenLinkInfo> brokenLinks = new ArrayList<>();

        for (GraphEdge e : graph.edges()) {
            if (e.type() != GraphEdge.Type.LINK || !noteTitle.containsKey(e.source())) {
                continue;
            }
            if (noteTitle.containsKey(e.target())) {
                outbound.merge(e.source(), 1, Integer::sum);
                inbound.merge(e.target(), 1, Integer::sum);
            } else {
                GraphNode target = byId.get(e.target());
                if (target != null && target.type() == GraphNode.Type.GHOST) {
                    brokenLinks.add(new BrokenLinkInfo(e.source(), noteTitle.get(e.source()), target.label()));
                }
            }
        }

        List<NoteConnectivityInfo> connectivity = new ArrayList<>(noteTitle.size());
        List<KnowledgeHealthReport.NoteRef> orphans = new ArrayList<>();
        for (Map.Entry<String, String> entry : noteTitle.entrySet()) {
            String id = entry.getKey();
            int in = inbound.getOrDefault(id, 0);
            int out = outbound.getOrDefault(id, 0);
            connectivity.add(new NoteConnectivityInfo(id, entry.getValue(), in, out));
            if (in == 0 && out == 0) {
                orphans.add(new KnowledgeHealthReport.NoteRef(id, entry.getValue()));
            }
        }
        // Most connected first; stable by title for ties.
        connectivity.sort((a, b) -> {
            int byTotal = Integer.compare(b.total(), a.total());
            return byTotal != 0 ? byTotal : String.valueOf(a.title()).compareToIgnoreCase(String.valueOf(b.title()));
        });

        int totalNotes = noteTitle.size();
        int totalLinks = outbound.values().stream().mapToInt(Integer::intValue).sum();
        return new GraphAnalysis(totalNotes, totalLinks, connectivity, orphans, brokenLinks);
    }

    /**
     * Result of a structural pass over the graph.
     *
     * @param totalNotes   number of existing notes
     * @param totalLinks   resolved directed note→note links
     * @param connectivity per-note inbound/outbound, sorted by total descending
     * @param orphans      notes with no resolved links
     * @param brokenLinks  links to non-existent notes
     */
    public record GraphAnalysis(
            int totalNotes,
            int totalLinks,
            List<NoteConnectivityInfo> connectivity,
            List<KnowledgeHealthReport.NoteRef> orphans,
            List<BrokenLinkInfo> brokenLinks) {
    }
}
