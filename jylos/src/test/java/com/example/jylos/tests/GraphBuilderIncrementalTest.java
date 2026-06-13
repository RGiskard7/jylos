package com.example.jylos.tests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.example.jylos.data.dao.filesystem.FolderDAOFileSystem;
import com.example.jylos.data.dao.filesystem.NoteDAOFileSystem;
import com.example.jylos.data.dao.filesystem.TagDAOFileSystem;
import com.example.jylos.data.models.Note;
import com.example.jylos.graph.GraphBuilder;
import com.example.jylos.graph.GraphData;
import com.example.jylos.graph.GraphEdge;
import com.example.jylos.graph.GraphNode;
import com.example.jylos.service.NoteService;
import com.example.jylos.service.TagService;

/**
 * Perf P3: {@link GraphBuilder} caches link targets per note and exposes
 * {@code invalidateNote}/{@code invalidateAll}. After a note's links change and its
 * cache entry is invalidated, a rebuild must reflect the new edges — equivalent to a
 * full rebuild from scratch.
 */
class GraphBuilderIncrementalTest {

    private static Set<String> edgeKeys(GraphData data, java.util.Map<String, String> labelById) {
        return data.edges().stream()
                .map(e -> labelById.get(e.source()) + "->" + labelById.get(e.target()))
                .collect(Collectors.toSet());
    }

    private static java.util.Map<String, String> labelsById(GraphData data) {
        return data.nodes().stream()
                .collect(Collectors.toMap(GraphNode::id, GraphNode::label, (a, b) -> a));
    }

    @Test
    void invalidateNoteReflectsNewLinkOnRebuild(@TempDir Path vault) throws Exception {
        Files.writeString(vault.resolve("A.md"), "# A\n[[B]]\n", StandardCharsets.UTF_8);
        Files.writeString(vault.resolve("B.md"), "# B\n", StandardCharsets.UTF_8);
        Files.writeString(vault.resolve("C.md"), "# C\n", StandardCharsets.UTF_8);

        NoteDAOFileSystem noteDao = new NoteDAOFileSystem(vault.toString());
        NoteService noteSvc = new NoteService(noteDao, new FolderDAOFileSystem(vault.toString()),
                new TagDAOFileSystem(noteDao));
        TagService tagSvc = new TagService(new TagDAOFileSystem(noteDao), noteDao);
        GraphBuilder graph = new GraphBuilder(noteSvc, tagSvc);

        GraphBuilder.Options opts = new GraphBuilder.Options(false, true, true);

        GraphData before = graph.buildGlobalGraph(opts);
        assertTrue(edgeKeys(before, labelsById(before)).contains("A->B"), "initial A->B edge");

        // Find A's id and append a link to C.
        String idA = noteSvc.getAllNotes().stream()
                .filter(n -> "A".equals(n.getTitle())).map(Note::getId).findFirst().orElseThrow();
        Files.writeString(vault.resolve("A.md"), "# A\n[[B]]\n[[C]]\n", StandardCharsets.UTF_8);

        graph.invalidateNote(idA);

        GraphData after = graph.buildGlobalGraph(opts);
        Set<String> keys = edgeKeys(after, labelsById(after));
        assertTrue(keys.contains("A->B"), "A->B kept");
        assertTrue(keys.contains("A->C"), "A->C added after invalidate; got: " + keys);

        // A fresh builder (no cache) must agree — invalidation = full rebuild equivalence.
        GraphData fresh = new GraphBuilder(noteSvc, tagSvc).buildGlobalGraph(opts);
        assertEquals(edgeKeys(fresh, labelsById(fresh)), keys, "incremental == full rebuild");
    }

    @Test
    void invalidateAllAndUnknownIdAreSafe(@TempDir Path vault) throws Exception {
        Files.writeString(vault.resolve("A.md"), "# A\n[[B]]\n", StandardCharsets.UTF_8);
        Files.writeString(vault.resolve("B.md"), "# B\n", StandardCharsets.UTF_8);

        NoteDAOFileSystem noteDao = new NoteDAOFileSystem(vault.toString());
        NoteService noteSvc = new NoteService(noteDao, new FolderDAOFileSystem(vault.toString()),
                new TagDAOFileSystem(noteDao));
        TagService tagSvc = new TagService(new TagDAOFileSystem(noteDao), noteDao);
        GraphBuilder graph = new GraphBuilder(noteSvc, tagSvc);

        GraphBuilder.Options opts = new GraphBuilder.Options(false, true, true);
        graph.buildGlobalGraph(opts);

        graph.invalidateNote(null);            // no-op, must not throw
        graph.invalidateNote("does-not-exist"); // no-op, must not throw
        graph.invalidateAll();

        GraphData after = graph.buildGlobalGraph(opts);
        long links = after.edges().stream().filter(e -> e.type() == GraphEdge.Type.LINK).count();
        assertEquals(1, links, "A->B still present after invalidateAll + rebuild");
    }
}
