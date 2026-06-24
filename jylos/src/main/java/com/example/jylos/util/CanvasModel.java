package com.example.jylos.util;

import java.util.ArrayList;
import java.util.List;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Parser for the open <a href="https://jsoncanvas.org">JSON Canvas</a> format used by
 * Obsidian {@code .canvas} files: a flat list of positioned {@code nodes} and the
 * {@code edges} connecting them.
 *
 * <p>Parsing is lenient and defensive — unknown node types and malformed entries are
 * skipped rather than failing the whole document, so a canvas authored by Obsidian (or
 * a future spec revision) still opens. This class only reads; rendering lives in
 * {@code CanvasView}.</p>
 *
 * @author Edu Díaz (RGiskard7)
 * @since 2.2.0
 */
public final class CanvasModel {

    private CanvasModel() {
    }

    /** A whole canvas: its nodes and the edges between them. */
    public record CanvasDoc(List<CanvasNode> nodes, List<CanvasEdge> edges) {
        public boolean isEmpty() {
            return nodes.isEmpty() && edges.isEmpty();
        }
    }

    /**
     * A canvas node. {@code type} is one of {@code text}, {@code file}, {@code link} or
     * {@code group}; the payload field used depends on it ({@link #text}/{@link #file}/
     * {@link #url}). Coordinates and sizes are in canvas (world) units.
     */
    public record CanvasNode(String id, String type, double x, double y, double width, double height,
            String text, String file, String url, String label, String color) {
    }

    /** A connection between two nodes, optionally anchored to a side and labelled. */
    public record CanvasEdge(String id, String fromNode, String toNode,
            String fromSide, String toSide, String label, String color) {
    }

    /**
     * Parses {@code json} into a {@link CanvasDoc}. Returns an empty document for null,
     * blank or invalid input rather than throwing.
     */
    public static CanvasDoc parse(String json) {
        JsonObject obj = parseObject(json);
        List<CanvasNode> nodes = new ArrayList<>();
        List<CanvasEdge> edges = new ArrayList<>();
        parseNodes(obj, nodes);
        parseEdges(obj, edges);
        return new CanvasDoc(nodes, edges);
    }

    /** Parses {@code json} to a root object, or an empty object for null/blank/invalid input. */
    private static JsonObject parseObject(String json) {
        if (json == null || json.isBlank()) {
            return new JsonObject();
        }
        try {
            JsonElement root = JsonParser.parseString(json);
            return root.isJsonObject() ? root.getAsJsonObject() : new JsonObject();
        } catch (Exception e) {
            return new JsonObject();
        }
    }

    /**
     * A mutable canvas backed by its original JSON, so edits (e.g. moving a node) update
     * only the touched fields and {@link #toJson()} preserves everything else — including
     * keys this app does not understand. This is what makes saving a {@code .canvas}
     * round-trip safe with Obsidian.
     */
    public static final class Document {

        private final JsonObject root;

        private Document(JsonObject root) {
            this.root = root;
        }

        /** Parses {@code json} into an editable document (empty for null/blank/invalid). */
        public static Document parse(String json) {
            JsonObject obj = parseObject(json);
            if (!obj.has("nodes")) {
                obj.add("nodes", new JsonArray());
            }
            if (!obj.has("edges")) {
                obj.add("edges", new JsonArray());
            }
            return new Document(obj);
        }

        public List<CanvasNode> nodes() {
            List<CanvasNode> out = new ArrayList<>();
            parseNodes(root, out);
            return out;
        }

        public List<CanvasEdge> edges() {
            List<CanvasEdge> out = new ArrayList<>();
            parseEdges(root, out);
            return out;
        }

        public boolean isEmpty() {
            return nodes().isEmpty() && edges().isEmpty();
        }

