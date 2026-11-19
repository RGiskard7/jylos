package com.example.jylos.graph;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;

/**
 * The graph's colour groups, as in Obsidian: each group is a query and a colour; they are
 * tried in order and the first one a node matches gives it its colour. A group with an empty
 * query colours nothing.
 *
 * <p>Queries: {@code tag:name} (the note has the tag, or it is that tag's node),
 * {@code path:text} (in the note's path), {@code file:text} (in its name), or plain text
 * (in the name or the path). Matching ignores case.</p>
 *
 * @author Edu Díaz (RGiskard7)
 * @since 2.6.0
 */
public final class GraphColorGroups {

    /** A colour group: the query and the colour, as {@code #rrggbb}. */
    public record Group(String query, String color) {
    }

    private GraphColorGroups() {
    }

    /**
     * The colour of every node some group matches (nodes no group matches are left out).
     *
     * @param tagsOf the tags of a note, by id (null or empty when it has none)
     */
    public static Map<String, String> assign(List<GraphNode> nodes, List<Group> groups,
            Function<String, ? extends Collection<String>> tagsOf) {
        Map<String, String> colors = new LinkedHashMap<>();
        for (GraphNode node : nodes) {
            for (Group group : groups) {
                if (group.color() != null && matches(node, group.query(), tagsOf)) {
                    colors.put(node.id(), group.color());
                    break;
                }
            }
        }
        return colors;
    }

    private static boolean matches(GraphNode node, String query,
            Function<String, ? extends Collection<String>> tagsOf) {
        String q = query == null ? "" : query.strip().toLowerCase(Locale.ROOT);
        String label = node.label() == null ? "" : node.label().toLowerCase(Locale.ROOT);
        String path = node.id() == null ? "" : node.id().replace('\\', '/').toLowerCase(Locale.ROOT);
        if (q.startsWith("tag:")) {
            String tag = q.substring(4).strip();
            tag = tag.startsWith("#") ? tag.substring(1) : tag;
            if (tag.isEmpty()) {
                return false;
            }
            if (node.type() == GraphNode.Type.TAG) {
                return label.equals("#" + tag);
            }
            Collection<String> tags = node.type() == GraphNode.Type.NOTE ? tagsOf.apply(node.id()) : null;
            if (tags == null) {
                return false;
            }
            for (String t : tags) {
                if (t != null && t.toLowerCase(Locale.ROOT).replaceFirst("^#", "").equals(tag)) {
                    return true;
                }
            }
            return false;
        }
        if (q.startsWith("path:")) {
            String text = q.substring(5).strip();
            return !text.isEmpty() && node.type() != GraphNode.Type.TAG && path.contains(text);
        }
        if (q.startsWith("file:")) {
            String text = q.substring(5).strip();
            return !text.isEmpty() && node.type() != GraphNode.Type.TAG && label.contains(text);
        }
        return !q.isEmpty() && (label.contains(q) || (node.type() != GraphNode.Type.TAG && path.contains(q)));
    }
}
