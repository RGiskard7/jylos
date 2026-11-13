package com.example.jylos.graph;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.TreeSet;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.example.jylos.data.dao.filesystem.FolderDAOFileSystem;
import com.example.jylos.data.dao.filesystem.NoteDAOFileSystem;
import com.example.jylos.data.dao.filesystem.TagDAOFileSystem;
import com.example.jylos.service.NoteService;
import com.example.jylos.service.TagService;

/**
 * Attachments in the graph as in Obsidian: hidden unless "Attachments" is on, and then each
 * one is a node joined to the notes that embed or link it. A canvas is always a node.
 *
 * <pre>A embeds foto.png and links B; sola.png is linked by nobody; Lienzo.canvas</pre>
 */
class GraphAttachmentsTest {

    @TempDir
    Path vault;
    private GraphBuilder graph;

    @BeforeEach
    void setUp() throws Exception {
        Files.createDirectories(vault.resolve("img"));
        Files.writeString(vault.resolve("A.md"), "![[foto.png]] y [[B]], y [la sola](img/sola.png)", StandardCharsets.UTF_8);
        Files.writeString(vault.resolve("B.md"), "", StandardCharsets.UTF_8);
        Files.write(vault.resolve("img/foto.png"), new byte[] {1});
        Files.write(vault.resolve("img/sola.png"), new byte[] {1});
        Files.write(vault.resolve("otra.png"), new byte[] {1});
        Files.writeString(vault.resolve("Lienzo.canvas"), "{\"nodes\":[],\"edges\":[]}", StandardCharsets.UTF_8);
        NoteDAOFileSystem dao = new NoteDAOFileSystem(vault.toString());
        graph = new GraphBuilder(new NoteService(dao, new FolderDAOFileSystem(vault.toString())),
                new TagService(new TagDAOFileSystem(dao), dao));
    }

    @Test
    void attachmentsAreLeftOutUnlessAsked() {
        GraphData data = graph.buildGlobalGraph(new GraphBuilder.Options(false, true, true));

        assertEquals(Set.of("A.md", "B.md", "Lienzo.canvas"), ids(data));
        assertEquals(Set.of("A.md->B.md"), edges(data));
    }

    @Test
    void withAttachmentsEachIsANodeJoinedToTheNotesThatUseIt() {
        GraphData data = graph.buildGlobalGraph(new GraphBuilder.Options(false, true, true, true));

        assertEquals(Set.of("A.md", "B.md", "Lienzo.canvas", "img/foto.png", "img/sola.png", "otra.png"), ids(data));
        assertEquals(Set.of("A.md->B.md", "A.md->img/foto.png", "A.md->img/sola.png"), edges(data));
    }

    private static Set<String> ids(GraphData data) {
        Set<String> ids = new TreeSet<>();
        data.nodes().forEach(node -> ids.add(node.id()));
        return ids;
    }

    private static Set<String> edges(GraphData data) {
        Set<String> edges = new TreeSet<>();
        data.edges().forEach(edge -> edges.add(edge.source() + "->" + edge.target()));
        return edges;
    }
}
