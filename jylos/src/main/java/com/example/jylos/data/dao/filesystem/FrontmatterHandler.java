package com.example.jylos.data.dao.filesystem;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import com.example.jylos.data.models.Note;
import com.example.jylos.data.models.Tag;
import com.example.jylos.data.models.ToDoNote;

/**
 * Parses and generates Obsidian-compatible YAML frontmatter in Markdown files.
 *
 * <p>Known note fields are mapped to {@link Note} properties. Every non-system
 * frontmatter entry is preserved through round-trips, including nested YAML
 * structures that the flat properties UI cannot edit directly.</p>
 */
public final class FrontmatterHandler {

    private static final String SEPARATOR = "---";

    /** Keys consumed by the fixed Note schema — never exposed as custom properties. */
    private static final List<String> SYSTEM_KEYS = List.of(
            "id", "title", "created", "modified",
            "favorite", "pinned", "deleted", "deleted_date",
            "author", "source_url", "tags", "status", "private",
            "is_todo", "todo_due", "todo_completed");

    private FrontmatterHandler() {
        // utility class
    }

    public static Note parse(String fileContent) {
        if (fileContent == null || fileContent.isEmpty()) {
            return new Note("", "");
        }
        if (!fileContent.startsWith(SEPARATOR)) {
            return parseWithoutFrontmatter(fileContent);
        }

        int secondSep = fileContent.indexOf('\n' + SEPARATOR, SEPARATOR.length());
        if (secondSep < 0) {
            return parseWithoutFrontmatter(fileContent);
        }

        String yamlBlock = fileContent.substring(SEPARATOR.length(), secondSep);
        String body = fileContent.substring(secondSep + 1 + SEPARATOR.length()).stripLeading();
        return buildNote(parseYaml(yamlBlock), body);
    }