        /** Updates a node's position (rounded to integers, as Obsidian stores them). */
        public void moveNode(String id, double x, double y) {
            if (id == null || !root.get("nodes").isJsonArray()) {
                return;
            }
            for (JsonElement el : root.getAsJsonArray("nodes")) {
                if (el.isJsonObject() && id.equals(str(el.getAsJsonObject(), "id"))) {
                    JsonObject n = el.getAsJsonObject();
                    n.addProperty("x", Math.round(x));
                    n.addProperty("y", Math.round(y));
                    return;
                }
            }
        }

        /** Adds a new text node and returns its generated id. */
        public String addTextNode(double x, double y, double width, double height, String text) {
            JsonObject n = newNode("text", x, y, width, height);
            n.addProperty("text", text == null ? "" : text);
            root.getAsJsonArray("nodes").add(n);
            return n.get("id").getAsString();
        }

        /** Adds a new link (URL) node and returns its generated id. */
        public String addLinkNode(double x, double y, double width, double height, String url) {
            JsonObject n = newNode("link", x, y, width, height);
            n.addProperty("url", url == null ? "" : url);
            root.getAsJsonArray("nodes").add(n);
            return n.get("id").getAsString();
        }

        /** Adds a new group node (labelled rectangle) and returns its generated id. */
        public String addGroupNode(double x, double y, double width, double height, String label) {
            JsonObject n = newNode("group", x, y, width, height);
            if (label != null && !label.isEmpty()) {
                n.addProperty("label", label);
            }
            root.getAsJsonArray("nodes").add(n);
            return n.get("id").getAsString();
        }

        /** Builds a node object with a fresh id and the common geometry fields. */
        private JsonObject newNode(String type, double x, double y, double width, double height) {
            JsonObject n = new JsonObject();
            n.addProperty("id", newId());
            n.addProperty("type", type);
            n.addProperty("x", Math.round(x));
            n.addProperty("y", Math.round(y));
            n.addProperty("width", Math.round(width));
            n.addProperty("height", Math.round(height));
            return n;
        }

        /** Updates a node's size (rounded to integers, as Obsidian stores them). */
        public void resizeNode(String id, double width, double height) {
            forNode(id, n -> {
                n.addProperty("width", Math.round(width));
                n.addProperty("height", Math.round(height));
            });
        }

        /** Sets (or clears, when {@code color} is null/blank) a node's color. */
        public void setNodeColor(String id, String color) {
            forNode(id, n -> applyColorProperty(n, color));
        }

        /** Sets (or clears, when {@code color} is null/blank) an edge's color. */
        public void setEdgeColor(String id, String color) {
            if (id == null || !root.get("edges").isJsonArray()) {
                return;
            }
            for (JsonElement el : root.getAsJsonArray("edges")) {
                if (el.isJsonObject() && id.equals(str(el.getAsJsonObject(), "id"))) {
                    applyColorProperty(el.getAsJsonObject(), color);
                    return;
                }
            }
        }

        private static void applyColorProperty(JsonObject o, String color) {
            if (color == null || color.isBlank()) {
                o.remove("color");
            } else {
                o.addProperty("color", color);
            }
        }

        /** Applies {@code action} to the node object with the given id, if present. */
        private void forNode(String id, java.util.function.Consumer<JsonObject> action) {
            if (id == null || !root.get("nodes").isJsonArray()) {
                return;
            }
            for (JsonElement el : root.getAsJsonArray("nodes")) {
                if (el.isJsonObject() && id.equals(str(el.getAsJsonObject(), "id"))) {
                    action.accept(el.getAsJsonObject());
                    return;
                }
            }
        }

        /** Updates the {@code text} of a text node. */
        public void setNodeText(String id, String text) {
            if (id == null) {
                return;
            }
            for (JsonElement el : root.getAsJsonArray("nodes")) {
                if (el.isJsonObject() && id.equals(str(el.getAsJsonObject(), "id"))) {
                    el.getAsJsonObject().addProperty("text", text == null ? "" : text);
                    return;
                }
            }
        }

