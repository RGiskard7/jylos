package com.example.jylos.data.dao.filesystem;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.example.jylos.data.models.Note;
import com.example.jylos.data.models.Tag;
import com.example.jylos.data.models.ToDoNote;

/**
 * Parses and generates Obsidian-compatible YAML frontmatter in Markdown files.
 *
 * <h3>Frontmatter format</h3>
 * <pre>
 * ---
 * id: some-id
 * title: My Note
 * tags: [tag1, tag2]
 * aliases: [Alt Title]
 * date: 2026-05-31
 * ---
 *
 * Note body…
 * </pre>
 *
 * <h3>Round-trip guarantee</h3>
 * <ul>
 *   <li>All <em>known</em> system fields (id, title, created, modified, …) are
 *       mapped to {@link Note} properties.</li>
 *   <li>Every other field is preserved verbatim in
 *       {@link Note#getCustomProperties()}, written back on
 *       {@link #generate(Note)} in the same order.</li>
 *   <li>Both inline-array ({@code tags: [a, b]}) and block-sequence
 *       ({@code - item}) syntaxes are parsed correctly.</li>
 * </ul>
 *
 * @author Edu Díaz (RGiskard7)
 * @since 1.3.0
 */
public final class FrontmatterHandler {

    private static final String SEPARATOR = "---";
    private static final Pattern KEY_VALUE = Pattern.compile("^([a-zA-Z0-9_.\\-]+):\\s*(.*)$");

    /** Keys consumed by the fixed Note schema — never stored in customProperties. */
    private static final List<String> SYSTEM_KEYS = List.of(
            "id", "title", "created", "modified",
            "favorite", "pinned", "deleted", "deleted_date",
            "author", "source_url", "tags",
            "is_todo", "todo_due", "todo_completed");

    private FrontmatterHandler() {
        // utility class
    }

    // ------------------------------------------------------------------
    // Public API
    // ------------------------------------------------------------------

    /**
     * Parses a full Markdown file (frontmatter + body) into a {@link Note}.
     *
     * <p>System YAML fields are mapped to the Note's fixed properties.
     * All other fields land in {@link Note#getCustomProperties()}.</p>
     *
     * @param fileContent raw file content (may be null)
     * @return a populated Note; never null
     */
    public static Note parse(String fileContent) {
        if (fileContent == null || fileContent.isEmpty()) {
            return new Note("", "");
        }

        // File must start with "---" to have frontmatter
        if (!fileContent.startsWith(SEPARATOR)) {
            return parseWithoutFrontmatter(fileContent);
        }

        // Find the closing ---
        int secondSep = fileContent.indexOf('\n' + SEPARATOR, SEPARATOR.length());
        if (secondSep < 0) {
            return parseWithoutFrontmatter(fileContent);
        }

        String yamlBlock = fileContent.substring(SEPARATOR.length(), secondSep);
        String body = fileContent.substring(secondSep + 1 + SEPARATOR.length()).stripLeading();

        Map<String, String> all = parseYaml(yamlBlock);

        // ---- system fields ----
        String id = all.get("id");
        String title = all.get("title");
        String created = all.get("created");
        String modified = all.get("modified");

        boolean isToDo = "true".equalsIgnoreCase(all.get("is_todo"));
        Note note = isToDo
                ? new ToDoNote(id, title, body, created, modified,
                        all.get("todo_due"), all.get("todo_completed"))
                : new Note(id, title, body, created, modified);

        note.setFavorite("true".equalsIgnoreCase(all.get("favorite")));
        note.setPinned("true".equalsIgnoreCase(all.get("pinned")));
        note.setDeleted("true".equalsIgnoreCase(all.get("deleted")));
        note.setDeletedDate(all.get("deleted_date"));
        note.setAuthor(all.get("author"));
        note.setSourceUrl(all.get("source_url"));

        // ---- tags ----
        String tagsRaw = all.get("tags");
        if (tagsRaw != null && !tagsRaw.isBlank()) {
            for (String name : splitList(tagsRaw)) {
                note.addTag(new Tag(name));
            }
        }
        // inline #tags in body
        extractInlineTags(body, note);

        // ---- custom (non-system) properties ----
        Map<String, String> custom = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : all.entrySet()) {
            if (!SYSTEM_KEYS.contains(entry.getKey())) {
                custom.put(entry.getKey(), entry.getValue());
            }
        }
        note.setCustomProperties(custom);