    public static String generate(Note note) {
        Map<String, Object> root = new LinkedHashMap<>();

        putIfNotBlank(root, "id", note.getId());
        putIfNotBlank(root, "title", note.getTitle());
        putIfNotBlank(root, "created", note.getCreatedDate());
        putIfNotBlank(root, "modified", note.getModifiedDate());
        root.put("favorite", note.isFavorite());
        root.put("pinned", note.isPinned());
        root.put("deleted", note.isDeleted());
        putIfNotBlank(root, "deleted_date", note.getDeletedDate());
        putIfNotBlank(root, "status", note.getStatus());
        if (note.isPrivate()) {
            root.put("private", true);
        }
        putIfNotBlank(root, "author", note.getAuthor());
        putIfNotBlank(root, "source_url", note.getSourceUrl());

        List<String> tags = note.getTags().stream()
                .map(Tag::getTitle)
                .filter(title -> title != null && !title.isBlank())
                .toList();
        if (!tags.isEmpty()) {
            root.put("tags", tags);
        }

        if (note instanceof ToDoNote todo) {
            root.put("is_todo", true);
            putIfNotBlank(root, "todo_due", todo.getToDoDue());
            putIfNotBlank(root, "todo_completed", todo.getToDoCompleted());
        }

        Map<String, Object> structured = note.getStructuredFrontmatterProperties();
        Set<String> mirroredKeys = note.getDisplayableFrontmatterPropertyKeys();
        Map<String, String> custom = note.getCustomProperties() != null
                ? note.getCustomProperties()
                : Map.of();

        if (structured != null) {
            for (Map.Entry<String, Object> entry : structured.entrySet()) {
                String key = entry.getKey();
                if (key == null || SYSTEM_KEYS.contains(key)) {
                    continue;
                }
                if (mirroredKeys.contains(key) && !custom.containsKey(key)) {
                    continue;
                }
                root.put(key, deepCopyYamlValue(entry.getValue()));
            }
        }

        for (Map.Entry<String, String> entry : custom.entrySet()) {
            String key = entry.getKey();
            if (key == null || key.isBlank() || SYSTEM_KEYS.contains(key)) {
                continue;
            }
            Object preserved = structured != null ? structured.get(key) : null;
            String value = entry.getValue() != null ? entry.getValue() : "";
            if (preserved != null && value.equals(toDisplayableCustomValue(preserved))) {
                root.put(key, deepCopyYamlValue(preserved));
            } else {
                root.put(key, parseCustomPropertyValue(value));
            }
        }

        StringBuilder sb = new StringBuilder();
        sb.append(SEPARATOR).append('\n');
        sb.append(trimTrailingDocumentMarker(newYamlDumper().dump(root)));
        sb.append(SEPARATOR).append("\n\n");
        if (note.getContent() != null) {
            sb.append(note.getContent());
        }
        return sb.toString();
    }

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
        String body = limit > 0 && bodyRaw.length() > limit
                ? bodyRaw.substring(0, limit)
                : bodyRaw;
        return buildNote(parseYaml(yamlBlock), body);
    }

    public static String stripFrontmatter(String fileContent) {
        if (fileContent == null) return "";
        if (!fileContent.startsWith(SEPARATOR)) return fileContent;
        int secondSep = fileContent.indexOf('\n' + SEPARATOR, SEPARATOR.length());
        if (secondSep < 0) return fileContent;
        return fileContent.substring(secondSep + 1 + SEPARATOR.length()).stripLeading();
    }

    static List<String> splitList(String raw) {
        if (raw == null || raw.isBlank()) return List.of();
        String stripped = raw.trim();
        if (stripped.startsWith("[") && stripped.endsWith("]")) {
            stripped = stripped.substring(1, stripped.length() - 1);
        }
        List<String> result = new ArrayList<>();
        for (String part : stripped.split(",")) {
            String value = part.trim();
            if (!value.isBlank()) {
                result.add(value);
            }
        }
        return result;
    }

    private static Note buildNote(Map<String, Object> all, String body) {
        String id = stringValue(all.get("id"));
        String title = stringValue(all.get("title"));
        String created = stringValue(all.get("created"));
        String modified = stringValue(all.get("modified"));

        boolean isToDo = booleanValue(all.get("is_todo"));
        Note note = isToDo
                ? new ToDoNote(id, title, body, created, modified,
                        stringValue(all.get("todo_due")), stringValue(all.get("todo_completed")))
                : new Note(id, title, body, created, modified);

        note.setFavorite(booleanValue(all.get("favorite")));
        note.setPinned(booleanValue(all.get("pinned")));
        note.setDeleted(booleanValue(all.get("deleted")));
        note.setDeletedDate(stringValue(all.get("deleted_date")));
        note.setStatus(stringValue(all.get("status")));
        note.setPrivate(booleanValue(all.get("private")));
        note.setAuthor(stringValue(all.get("author")));
        note.setSourceUrl(stringValue(all.get("source_url")));

        for (String name : toStringList(all.get("tags"))) {
            note.addTag(new Tag(name));
        }
        extractInlineTags(body, note);

        Map<String, String> custom = new LinkedHashMap<>();
        Map<String, Object> structured = new LinkedHashMap<>();
        Set<String> displayable = new LinkedHashSet<>();
        for (Map.Entry<String, Object> entry : all.entrySet()) {
            String key = entry.getKey();
            if (key == null || SYSTEM_KEYS.contains(key)) {
                continue;
            }
            Object raw = deepCopyYamlValue(entry.getValue());
            structured.put(key, raw);
            String displayValue = toDisplayableCustomValue(raw);
            if (displayValue != null) {
                custom.put(key, displayValue);
                displayable.add(key);
            }
        }
        note.setCustomProperties(custom);
        note.setStructuredFrontmatterProperties(structured);
        note.setDisplayableFrontmatterPropertyKeys(displayable);
        return note;
    }

    private static Map<String, Object> parseYaml(String yaml) {
        if (yaml == null || yaml.isBlank()) {
            return new LinkedHashMap<>();
        }
        Object loaded = newYamlParser().load(yaml);
        if (!(loaded instanceof Map<?, ?> rawMap)) {
            return new LinkedHashMap<>();
        }
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : rawMap.entrySet()) {
            if (entry.getKey() != null) {
                result.put(String.valueOf(entry.getKey()), deepCopyYamlValue(entry.getValue()));
            }
        }
        return result;
    }

    private static Yaml newYamlParser() {
        return new Yaml(new SafeConstructor(new LoaderOptions()));
    }

    private static Yaml newYamlDumper() {
        return new Yaml(createDumperOptions());
    }

    private static String trimTrailingDocumentMarker(String dumpedYaml) {
        if (dumpedYaml == null || dumpedYaml.isEmpty()) {
            return "";
        }
        String normalized = dumpedYaml.replace("\r\n", "\n");
        if (normalized.endsWith("...\n")) {
            normalized = normalized.substring(0, normalized.length() - 4);
        }
        if (!normalized.endsWith("\n")) {
            normalized = normalized + "\n";
        }
        return normalized;
    }

    private static Object parseCustomPropertyValue(String value) {
        String trimmed = value != null ? value.trim() : "";
        if (trimmed.isEmpty()) {
            return "";
        }
        if ("true".equalsIgnoreCase(trimmed)) {
            return true;
        }
        if ("false".equalsIgnoreCase(trimmed)) {
            return false;
        }
        if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
            return splitList(trimmed);
        }
        try {
            if (trimmed.contains(".")) {
                return Double.parseDouble(trimmed);
            }
            return Long.parseLong(trimmed);
        } catch (NumberFormatException ignored) {
            return value;
        }
    }

    private static String toDisplayableCustomValue(Object value) {
        if (value == null) {
            return "";
        }
        if (value instanceof String || value instanceof Number || value instanceof Boolean) {
            return String.valueOf(value);
        }
        if (value instanceof List<?> list && list.stream().allMatch(FrontmatterHandler::isScalarYamlValue)) {
            List<String> rendered = new ArrayList<>();
            for (Object item : list) {
                rendered.add(String.valueOf(item));
            }
            return "[" + String.join(", ", rendered) + "]";
        }
        return null;
    }

    private static boolean isScalarYamlValue(Object value) {
        return value == null || value instanceof String || value instanceof Number || value instanceof Boolean;
    }

    private static List<String> toStringList(Object value) {
        if (value == null) {
            return List.of();
        }
        if (value instanceof List<?> list) {
            List<String> result = new ArrayList<>();
            for (Object item : list) {
                String rendered = stringValue(item);
                if (!rendered.isBlank()) {
                    result.add(rendered);
                }
            }
            return result;
        }
        String rendered = stringValue(value);
        return rendered.isBlank() ? List.of() : List.of(rendered);
    }

    private static String stringValue(Object value) {
        if (value == null) {
            return null;
        }
        return String.valueOf(value);
    }

    private static boolean booleanValue(Object value) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value instanceof Number number) {
            return number.intValue() != 0;
        }
        return value != null && "true".equalsIgnoreCase(String.valueOf(value));
    }

    @SuppressWarnings("unchecked")
    private static Object deepCopyYamlValue(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> copy = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (entry.getKey() != null) {
                    copy.put(String.valueOf(entry.getKey()), deepCopyYamlValue(entry.getValue()));
                }
            }
            return copy;
        }
        if (value instanceof List<?> list) {
            List<Object> copy = new ArrayList<>(list.size());
            for (Object item : list) {
                copy.add(deepCopyYamlValue(item));
            }
            return copy;
        }
        if (value instanceof Set<?> set) {
            List<Object> copy = new ArrayList<>(set.size());
            for (Object item : set) {
                copy.add(deepCopyYamlValue(item));
            }
            return copy;
        }
        return value;
    }

    private static DumperOptions createDumperOptions() {
        DumperOptions options = new DumperOptions();
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        options.setPrettyFlow(true);
        options.setIndent(2);
        options.setIndicatorIndent(1);
        options.setProcessComments(false);
        return options;
    }

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

    private static void putIfNotBlank(Map<String, Object> root, String key, String value) {
        if (value != null && !value.isBlank()) {
            root.put(key, value);
        }
    }

    private static void extractInlineTags(String content, Note note) {
        if (content == null || content.isBlank() || note == null) {
            return;
        }
        for (String word : content.split("\\s+")) {
            if (word.startsWith("#") && word.length() > 1) {
                String tag = word.substring(1).replaceAll("[^\\p{L}\\p{N}_/-]", "");
                if (!tag.isBlank()) {
                    note.addTag(new Tag(tag));
                }
            }
        }
    }
}
