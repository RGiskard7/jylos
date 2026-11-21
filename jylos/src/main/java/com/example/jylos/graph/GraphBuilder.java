package com.example.jylos.graph;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

import com.example.jylos.config.LoggerConfig;
import com.example.jylos.data.models.Note;
import com.example.jylos.data.models.Tag;
import com.example.jylos.service.LinkIndex;
import com.example.jylos.service.NoteService;
import com.example.jylos.service.TagService;
import com.example.jylos.util.AttachmentType;
import com.example.jylos.util.WikiLinkResolver;

/**
 * Builds the knowledge graph (notes + tags + their relationships) from the
 * existing services.
 *
 * <p>Note→note edges are derived with {@link WikiLinkResolver#extractRawLinks(String)}
 * (only {@code [[wiki-links]]} and internal {@code [label](note)} references outside
 * code — no URLs, anchors, attachments, or inline {@code #tags}), each resolved to the
 * note it names by path, as in Obsidian ({@link com.example.jylos.service.LinkIndex}).
 * Optional tag nodes are off by default ({@link Options#includeTags()}).</p>
 *
 * <p>Building is read-only and side-effect free; callers should run it off the
 * JavaFX thread for large vaults.</p>
 *
 * @author Edu Díaz (RGiskard7)
 * @since 1.0.0
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
    private final Map<String, CachedLinks> linkCache = new ConcurrentHashMap<>();

    private record CachedLinks(String modified, List<String> targets, List<String> files) {
    }

    /**
     * Notes invalidated one by one ({@link #invalidateNote}): their file may have changed since
     * the vault listed them, so they are read again instead of taken from the listing, until the
     * whole graph is invalidated (the vault was read again).
     */
    private final Set<String> readAgain = ConcurrentHashMap.newKeySet();

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
            readAgain.add(id);
        }
    }

    /** Waits (off the FX thread) for the vault to finish loading its notes and their links. */
    public void awaitNotesLoaded() {
        noteService.awaitContentLoaded(10 * 60_000L);
    }

    /** Clears the whole link cache (e.g. folder deletion or storage switch). */
    public void invalidateAll() {
        linkCache.clear();
        readAgain.clear();
    }

    /**
     * Returns the internal-link targets of a note, of its <em>full</em> content: those the
     * vault listed from the whole file when it has them ({@link Note#isMetadataComplete()}),
     * else read from the note (filesystem {@code getAllNotes} returns a truncated body, which
     * would drop links located deeper in long notes). Reads are cached per note id and
     * validated by {@code modified}.
     */
    private List<String> linkTargetsFor(Note note) {
        return linksOf(note).targets();
    }

    /** The links a note makes to files that are not notes (images, PDFs, canvases…). */
    private List<String> fileLinksFor(Note note) {
        return linksOf(note).files();
    }

    private CachedLinks linksOf(Note note) {
        String id = note.getId();
        String modified = note.getModifiedDate();
        if (note.isMetadataComplete() && note.getLinkTargets() != null && note.getFileLinkTargets() != null
                && !readAgain.contains(id)) {
            // The vault read them from the whole file (or its cache of it): nothing to read.
            return new CachedLinks(modified, note.getLinkTargets(), note.getFileLinkTargets());
        }
        CachedLinks cached = linkCache.get(id);
        if (cached != null && Objects.equals(cached.modified(), modified)) {
            return cached;
        }
        String content = note.getContent();
        List<String> targets = null;
        try {
            Note full = noteService.getNoteById(id).orElse(null);
            if (full != null) {
                // The links storage found in the whole file, properties included (as Obsidian).
                targets = full.getLinkTargets();
                if (full.getContent() != null) {
                    content = full.getContent();
                }
            }
        } catch (Exception e) {
            logger.fine("Graph: could not read full content for " + id + ": " + e.getMessage());
        }
        if (targets == null) {
            targets = WikiLinkResolver.extractRawLinks(content);
        }
        CachedLinks links = new CachedLinks(modified, targets, WikiLinkResolver.extractFileLinks(content));
        linkCache.put(id, links);
        return links;
    }

    /**
     * Graph build options, mirroring Obsidian's graph filters.
     *
     * @param includeTags    include {@code #tag} nodes and note→tag edges
     * @param includeGhosts  include hollow nodes for links to non-existent notes
     * @param includeOrphans include nodes that have no connections at all
     * @param includeAttachments include images, PDFs and the other files notes link to, each
     *                       joined to the notes that link or embed it (off by default, as in
     *                       Obsidian); a canvas is a node either way
     */
    public record Options(boolean includeTags, boolean includeGhosts, boolean includeOrphans,
            boolean includeAttachments) {
        /** Without attachments, as Obsidian starts. */
        public Options(boolean includeTags, boolean includeGhosts, boolean includeOrphans) {
            this(includeTags, includeGhosts, includeOrphans, false);
        }

        public static Options defaults() {
            return new Options(false, true, true);
        }
    }

    /**
     * What the local graph shows, as in Obsidian.
     *
     * @param depth          how many links away from the open note (kept between 1 and 5)
     * @param outgoing       follow the links each note makes
     * @param incoming       follow the links each note receives
     * @param neighborLinks  also show the links between the notes reached
     */
    public record LocalOptions(int depth, boolean outgoing, boolean incoming, boolean neighborLinks) {
        /** Obsidian's defaults: one link away, both directions, no links between neighbours. */
        public static LocalOptions defaults() {
            return new LocalOptions(1, true, true, false);
        }
    }

    /**
     * The local graph of a note, as in Obsidian: the note plus the notes up to
     * {@code local.depth()} links away, following outgoing and/or incoming links. Only the
     * links that were followed are shown, plus every link between the notes reached when
     * {@code neighborLinks} is on. Tags are reached but not followed further. The note itself
     * is always there, even with no links (orphans are not filtered out here).
     */
    public GraphData buildLocalGraph(String rootId, LocalOptions local, Options options) {
        GraphData global = buildGlobalGraph(new Options(options.includeTags(), options.includeGhosts(), true,
                options.includeAttachments()));
        if (rootId == null) {
            return global;
        }
        Map<String, GraphNode> byId = new LinkedHashMap<>();
        for (GraphNode node : global.nodes()) {
            byId.put(node.id(), node);
        }
        if (!byId.containsKey(rootId)) {
            return GraphData.empty();
        }
        Map<String, List<GraphEdge>> out = new HashMap<>();
        Map<String, List<GraphEdge>> in = new HashMap<>();
        for (GraphEdge edge : global.edges()) {
            out.computeIfAbsent(edge.source(), k -> new ArrayList<>()).add(edge);
            in.computeIfAbsent(edge.target(), k -> new ArrayList<>()).add(edge);
        }
        Set<String> keep = new LinkedHashSet<>();
        keep.add(rootId);
        Set<GraphEdge> shown = new LinkedHashSet<>();
        List<String> frontier = List.of(rootId);
        int depth = Math.max(1, Math.min(5, local.depth()));
        for (int hop = 0; hop < depth && !frontier.isEmpty(); hop++) {
            List<String> next = new ArrayList<>();
            for (String id : frontier) {
                GraphNode node = byId.get(id);
                if (node != null && node.type() == GraphNode.Type.TAG && !id.equals(rootId)) {
                    continue; // a tag is reached, not followed
                }
                if (local.outgoing()) {
                    for (GraphEdge edge : out.getOrDefault(id, List.of())) {
                        shown.add(edge);
                        if (keep.add(edge.target())) {
                            next.add(edge.target());
                        }
                    }
                }
                if (local.incoming()) {
                    for (GraphEdge edge : in.getOrDefault(id, List.of())) {
                        shown.add(edge);
                        if (keep.add(edge.source())) {
                            next.add(edge.source());
                        }
                    }
                }
            }
            frontier = next;
        }
        if (local.neighborLinks()) {
            for (GraphEdge edge : global.edges()) {
                if (keep.contains(edge.source()) && keep.contains(edge.target())) {
                    shown.add(edge);
                }
            }
        }
        List<GraphNode> nodes = new ArrayList<>();
        for (String id : keep) {
            if (byId.containsKey(id)) {
                nodes.add(byId.get(id));
            }
        }
        return new GraphData(nodes, new ArrayList<>(shown));
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

        LinkIndex links = noteService.linkIndex();
        Map<String, GraphNode> nodes = new LinkedHashMap<>();
        for (Note note : notes) {
            if (note == null || note.getId() == null) {
                continue;
            }
            if (isHiddenAttachment(note.getId(), options)) {
                continue;
            }
            nodes.put(note.getId(), new GraphNode(note.getId(), labelOf(note),
                    GraphNode.Type.NOTE, folderGroup(note), 0));
        }

        List<GraphEdge> edges = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (Note note : notes) {
            if (note == null || note.getId() == null || !nodes.containsKey(note.getId())) {
                continue;
            }
            for (String file : fileLinksFor(note)) {
                // Only to a file that is on the graph: a canvas always, the rest with attachments.
                links.resolveId(file, note.getId())
                        .filter(nodes::containsKey)
                        .filter(fileId -> !fileId.equals(note.getId()))
                        .filter(fileId -> seen.add(note.getId() + "\u0000" + fileId))
                        .ifPresent(fileId -> edges.add(new GraphEdge(note.getId(), fileId, GraphEdge.Type.LINK)));
            }
            for (String target : linkTargetsFor(note)) {
                String targetId = links.resolveId(target, note.getId())
                        .filter(nodes::containsKey)
                        .orElse(null);
                if (targetId != null) {
                    if (targetId.equals(note.getId())) {
                        continue;
                    }
                    if (seen.add(note.getId() + "\u0000" + targetId)) {
                        edges.add(new GraphEdge(note.getId(), targetId, GraphEdge.Type.LINK));
                    }
                } else if (options.includeGhosts()) {
                    // Link to a note that doesn't exist yet -> Obsidian-style ghost node.
                    String ghostId = "ghost:" + normalize(target);
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

    /** An image, PDF or other file that is not a note, left out unless attachments are shown. */
    private static boolean isHiddenAttachment(String id, Options options) {
        AttachmentType type = AttachmentType.fromName(id);
        return type != AttachmentType.MARKDOWN
                && type != AttachmentType.CANVAS
                && !options.includeAttachments();
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