        return note;
    }

    /**
     * Serializes a {@link Note} back to a full Markdown file string including
     * YAML frontmatter.
     *
     * <p>System fields are written first (in a fixed canonical order), then
     * {@link Note#getCustomProperties()} in their insertion order.</p>
     *
     * @param note the note to serialize; must not be null
     * @return full file content string
     */
    public static String generate(Note note) {
        StringBuilder sb = new StringBuilder();
        sb.append(SEPARATOR).append('\n');

        // ---- system fields ----
        appendIfNotNull(sb, "id", note.getId());
        appendIfNotNull(sb, "title", note.getTitle());
        appendIfNotNull(sb, "created", note.getCreatedDate());
        appendIfNotNull(sb, "modified", note.getModifiedDate());
        sb.append("favorite: ").append(note.isFavorite()).append('\n');
        sb.append("pinned: ").append(note.isPinned()).append('\n');
        sb.append("deleted: ").append(note.isDeleted()).append('\n');
        appendIfNotNull(sb, "deleted_date", note.getDeletedDate());
        appendIfNotNull(sb, "author", note.getAuthor());
        appendIfNotNull(sb, "source_url", note.getSourceUrl());

        List<Tag> tags = note.getTags();
        if (tags != null && !tags.isEmpty()) {
            sb.append("tags: [");
            for (int i = 0; i < tags.size(); i++) {
                sb.append(tags.get(i).getTitle());
                if (i < tags.size() - 1) sb.append(", ");
            }
            sb.append("]\n");
        }

        if (note instanceof ToDoNote todo) {
            sb.append("is_todo: true\n");
            appendIfNotNull(sb, "todo_due", todo.getToDoDue());
            appendIfNotNull(sb, "todo_completed", todo.getToDoCompleted());
        }

        // ---- custom properties (verbatim round-trip) ----
        Map<String, String> custom = note.getCustomProperties();
        if (custom != null) {
            for (Map.Entry<String, String> entry : custom.entrySet()) {
                appendIfNotNull(sb, entry.getKey(), entry.getValue());
            }
        }

        sb.append(SEPARATOR).append("\n\n");
        if (note.getContent() != null) {
            sb.append(note.getContent());
        }
        return sb.toString();
    }

    /**
     * Parses frontmatter and only the first {@code maxBodyChars} of the body.
     * Intended for note-list previews without reading entire large files.
     *
     * @param fileContent  file head (may be truncated)
     * @param maxBodyChars maximum body characters to keep
     * @return populated note with excerpted body
     */
    public static Note parseLightweight(String fileContent, int maxBodyChars) {
        if (fileContent == null || fileContent.isEmpty()) {
            return new Note("", "");
        }
        int limit = Math.max(0, maxBodyChars);
        if (!fileContent.startsWith(SEPARATOR)) {
            String body = limit > 0 && fileContent.length() > limit
                    ? fileContent.substring(0, limit)
                    : fileContent;
            return parseWithoutFrontmatter(body);
        }

        int secondSep = fileContent.indexOf('\n' + SEPARATOR, SEPARATOR.length());
        if (secondSep < 0) {
            String body = limit > 0 && fileContent.length() > limit
                    ? fileContent.substring(0, limit)
                    : fileContent;
            return parseWithoutFrontmatter(body);
        }

        String yamlBlock = fileContent.substring(SEPARATOR.length(), secondSep);
        String bodyRaw = fileContent.substring(secondSep + 1 + SEPARATOR.length()).stripLeading();
        String body = bodyRaw;
        if (limit > 0 && bodyRaw.length() > limit) {
            body = bodyRaw.substring(0, limit);
        }

        Map<String, String> all = parseYaml(yamlBlock);
        String id = all.get("id");
        String title = all.get("title");
        String created = all.get("created");
        String modified = all.get("modified");

        boolean isToDo = "true".equalsIgnoreCase(all.get("is_todo"));
        Note note = isToDo
                ? new ToDoNote(id, title, body, created, modified,
                        all.get("todo_due"), all.get("todo_completed"))
                : new Note(id, title, body, created, modified);

        note.setFavorite("true".equalsIgnoreCase(all.get("favorite")));
        note.setPinned("true".equalsIgnoreCase(all.get("pinned")));
        note.setDeleted("true".equalsIgnoreCase(all.get("deleted")));
        note.setDeletedDate(all.get("deleted_date"));
        note.setAuthor(all.get("author"));
        note.setSourceUrl(all.get("source_url"));

        String tagsRaw = all.get("tags");
        if (tagsRaw != null && !tagsRaw.isBlank()) {
            for (String name : splitList(tagsRaw)) {
                note.addTag(new Tag(name));
            }
        }
        extractInlineTags(body, note);

        Map<String, String> custom = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : all.entrySet()) {
            if (!SYSTEM_KEYS.contains(entry.getKey())) {
                custom.put(entry.getKey(), entry.getValue());
            }
        }
        note.setCustomProperties(custom);
        return note;
    }

    /**
     * Returns only the content body of a Markdown file, stripping any
     * YAML frontmatter block.
     *
     * <p>Useful when the caller only needs the user-visible text without
     * having to parse the whole note model.</p>
     *
     * @param fileContent raw file content
     * @return body string (never null)
     */
    public static String stripFrontmatter(String fileContent) {
        if (fileContent == null) return "";
        if (!fileContent.startsWith(SEPARATOR)) return fileContent;
        int secondSep = fileContent.indexOf('\n' + SEPARATOR, SEPARATOR.length());
        if (secondSep < 0) return fileContent;
        return fileContent.substring(secondSep + 1 + SEPARATOR.length()).stripLeading();
    }

    // ------------------------------------------------------------------
    // YAML parsing
    // ------------------------------------------------------------------

    /**
     * Parses a YAML block into a {@link LinkedHashMap} preserving insertion order.
     * Supports:
     * <ul>
     *   <li>Scalar values: {@code key: value}</li>
     *   <li>Inline arrays: {@code key: [a, b, c]}</li>
     *   <li>Block sequences: {@code key:\n  - a\n  - b}</li>
     *   <li>Quoted values (single and double quotes)</li>
     * </ul>
     *
     * @param yaml raw YAML block (text between the {@code ---} separators)
     * @return ordered map of key → raw-value-string
     */
    static Map<String, String> parseYaml(String yaml) {
        Map<String, String> result = new LinkedHashMap<>();
        if (yaml == null || yaml.isBlank()) return result;

        String[] lines = yaml.split("\n");
        String currentKey = null;
        List<String> currentList = null;

        for (String raw : lines) {
            String line = raw.stripTrailing();
            if (line.isBlank()) continue;

            // Block-sequence element "  - value"
            if (currentKey != null && currentList != null && line.matches("\\s+-.*")) {
                String item = line.replaceFirst("\\s+-\\s*", "").trim();
                item = unquote(item);
                currentList.add(item);
                result.put(currentKey, "[" + String.join(", ", currentList) + "]");
                continue;
            }

            Matcher m = KEY_VALUE.matcher(line.trim());
            if (m.matches()) {
                String key = m.group(1).trim();
                String value = m.group(2).trim();

                // Block-sequence start: "key:" with no inline value
                if (value.isEmpty()) {
                    currentKey = key;
                    currentList = new ArrayList<>();
                    result.put(key, "[]");
                    continue;
                }

                // Regular scalar or inline array
                currentKey = null;
                currentList = null;
                result.put(key, unquote(value));
            } else {
                // Unrecognised line ends any ongoing block sequence
                currentKey = null;
                currentList = null;
            }
        }
        return result;
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private static Note parseWithoutFrontmatter(String content) {
        String title = "Untitled";
        String body = content;
        String[] lines = content.split("\n", 2);
        if (lines[0].startsWith("# ")) {
            title = lines[0].substring(2).trim();
            body = lines.length > 1 ? lines[1] : "";
        }
        return new Note(title, body);
    }

    private static void appendIfNotNull(StringBuilder sb, String key, String value) {
        if (value != null) {
            sb.append(key).append(": ").append(quoteIfNeeded(value)).append('\n');
        }
    }

    /**
     * Splits a raw YAML list value (inline {@code [a, b]} or comma-separated)
     * into individual trimmed, non-empty strings.
     */
    static List<String> splitList(String raw) {
        if (raw == null || raw.isBlank()) return List.of();
        String stripped = raw.trim();
        if (stripped.startsWith("[") && stripped.endsWith("]")) {
            stripped = stripped.substring(1, stripped.length() - 1);
        }
        List<String> result = new ArrayList<>();
        for (String part : stripped.split(",")) {
            String t = unquote(part.trim());
            if (!t.isBlank()) result.add(t);
        }
        return result;
    }

    private static String unquote(String v) {
        if (v.length() >= 2) {
            if ((v.startsWith("\"") && v.endsWith("\""))
                    || (v.startsWith("'") && v.endsWith("'"))) {
                return v.substring(1, v.length() - 1);
            }
        }
        return v;
    }

    /**
     * Quotes a YAML scalar value if it contains characters that would break
     * plain YAML parsing ({@code :}, {@code #}, leading/trailing spaces, etc.).
     */
    private static String quoteIfNeeded(String value) {
        if (value == null) return "";
        // Inline arrays and booleans/numbers are safe as-is
        if (value.startsWith("[") || value.matches("-?\\d+(\\.\\d+)?")
                || "true".equals(value) || "false".equals(value)) {
            return value;
        }
        // Quote if contains colon-space, hash-space or leading/trailing whitespace
        if (value.contains(": ") || value.contains(" #") || !value.equals(value.trim())) {
            return '"' + value.replace("\\", "\\\\").replace("\"", "\\\"") + '"';
        }
        return value;
    }

    private static void extractInlineTags(String content, Note note) {
        if (content == null || content.isBlank()) return;
        Matcher m = Pattern.compile("(?<=\\s|^)#([a-zA-ZáéíóúÁÉÍÓÚñÑüÜ0-9_\\-\\/]+)(?=\\s|$)")
                .matcher(content);
        while (m.find()) {
            String name = m.group(1);
            boolean dup = note.getTags().stream()
                    .anyMatch(t -> t.getTitle().equalsIgnoreCase(name));
            if (!dup) note.addTag(new Tag(name));
        }
    }
}
