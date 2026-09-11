package com.example.jylos.plugin.builtin.publish;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.example.jylos.data.models.Note;

/**
 * Builds the folder/note tree the persistent sidebar (assets/nav.js) renders on
 * every published page, serialized once to assets/nav-data.js — a single shared
 * file every page loads via a real {@code <script src>}, instead of each of
 * potentially thousands of pages embedding its own copy of the whole vault's
 * structure. A classic (non-module) {@code <script src>} loads fine even when
 * the site is opened directly via {@code file://}; only {@code fetch()}/XHR are
 * rejected as cross-origin for local files — the same distinction that pushed
 * graph.html's own data inline in {@link VaultExporter} instead of fetching
 * graph.json (that one embeds inline because it is a single page, not a file
 * shared by every page — inlining a multi-thousand-note tree into every one of
 * those pages would multiply its size by the note count instead).
 */
final class NavTree {

    private NavTree() {
    }

    /** One folder in the tree — only what nav.js needs to render and to key localStorage collapse state on. */
    private static final class FolderNode {
        final String name;
        final String path;
        final List<FolderNode> folders = new ArrayList<>();
        final List<Note> notes = new ArrayList<>();

        FolderNode(String name, String path) {
            this.name = name;
            this.path = path;
        }
    }

    /**
     * Builds the tree from the same folder-path-by-note-id map and slugs
     * {@link VaultExporter} already computes, and serializes it as a plain
     * assignment — {@code window.__JYLOS_NAV__ = {...};} — never parsed as JSON
     * on its own, since it is always loaded through a real {@code <script>} tag.
     *
     * <p>Relies on {@code exportable} already being sorted by folder path then
     * title (the same order {@link VaultExporter} sorts it into before slug
     * registration) — folders and notes are appended to the tree in first-seen
     * order, so that sort is what keeps the rendered sidebar alphabetical
     * without this class re-sorting anything itself.</p>
     */
    static String buildScript(List<Note> exportable, Map<String, List<String>> folderPathByNoteId, NoteSlugs slugs) {
        FolderNode root = new FolderNode("", "");
        for (Note note : exportable) {
            List<String> path = folderPathByNoteId.getOrDefault(note.getId(), List.of());
            FolderNode current = root;
            StringBuilder pathSoFar = new StringBuilder();
            for (String segment : path) {
                if (pathSoFar.length() > 0) {
                    pathSoFar.append('/');
                }
                pathSoFar.append(segment);
                current = childFolder(current, segment, pathSoFar.toString());
            }
            current.notes.add(note);
        }

        StringBuilder json = new StringBuilder();
        json.append("window.__JYLOS_NAV__ = {\"notes\":");
        appendNotes(json, root.notes, slugs);
        json.append(",\"folders\":");
        appendFolders(json, root.folders, slugs);
        json.append("};");
        return json.toString();
    }

    private static FolderNode childFolder(FolderNode parent, String name, String path) {
        for (FolderNode child : parent.folders) {
            if (child.path.equals(path)) {
                return child;
            }
        }
        FolderNode child = new FolderNode(name, path);
        parent.folders.add(child);
        return child;
    }

    private static void appendNotes(StringBuilder json, List<Note> notes, NoteSlugs slugs) {
        json.append('[');
        for (int i = 0; i < notes.size(); i++) {
            if (i > 0) {
                json.append(',');
            }
            Note note = notes.get(i);
            json.append("{\"title\":").append(jsonString(note.getTitle()))
                    .append(",\"href\":").append(jsonString(slugs.pathFor(note.getId())))
                    .append('}');
        }
        json.append(']');
    }

    private static void appendFolders(StringBuilder json, List<FolderNode> folders, NoteSlugs slugs) {
        json.append('[');
        for (int i = 0; i < folders.size(); i++) {
            if (i > 0) {
                json.append(',');
            }
            FolderNode folder = folders.get(i);
            json.append("{\"name\":").append(jsonString(folder.name))
                    .append(",\"path\":").append(jsonString(folder.path))
                    .append(",\"notes\":");
            appendNotes(json, folder.notes, slugs);
            json.append(",\"folders\":");
            appendFolders(json, folder.folders, slugs);
            json.append('}');
        }
        json.append(']');
    }

    private static String jsonString(String value) {
        if (value == null) {
            return "null";
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
                case '<' -> sb.append("\\u003c"); // defuse "</script>" inside a title, same reasoning as graph.json
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        return sb.append('"').toString();
    }
}
