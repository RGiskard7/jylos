package com.example.jylos.graph;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

import com.example.jylos.config.LoggerConfig;
import com.example.jylos.data.models.Note;
import com.example.jylos.data.models.Tag;
import com.example.jylos.service.NoteService;
import com.example.jylos.service.TagService;
import com.example.jylos.util.WikiLinkResolver;

/**
 * Builds the knowledge graph (notes + tags + their relationships) from the
 * existing services.
 *
 * <p>Note→note edges are derived with {@link WikiLinkResolver#extractLinkTargets(String)}
 * (only {@code [[wiki-links]]} and internal {@code [label](note)} references —
 * no URLs, anchors, attachments, or inline {@code #tags}). Optional tag nodes
 * are off by default ({@link Options#includeTags()}).</p>
 *
 * <p>Building is read-only and side-effect free; callers should run it off the
 * JavaFX thread for large vaults.</p>
 *
 * @author Edu Díaz (RGiskard7)
 */
public class GraphBuilder {

    private static final Logger logger = LoggerConfig.getLogger(GraphBuilder.class);

    private final NoteService noteService;
    private final TagService tagService;

    /**
     * Per-note cache of extracted link targets, keyed by note id. Avoids re-reading
     * every file on each rebuild (toggle tags/scope); invalidated when the note's
     * {@code modified} timestamp changes, or explicitly via {@link #invalidateNote}.
     *
     * <p>Concurrent because builds run off the JavaFX thread while invalidation is
     * driven from note/folder events on the FX thread (perf P3).</p>
     */
    private final Map<String, CachedLinks> linkCache = new java.util.concurrent.ConcurrentHashMap<>();

    private record CachedLinks(String modified, List<String> targets) {
    }

    public GraphBuilder(NoteService noteService, TagService tagService) {
        this.noteService = noteService;
        this.tagService = tagService;
    }

    /**
     * Drops the cached link targets for a single note so the next build re-reads its
     * full content. Cheap and FX-thread-safe — the actual (file) read is deferred to
     * the next off-thread build. No-op for a {@code null} id.
     */
    public void invalidateNote(String id) {
        if (id != null) {
            linkCache.remove(id);
        }
    }

    /** Clears the whole link cache (e.g. folder deletion or storage switch). */
    public void invalidateAll() {
        linkCache.clear();
    }

    /**
     * Returns the internal-link targets of a note, reading its <em>full</em> content
     * (filesystem {@code getAllNotes} returns a truncated body, which would drop
     * links located deeper in long notes — the cause of a sparser-than-Obsidian
     * graph). Results are cached per note id and validated by {@code modified}.
     */
    private List<String> linkTargetsFor(Note note) {
        String id = note.getId();
        String modified = note.getModifiedDate();
        CachedLinks cached = linkCache.get(id);
        if (cached != null && java.util.Objects.equals(cached.modified(), modified)) {
            return cached.targets();
        }
        String content = note.getContent();
        try {
            Note full = noteService.getNoteById(id).orElse(null);
            if (full != null && full.getContent() != null) {
                content = full.getContent();
            }
        } catch (Exception e) {
            logger.fine("Graph: could not read full content for " + id + ": " + e.getMessage());
        }
        List<String> targets = WikiLinkResolver.extractLinkTargets(content);
        linkCache.put(id, new CachedLinks(modified, targets));
        return targets;
    }

    /**
     * Graph build options, mirroring Obsidian's graph filters.
     *
     * @param includeTags    include {@code #tag} nodes and note→tag edges
     * @param includeGhosts  include hollow nodes for links to non-existent notes
     * @param includeOrphans include nodes that have no connections at all
     */
    public record Options(boolean includeTags, boolean includeGhosts, boolean includeOrphans) {
        public static Options defaults() {
            return new Options(false, true, true);
        }
    }

    /** Builds the full vault graph with default options. */
    public GraphData buildGlobalGraph(boolean includeTags) {
        return buildGlobalGraph(new Options(includeTags, true, true));
    }

    /** Builds the full vault graph with explicit filter options. */
    public GraphData buildGlobalGraph(Options options) {
        if (noteService == null) {
            return GraphData.empty();
        }

        List<Note> notes = noteService.getAllNotes();

        Map<String, String> titleToId = new HashMap<>();
        Map<String, GraphNode> nodes = new LinkedHashMap<>();
        for (Note note : notes) {
            if (note == null || note.getId() == null) {
                continue;
            }
            String title = note.getTitle();
            if (title != null && !title.isBlank()) {
                titleToId.putIfAbsent(normalize(title), note.getId());
            }
            nodes.put(note.getId(), new GraphNode(note.getId(), labelOf(note),
                    GraphNode.Type.NOTE, folderGroup(note), 0));
        }

        List<GraphEdge> edges = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (Note note : notes) {
            if (note == null || note.getId() == null) {
                continue;
            }
            for (String target : linkTargetsFor(note)) {
                String norm = normalize(target);
                String targetId = titleToId.get(norm);
                if (targetId != null) {
                    if (targetId.equals(note.getId())) {
                        continue;
                    }
                    if (seen.add(note.getId() + "\u0000" + targetId)) {
                        edges.add(new GraphEdge(note.getId(), targetId, GraphEdge.Type.LINK));
                    }
                } else if (options.includeGhosts()) {
                    // Link to a note that doesn't exist yet -> Obsidian-style ghost node.
                    String ghostId = "ghost:" + norm;
                    nodes.putIfAbsent(ghostId, new GraphNode(ghostId, target.trim(),
                            GraphNode.Type.GHOST, "", 0));
                    if (seen.add(note.getId() + "\u0000" + ghostId)) {
                        edges.add(new GraphEdge(note.getId(), ghostId, GraphEdge.Type.LINK));
                    }
                }
            }
        }

        if (options.includeTags()) {
            addTagEdges(nodes, edges);
        }

        GraphData graph = withDegrees(nodes, edges);
        return options.includeOrphans() ? graph : dropOrphans(graph);
    }

