package com.example.jylos.graph;

/**
 * A directed connection between two {@link GraphNode}s.
 *
 * @param source origin node id
 * @param target destination node id
 * @param type   {@link Type} of relationship
 */
public record GraphEdge(String source, String target, Type type) {

    /** Kind of relationship represented by the edge. */
    public enum Type {
        /** {@code [[wiki-link]]} or {@code [label](note)} between two notes. */
        LINK,
        /** Association between a note and one of its tags. */
        TAG
    }
}
