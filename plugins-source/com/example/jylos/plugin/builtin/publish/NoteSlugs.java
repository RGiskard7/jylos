package com.example.jylos.plugin.builtin.publish;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Maps each note id to a stable, filesystem/URL-safe relative path under
 * {@code notes/}, mirroring the vault's own folder structure. Built once per
 * export (see {@link VaultExporter}), then used both to write each note's HTML
 * file at that path and to rewrite wiki-links pointing at it.
 */
final class NoteSlugs {

    private final Map<String, String> pathsByNoteId = new LinkedHashMap<>();

    /**
     * Registers one note's output path. {@code folderSegments} is the note's
     * folder path, root-to-leaf (empty for a note at the vault root); {@code title}
     * is the note's own title. Collisions — two different notes computing the same
     * path, e.g. two notes literally titled "Duplicate" in the same folder — are
     * resolved by appending {@code -2}, {@code -3}, ... to the second and later
     * note registered for that path, so no note's page is ever silently
     * overwritten by another's.
     */
    void register(String noteId, List<String> folderSegments, String title) {
        StringBuilder base = new StringBuilder("notes/");
        for (String segment : folderSegments) {
            base.append(sanitize(segment)).append('/');
        }
        base.append(sanitize(title));

        String candidate = base + ".html";
        int suffix = 2;
        while (pathsByNoteId.containsValue(candidate)) {
            candidate = base + "-" + suffix + ".html";
            suffix++;
        }
        pathsByNoteId.put(noteId, candidate);
    }

    /** The relative path (from the export root) registered for this note id, or {@code null} if none was. */
    String pathFor(String noteId) {
        return pathsByNoteId.get(noteId);
    }

    /** Every registered note id → relative path, in registration order. */
    Map<String, String> asMap() {
        return pathsByNoteId;
    }

    /**
     * A filesystem- and URL-safe path segment: strips characters invalid on
     * Windows paths or meaningful in a URL, collapses whitespace and repeated
     * hyphens, trims leading/trailing hyphens. Never returns blank — a title made
     * entirely of stripped characters (or blank to begin with) falls back to
     * {@code "untitled"} rather than collapsing the path to {@code "notes/.html"}
     * or a bare directory separator.
     */
    static String sanitize(String raw) {
        if (raw == null || raw.isBlank()) {
            return "untitled";
        }
        String cleaned = raw.trim()
                .replaceAll("[\\\\/:*?\"<>|#]", "-")
                .replaceAll("\\s+", "-")
                .replaceAll("-{2,}", "-")
                .replaceAll("^-+|-+$", "");
        return cleaned.isBlank() ? "untitled" : cleaned;
    }
}
