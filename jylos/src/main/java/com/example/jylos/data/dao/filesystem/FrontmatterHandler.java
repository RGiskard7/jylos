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
import org.yaml.snakeyaml.representer.Representer;
import org.yaml.snakeyaml.resolver.Resolver;

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
        FrontmatterBlock block = extractLeadingFrontmatter(fileContent);
        if (block == null) {
            return parseWithoutFrontmatter(fileContent);
        }
        return buildNote(parseYaml(block.yaml()), block.body());
    }

    /**
     * Imports a YAML frontmatter block typed or pasted at the beginning of a note body.
     *
     * <p>The editor normally exposes metadata through the properties panel, so its text
     * content does not include the persisted YAML header. This method supports importing a
     * complete Markdown document without persisting a second header inside the body. Fields
     * present in the imported header replace their corresponding values; unrelated existing
     * metadata remains intact.</p>
     *
     * @param note note whose content may begin with YAML frontmatter
     * @return {@code true} when a leading frontmatter block was imported
     * @throws IllegalArgumentException if a leading frontmatter block contains invalid YAML
     */
    public static boolean importLeadingFrontmatter(Note note) {
        if (note == null || note.getContent() == null || note.getContent().isEmpty()) {
            return false;
        }

        FrontmatterBlock block = extractLeadingFrontmatter(note.getContent());
        if (block == null) {
            return false;
        }

        Map<String, Object> values;
        try {
            values = parseYaml(block.yaml());
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("Invalid YAML frontmatter", e);
        }
        applyImportedFrontmatter(note, values, block.body());
        return true;
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
        FrontmatterBlock block = extractLeadingFrontmatter(fileContent);
        if (block == null) {
            String body = limit > 0 && fileContent.length() > limit
                    ? fileContent.substring(0, limit)
                    : fileContent;
            return parseWithoutFrontmatter(body);
        }
        String bodyRaw = block.body();
        String body = limit > 0 && bodyRaw.length() > limit
                ? bodyRaw.substring(0, limit)
                : bodyRaw;
        return buildNote(parseYaml(block.yaml()), body);
    }

    public static String stripFrontmatter(String fileContent) {
        if (fileContent == null) return "";
        FrontmatterBlock block = extractLeadingFrontmatter(fileContent);
        return block != null ? block.body() : fileContent;
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

    private static void applyImportedFrontmatter(Note target, Map<String, Object> values, String body) {
        Note imported = buildNote(values, body);
        target.setContent(body);
        target.setContentComplete(true);

        if (values.containsKey("title")) target.setTitle(imported.getTitle());
        if (values.containsKey("created")) target.setCreatedDate(imported.getCreatedDate());
        if (values.containsKey("modified")) target.setModifiedDate(imported.getModifiedDate());
        if (values.containsKey("favorite")) target.setFavorite(imported.isFavorite());
        if (values.containsKey("pinned")) target.setPinned(imported.isPinned());
        if (values.containsKey("deleted")) target.setDeleted(imported.isDeleted());
        if (values.containsKey("deleted_date")) target.setDeletedDate(imported.getDeletedDate());
        if (values.containsKey("status")) target.setStatus(imported.getStatus());
        if (values.containsKey("private")) target.setPrivate(imported.isPrivate());
        if (values.containsKey("author")) target.setAuthor(imported.getAuthor());
        if (values.containsKey("source_url")) target.setSourceUrl(imported.getSourceUrl());
        if (values.containsKey("tags")) target.setTags(imported.getTags());

        Map<String, String> custom = new LinkedHashMap<>(target.getCustomProperties());
        Map<String, Object> structured = new LinkedHashMap<>(target.getStructuredFrontmatterProperties());
        Set<String> displayable = new LinkedHashSet<>(target.getDisplayableFrontmatterPropertyKeys());
        for (Map.Entry<String, Object> entry : values.entrySet()) {
            String key = entry.getKey();
            if (key == null || SYSTEM_KEYS.contains(key)) {
                continue;
            }
            Object value = deepCopyYamlValue(entry.getValue());
            structured.put(key, value);
            String displayValue = toDisplayableCustomValue(value);
            if (displayValue == null) {
                custom.remove(key);
                displayable.remove(key);
            } else {
                custom.put(key, displayValue);
                displayable.add(key);
            }
        }
        target.setCustomProperties(custom);
        target.setStructuredFrontmatterProperties(structured);
        target.setDisplayableFrontmatterPropertyKeys(displayable);
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
        LoaderOptions loaderOptions = new LoaderOptions();
        DumperOptions dumperOptions = createDumperOptions();
        return new Yaml(new SafeConstructor(loaderOptions), new Representer(dumperOptions), dumperOptions,
                loaderOptions, new FrontmatterResolver());
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
        if (value instanceof List<?> list && list.size() == 1) {
            return stringValue(list.getFirst());
        }
        return String.valueOf(value);
    }

    private static FrontmatterBlock extractLeadingFrontmatter(String content) {
        if (content == null || content.isEmpty()) {
            return null;
        }

        int firstLineEnd = findLineEnd(content, 0);
        if (!SEPARATOR.equals(content.substring(0, firstLineEnd))) {
            return null;
        }

        int yamlStart = skipLineBreak(content, firstLineEnd);
        int lineStart = yamlStart;
        while (lineStart <= content.length()) {
            int lineEnd = findLineEnd(content, lineStart);
            if (SEPARATOR.equals(content.substring(lineStart, lineEnd))) {
                int bodyStart = skipLineBreak(content, lineEnd);
                return new FrontmatterBlock(content.substring(yamlStart, lineStart),
                        content.substring(bodyStart).stripLeading());
            }
            if (lineEnd >= content.length()) {
                return null;
            }
            lineStart = skipLineBreak(content, lineEnd);
        }
        return null;
    }

    private static int findLineEnd(String value, int start) {
        int lineFeed = value.indexOf('\n', start);
        int carriageReturn = value.indexOf('\r', start);
        if (lineFeed < 0) return carriageReturn < 0 ? value.length() : carriageReturn;
        if (carriageReturn < 0) return lineFeed;
        return Math.min(lineFeed, carriageReturn);
    }

    private static int skipLineBreak(String value, int index) {
        int cursor = index;
        if (cursor < value.length() && value.charAt(cursor) == '\r') cursor++;
        if (cursor < value.length() && value.charAt(cursor) == '\n') cursor++;
        return cursor;
    }

    private record FrontmatterBlock(String yaml, String body) {
    }

    /**
     * Keeps ISO-like YAML dates as strings. Frontmatter is document metadata, not a
     * Java date-serialization format; coercing it to {@code Date} changes the value
     * and breaks round-trips with external Markdown tools.
     */
    private static final class FrontmatterResolver extends Resolver {
        @Override
        protected void addImplicitResolvers() {
            addImplicitResolver(org.yaml.snakeyaml.nodes.Tag.BOOL, BOOL, "yYnNtTfFoO", 10);
            addImplicitResolver(org.yaml.snakeyaml.nodes.Tag.INT, INT, "-+0123456789");
            addImplicitResolver(org.yaml.snakeyaml.nodes.Tag.FLOAT, FLOAT, "-+0123456789.");
            addImplicitResolver(org.yaml.snakeyaml.nodes.Tag.MERGE, MERGE, "<", 10);
            addImplicitResolver(org.yaml.snakeyaml.nodes.Tag.NULL, NULL, "~nN\0", 10);
            addImplicitResolver(org.yaml.snakeyaml.nodes.Tag.NULL, EMPTY, null, 10);
            addImplicitResolver(org.yaml.snakeyaml.nodes.Tag.YAML, YAML, "!&*", 10);
        }
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
