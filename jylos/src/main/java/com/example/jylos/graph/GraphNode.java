package com.example.jylos.graph;

/**
 * A single node in the knowledge graph.
 *
 * @param id     stable node id ({@code noteId} for notes, {@code "tag:" + title} for tags)
 * @param label  human-readable label shown next to the node
 * @param type   {@link Type} of node
 * @param group  grouping key used for coloring (folder path for notes, the tag itself for tags)
 * @param degree number of connected edges (drives node radius and font size)
 */
public record GraphNode(String id, String label, Type type, String group, int degree) {

    /** Kind of graph node. */
    public enum Type {
        /** An existing note in the vault. */
        NOTE,
        /** A {@code #tag} grouping node. */
        TAG,
        /** A link target with no matching note yet (Obsidian's hollow "unresolved" node). */
        GHOST
    }

    public GraphNode withDegree(int newDegree) {
        return new GraphNode(id, label, type, group, newDegree);
    }
}
