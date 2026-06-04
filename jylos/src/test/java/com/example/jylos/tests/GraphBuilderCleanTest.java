package com.example.jylos.tests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
import com.example.jylos.graph.GraphBuilder;
import com.example.jylos.graph.GraphData;
import com.example.jylos.graph.GraphNode;
import com.example.jylos.service.NoteService;
import com.example.jylos.service.TagService;

/**
 * The graph (tags off, the default) must contain only note nodes and ghost nodes
 * for unresolved wiki-links — never URLs, anchors, inline #tags or attachments.
 */
class GraphBuilderCleanTest {

    @Test
    void globalGraphHasNoJunkNodes(@TempDir Path vault) throws Exception {
        Files.writeString(vault.resolve("Note A.md"), String.join("\n",
                "# Note A",
                "[[Note B]] and [[folder/Note C#H|alias]]",
                "[[Ghost Note]]",
                "[g](https://google.com) [w](www.x.com) [s](#sec) [m](mailto:a@b.com)",
                "![p](diagram.png) [d](report.pdf)",
                "#proyecto #dfsdf"), StandardCharsets.UTF_8);
        Files.writeString(vault.resolve("Note B.md"), "# Note B\n", StandardCharsets.UTF_8);
        Files.createDirectories(vault.resolve("folder"));
        Files.writeString(vault.resolve("folder/Note C.md"), "# Note C\n", StandardCharsets.UTF_8);

        NoteDAOFileSystem noteDao = new NoteDAOFileSystem(vault.toString());
        NoteService noteSvc = new NoteService(noteDao, new FolderDAOFileSystem(vault.toString()),
                new TagDAOFileSystem(noteDao));
        TagService tagSvc = new TagService(new TagDAOFileSystem(noteDao), noteDao);
        GraphBuilder graph = new GraphBuilder(noteSvc, tagSvc);

        GraphData data = graph.buildGlobalGraph(new GraphBuilder.Options(false, true, true));

        Set<String> labels = data.nodes().stream().map(GraphNode::label).collect(Collectors.toSet());
        assertEquals(Set.of("Note A", "Note B", "Note C", "Ghost Note"), labels,
                "only the 3 notes + 1 ghost; got: " + labels);

        for (GraphNode n : data.nodes()) {
            assertTrue(n.type() == GraphNode.Type.NOTE || n.type() == GraphNode.Type.GHOST,
                    "no TAG nodes with tags disabled: " + n.label());
        }
        for (String junk : new String[] { "proyecto", "dfsdf", "google", "x.com", "sec",
                "diagram", "report", "mailto", "#" }) {
            assertFalse(labels.stream().anyMatch(l -> l.toLowerCase().contains(junk)),
                    "junk node leaked: " + junk);
        }
    }
}