    /** Removes nodes with no connections (degree 0). Edges are unaffected. */
    private GraphData dropOrphans(GraphData data) {
        List<GraphNode> kept = new ArrayList<>(data.nodes().size());
        for (GraphNode node : data.nodes()) {
            if (node.degree() > 0) {
                kept.add(node);
            }
        }
        return new GraphData(kept, data.edges());
    }

    /**
     * Builds a local graph centered on a note: the note plus everything reachable
     * within {@code depth} hops (Obsidian's "local graph").
     *
     * @param rootId      center note id (if null, falls back to the global graph)
     * @param depth       neighborhood radius in hops (clamped to at least 1)
     * @param options graph filter options
     */
    public GraphData buildLocalGraph(String rootId, int depth, Options options) {
        GraphData global = buildGlobalGraph(options);
        if (rootId == null || global.nodes().isEmpty()) {
            return global;
        }

        Map<String, List<String>> adjacency = new HashMap<>();
        for (GraphEdge edge : global.edges()) {
            adjacency.computeIfAbsent(edge.source(), k -> new ArrayList<>()).add(edge.target());
            adjacency.computeIfAbsent(edge.target(), k -> new ArrayList<>()).add(edge.source());
        }

        Set<String> keep = new HashSet<>();
        keep.add(rootId);
        Set<String> frontier = new HashSet<>();
        frontier.add(rootId);
        int hops = Math.max(1, depth);
        for (int d = 0; d < hops && !frontier.isEmpty(); d++) {
            Set<String> next = new HashSet<>();
            for (String id : frontier) {
                for (String neighbor : adjacency.getOrDefault(id, List.of())) {
                    if (keep.add(neighbor)) {
                        next.add(neighbor);
                    }
                }
            }
            frontier = next;
        }

        List<GraphNode> nodes = new ArrayList<>();
        for (GraphNode node : global.nodes()) {
            if (keep.contains(node.id())) {
                nodes.add(node);
            }
        }
        List<GraphEdge> edges = new ArrayList<>();
        for (GraphEdge edge : global.edges()) {
            if (keep.contains(edge.source()) && keep.contains(edge.target())) {
                edges.add(edge);
            }
        }
        return new GraphData(nodes, edges);
    }

    private void addTagEdges(Map<String, GraphNode> nodes, List<GraphEdge> edges) {
        if (tagService == null) {
            return;
        }
        List<Tag> tags;
        try {
            tags = tagService.getAllTags();
        } catch (Exception e) {
            logger.warning("Could not load tags for graph: " + e.getMessage());
            return;
        }
        for (Tag tag : tags) {
            if (tag == null || tag.getTitle() == null || tag.getTitle().isBlank()) {
                continue;
            }
            String tagId = "tag:" + tag.getTitle();
            boolean tagAdded = false;
            try {
                for (Note tagged : tagService.getNotesWithTag(tag)) {
                    if (tagged == null || tagged.getId() == null || !nodes.containsKey(tagged.getId())) {
                        continue;
                    }
                    if (!tagAdded) {
                        nodes.put(tagId, new GraphNode(tagId, "#" + tag.getTitle(),
                                GraphNode.Type.TAG, tag.getTitle(), 0));
                        tagAdded = true;
                    }
                    edges.add(new GraphEdge(tagged.getId(), tagId, GraphEdge.Type.TAG));
                }
            } catch (Exception e) {
                logger.fine("Skipping tag '" + tag.getTitle() + "' in graph: " + e.getMessage());
            }
        }
    }

    private GraphData withDegrees(Map<String, GraphNode> nodes, List<GraphEdge> edges) {
        Map<String, Integer> degree = new HashMap<>();
        for (GraphEdge edge : edges) {
            degree.merge(edge.source(), 1, Integer::sum);
            degree.merge(edge.target(), 1, Integer::sum);
        }
        List<GraphNode> out = new ArrayList<>(nodes.size());
        for (GraphNode node : nodes.values()) {
            out.add(node.withDegree(degree.getOrDefault(node.id(), 0)));
        }
        return new GraphData(out, edges);
    }

    private static String folderGroup(Note note) {
        if (note.getParent() != null && note.getParent().getId() != null) {
            return note.getParent().getId();
        }
        return "";
    }

    private static String labelOf(Note note) {
        String title = note.getTitle();
        return (title != null && !title.isBlank()) ? title : "(untitled)";
    }

    private static String normalize(String title) {
        return title.trim().toLowerCase(Locale.ROOT);
    }
}
