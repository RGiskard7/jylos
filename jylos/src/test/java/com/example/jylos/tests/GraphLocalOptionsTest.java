package com.example.jylos.tests;

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
import com.example.jylos.graph.GraphBuilder;
import com.example.jylos.graph.GraphData;
import com.example.jylos.graph.GraphEdge;
import com.example.jylos.graph.GraphNode;
import com.example.jylos.service.NoteService;
import com.example.jylos.service.TagService;

/**
 * The local graph as in Obsidian: from the open note, as many hops as the depth (1 to 5),
 * following outgoing links, incoming links or both, and showing the links between the
 * neighbours only when asked.
 *
 * <pre>A → B, C → A, B → D, B → C, E (alone), D → F</pre>
 */
class GraphLocalOptionsTest {

    @TempDir
    Path vault;
    private GraphBuilder graph;

    @BeforeEach
    void vault() throws Exception {
        write("A", "[[B]]");
        write("B", "[[D]] [[C]]");
        write("C", "[[A]]");
        write("D", "[[F]]");
        write("E", "sola");
        write("F", "");
        NoteDAOFileSystem dao = new NoteDAOFileSystem(vault.toString());
        graph = new GraphBuilder(new NoteService(dao, new FolderDAOFileSystem(vault.toString())),
                new TagService(new TagDAOFileSystem(dao), dao));
    }

    @Test
    void depthOneWithBothDirectionsTakesTheDirectNeighboursAndOnlyTheLinksToThem() {
        GraphData local = local(1, true, true, false);
        assertEquals(Set.of("A", "B", "C"), labels(local));
        assertEquals(Set.of("A->B", "C->A"), edges(local), "B->C joins two neighbours: not without the option");
    }

    @Test
    void theLinksBetweenNeighboursShowOnlyWithThatOption() {
        assertEquals(Set.of("A->B", "C->A", "B->C"), edges(local(1, true, true, true)));
    }

    @Test
    void outgoingOnlyFollowsLinksTheNoteMakesAndIncomingOnlyTheOnesItReceives() {
        assertEquals(Set.of("A", "B"), labels(local(1, true, false, false)));
        assertEquals(Set.of("A", "C"), labels(local(1, false, true, false)));
        assertEquals(Set.of("A", "B", "D", "C", "F"), labels(local(3, true, false, false)),
                "3 hops out: A, B, then D and C, then F");
    }

    @Test
    void depthGrowsTheNeighbourhoodAndIsKeptBetween1And5() {
        assertEquals(Set.of("A", "B", "C", "D"), labels(local(2, true, true, false)));
        assertEquals(labels(local(1, true, true, false)), labels(local(0, true, true, false)));
        assertEquals(labels(local(5, true, true, false)), labels(local(9, true, true, false)));
    }

    @Test
    void theOpenNoteIsAlwaysThereEvenAlone() {
        GraphData local = graph.buildLocalGraph(id("E"), new GraphBuilder.LocalOptions(1, true, true, false),
                new GraphBuilder.Options(false, true, false));
        assertEquals(Set.of("E"), labels(local));
    }

    private GraphData local(int depth, boolean outgoing, boolean incoming, boolean neighbourLinks) {
        return graph.buildLocalGraph(id("A"), new GraphBuilder.LocalOptions(depth, outgoing, incoming, neighbourLinks),
                GraphBuilder.Options.defaults());
    }

    private void write(String name, String body) throws Exception {
        Files.writeString(vault.resolve(name + ".md"), body, StandardCharsets.UTF_8);
    }

    private static String id(String name) {
        return name + ".md";
    }

    private static Set<String> labels(GraphData data) {
        Set<String> out = new TreeSet<>();
        for (GraphNode node : data.nodes()) {
            out.add(node.label());
        }
        return out;
    }

    private static Set<String> edges(GraphData data) {
        Set<String> out = new TreeSet<>();
        for (GraphEdge edge : data.edges()) {
            out.add(edge.source().replace(".md", "") + "->" + edge.target().replace(".md", ""));
        }
        return out;
    }
}
