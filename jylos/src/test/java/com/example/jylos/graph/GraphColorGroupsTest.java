package com.example.jylos.graph;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

/** Colour groups as in Obsidian: queries tried in order, the first one a node matches gives its colour. */
class GraphColorGroupsTest {

    private static final List<GraphNode> NODES = List.of(
            new GraphNode("Trabajo/Acta.md", "Acta", GraphNode.Type.NOTE, "Trabajo", 1),
            new GraphNode("Trabajo/Plan.md", "Plan", GraphNode.Type.NOTE, "Trabajo", 1),
            new GraphNode("Casa/Compra.md", "Compra", GraphNode.Type.NOTE, "Casa", 1),
            new GraphNode("tag:urgente", "#urgente", GraphNode.Type.TAG, "urgente", 1),
            new GraphNode("ghost:idea", "Idea", GraphNode.Type.GHOST, "", 1));

    private static final Map<String, Set<String>> TAGS = Map.of(
            "Trabajo/Acta.md", Set.of("urgente"),
            "Casa/Compra.md", Set.of("Urgente", "casa"));

    @Test
    void theFirstGroupANodeMatchesGivesItsColour() {
        Map<String, String> colors = GraphColorGroups.assign(NODES, List.of(
                new GraphColorGroups.Group("tag:#urgente", "#ff0000"),
                new GraphColorGroups.Group("path:trabajo", "#00ff00")), TAGS::get);

        assertEquals(Map.of(
                "Trabajo/Acta.md", "#ff0000",
                "Casa/Compra.md", "#ff0000",
                "tag:urgente", "#ff0000",
                "Trabajo/Plan.md", "#00ff00"), colors);
    }

    @Test
    void eachKindOfQueryLooksWhereItSays() {
        assertEquals(Set.of("Trabajo/Plan.md"), matching("file:plan"));
        assertEquals(Set.of("Casa/Compra.md"), matching("path:casa/"));
        assertEquals(Set.of("Casa/Compra.md"), matching("tag:casa"));
        assertEquals(Set.of("Trabajo/Acta.md", "Trabajo/Plan.md"), matching("TRABAJO"), "a plain query: name or path");
        assertEquals(Set.of("ghost:idea"), matching("idea"));
    }

    /** Obsidian writes a value with spaces between quotes: {@code path:"30 Resources"}. */
    @Test
    void aQuotedValueIsReadWithoutItsQuotes() {
        assertEquals(Set.of("Trabajo/Acta.md", "Trabajo/Plan.md"), matching("path:\"trabajo/\""));
        assertEquals(Set.of("Trabajo/Plan.md"), matching("file:\"plan\""));
        assertEquals(Set.of("Casa/Compra.md"), matching("tag:\"#casa\""));
        assertEquals(Set.of("Trabajo/Acta.md", "Trabajo/Plan.md"), matching("\"trabajo\""), "a plain quoted query");
        assertEquals(Set.of(), matching("path:\"\""), "empty between the quotes: nothing");
    }

    @Test
    void anEmptyQueryColoursNothing() {
        assertEquals(Set.of(), matching("  "));
        assertEquals(Set.of(), matching("tag:"));
    }

    private static Set<String> matching(String query) {
        return GraphColorGroups.assign(NODES, List.of(new GraphColorGroups.Group(query, "#123456")), TAGS::get)
                .keySet();
    }
}