        /**
         * Adds an edge between two nodes (optionally anchored to a side) and returns its id.
         * Blank sides are omitted so Obsidian falls back to its automatic anchoring.
         */
        public String addEdge(String fromNode, String fromSide, String toNode, String toSide) {
            JsonObject e = new JsonObject();
            String id = newId();
            e.addProperty("id", id);
            e.addProperty("fromNode", fromNode);
            if (fromSide != null && !fromSide.isEmpty()) {
                e.addProperty("fromSide", fromSide);
            }
            e.addProperty("toNode", toNode);
            if (toSide != null && !toSide.isEmpty()) {
                e.addProperty("toSide", toSide);
            }
            root.getAsJsonArray("edges").add(e);
            return id;
        }

        /** Removes a single edge by id. */
        public void removeEdge(String id) {
            if (id == null) {
                return;
            }
            root.add("edges", filtered(root.getAsJsonArray("edges"), o -> !id.equals(str(o, "id"))));
        }

        /** Removes a node and every edge attached to it. */
        public void removeNode(String id) {
            if (id == null) {
                return;
            }
            root.add("nodes", filtered(root.getAsJsonArray("nodes"),
                    o -> !id.equals(str(o, "id"))));
            root.add("edges", filtered(root.getAsJsonArray("edges"),
                    o -> !id.equals(str(o, "fromNode")) && !id.equals(str(o, "toNode"))));
        }

        /** Returns a new array keeping only the object elements that satisfy {@code keep}. */
        private static JsonArray filtered(JsonArray array, java.util.function.Predicate<JsonObject> keep) {
            JsonArray out = new JsonArray();
            for (JsonElement el : array) {
                if (el.isJsonObject() && keep.test(el.getAsJsonObject())) {
                    out.add(el);
                }
            }
            return out;
        }

        private static String newId() {
            return java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        }

        /** Serializes the canvas back to pretty-printed JSON, preserving unknown fields. */
        public String toJson() {
            return new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(root);
        }
    }

    private static void parseNodes(JsonObject obj, List<CanvasNode> out) {
        if (!obj.has("nodes") || !obj.get("nodes").isJsonArray()) {
            return;
        }
        for (JsonElement el : obj.getAsJsonArray("nodes")) {
            if (!el.isJsonObject()) {
                continue;
            }
            JsonObject n = el.getAsJsonObject();
            String id = str(n, "id");
            if (id.isEmpty()) {
                continue;
            }
            out.add(new CanvasNode(
                    id,
                    str(n, "type"),
                    num(n, "x"), num(n, "y"),
                    num(n, "width"), num(n, "height"),
                    str(n, "text"), str(n, "file"), str(n, "url"),
                    str(n, "label"), str(n, "color")));
        }
    }

    private static void parseEdges(JsonObject obj, List<CanvasEdge> out) {
        if (!obj.has("edges") || !obj.get("edges").isJsonArray()) {
            return;
        }
        for (JsonElement el : obj.getAsJsonArray("edges")) {
            if (!el.isJsonObject()) {
                continue;
            }
            JsonObject e = el.getAsJsonObject();
            String from = str(e, "fromNode");
            String to = str(e, "toNode");
            if (from.isEmpty() || to.isEmpty()) {
                continue;
            }
            out.add(new CanvasEdge(
                    str(e, "id"), from, to,
                    str(e, "fromSide"), str(e, "toSide"),
                    str(e, "label"), str(e, "color")));
        }
    }

    /** Reads a string member, returning "" when absent or not a primitive. */
    private static String str(JsonObject obj, String key) {
        JsonElement el = obj.get(key);
        return el != null && el.isJsonPrimitive() ? el.getAsString() : "";
    }

    /** Reads a numeric member, returning 0 when absent or not a number. */
    private static double num(JsonObject obj, String key) {
        JsonElement el = obj.get(key);
        try {
            return el != null && el.isJsonPrimitive() ? el.getAsDouble() : 0;
        } catch (NumberFormatException ex) {
            return 0;
        }
    }
}
