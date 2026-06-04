package com.example.jylos.graph;

import java.util.List;

/**
 * Immutable graph payload (nodes + edges) plus dependency-free JSON serialization
 * for the embedded WebView renderer.
 *
 * <p>JSON is built by hand to avoid pulling a serialization dependency into an
 * otherwise lean desktop app. All strings are escaped per the JSON spec.</p>
 *
 * @param nodes graph nodes
 * @param edges graph edges
 */
public record GraphData(List<GraphNode> nodes, List<GraphEdge> edges) {

    public static GraphData empty() {
        return new GraphData(List.of(), List.of());
    }

    /**
     * Serializes the graph to a compact JSON object: {@code {"nodes":[...],"edges":[...]}}.
     */
    public String toJson() {
        StringBuilder sb = new StringBuilder(Math.max(256, nodes.size() * 96));
        sb.append("{\"nodes\":[");
        for (int i = 0; i < nodes.size(); i++) {
            GraphNode n = nodes.get(i);
            if (i > 0) {
                sb.append(',');
            }
            sb.append("{\"id\":").append(quote(n.id()))
              .append(",\"label\":").append(quote(n.label()))
              .append(",\"type\":").append(quote(typeName(n.type())))
              .append(",\"group\":").append(quote(n.group() != null ? n.group() : ""))
              .append(",\"degree\":").append(n.degree())
              .append('}');
        }
        sb.append("],\"edges\":[");
        for (int i = 0; i < edges.size(); i++) {
            GraphEdge e = edges.get(i);
            if (i > 0) {
                sb.append(',');
            }
            sb.append("{\"source\":").append(quote(e.source()))
              .append(",\"target\":").append(quote(e.target()))
              .append(",\"type\":").append(quote(e.type() == GraphEdge.Type.TAG ? "tag" : "link"))
              .append('}');
        }
        sb.append("]}");
        return sb.toString();
    }

    private static String typeName(GraphNode.Type type) {
        return switch (type) {
            case TAG -> "tag";
            case GHOST -> "ghost";
            default -> "note";
        };
    }

    static String quote(String value) {
        if (value == null) {
            return "\"\"";
        }
        StringBuilder sb = new StringBuilder(value.length() + 2);
        sb.append('"');
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                case '\b' -> sb.append("\\b");
                case '\f' -> sb.append("\\f");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        sb.append('"');
        return sb.toString();
    }
}
