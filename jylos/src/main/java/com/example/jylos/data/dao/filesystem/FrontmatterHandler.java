package com.example.jylos.data.dao.filesystem;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
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
import com.example.jylos.service.EncryptionService;
import com.example.jylos.util.ReadingSyntax;
import com.example.jylos.util.WikiLinkResolver;

/**
 * Parses and generates Obsidian-compatible YAML frontmatter in Markdown files.
 *
 * <p>Known note fields are mapped to {@link Note} properties. Every non-system
 * frontmatter entry is preserved through round-trips, including nested YAML
 * structures that the flat properties UI cannot edit directly.</p>
 *
 * @author Edu Díaz (RGiskard7)
 * @since 1.0.0
 */
public final class FrontmatterHandler {

    private static final String SEPARATOR = "---";

    /**
     * The fields older versions of Jylos wrote in every note. They are not written any more;
     * when the user wrote one of these keys, it is theirs and stays.
     */
    private static final List<String> JYLOS_FIELDS = List.of(
            "id", "title", "created", "modified", "favorite", "pinned", "deleted", "deleted_date");

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
        return generate(note, null);
    }

    /**
     * The file for {@code note}, given the file as it is on disk now ({@code previousFile},
     * {@code null} for a new note). Only what the user wrote goes in it, as Obsidian does:
     * <ul>
     *   <li>Jylos' own fields are not written: the id is the path, the title the file name, the
     *       dates the file's, the favorite and pinned marks are kept in {@code .jylos/} and the
     *       trash is a folder. The same keys written by the user are theirs and stay as they
     *       were; of the fields an older Jylos wrote ({@link #isJylosFrontmatter}) only
     *       {@code created} stays.</li>
     *   <li>Unchanged properties are copied byte for byte (comments, quotes, order, line ends),
     *       and so are properties that do not read as YAML, which Jylos cannot know.</li>
     *   <li>A note with no properties is written without a properties block.</li>
     *   <li>Its {@code tags:} keep the ones it had that the note still has, in their order, and
     *       gain those added from the app; a {@code #tag} of the text stays in the text.</li>
     * </ul>
     */
    public static String generate(Note note, String previousFile) {
        String text = note.getContent() != null ? note.getContent() : "";
        FrontmatterBlock before = previousFile != null ? extractLeadingFrontmatter(previousFile) : null;
        Map<String, Object> previous = before != null ? readableYaml(before.yaml()) : Map.of();
        if (previous == null) {
            return before.head() + text; // unreadable properties: kept as the user wrote them
        }
        boolean byJylos = isJylosFrontmatter(previous);
        Map<String, Object> root = new LinkedHashMap<>();

        for (String key : JYLOS_FIELDS) {
            if (previous.containsKey(key) && (!byJylos || "created".equals(key))) {
                root.put(key, previous.get(key));
            }
        }
        putKeeping(root, previous, "status", note.getStatus());
        if (note.isPrivate()) {
            root.put("private", true);
        }
        putKeeping(root, previous, "author", note.getAuthor());
        putKeeping(root, previous, "source_url", note.getSourceUrl());

        List<String> tags = propertyTags(note, previousFile);
        if (!tags.isEmpty()) {
            Object written = previous.get("tags");
            root.put("tags", written != null && toStringList(written).equals(tags) ? written : tags);
        }

        if (note instanceof ToDoNote todo) {
            root.put("is_todo", true);
            putKeeping(root, previous, "todo_due", todo.getToDoDue());
            putKeeping(root, previous, "todo_completed", todo.getToDoCompleted());
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

        root = inPreviousOrder(root, previous);
        if (before != null && root.equals(previous)) {
            return before.head() + text;
        }
        if (root.isEmpty()) {
            return text;
        }
        String lineEnd = before != null && before.head().contains("\r\n") ? "\r\n" : "\n";
        String yaml = trimTrailingDocumentMarker(newYamlDumper().dump(root));
        if (!"\n".equals(lineEnd)) {
            yaml = yaml.replace("\n", lineEnd);
        }
        return SEPARATOR + lineEnd + yaml + SEPARATOR + lineEnd + (before != null ? before.gap() : lineEnd) + text;
    }

    /** {@code value} under {@code key}, as {@code previous} wrote it when it is the same. */
    private static void putKeeping(Map<String, Object> root, Map<String, Object> previous, String key, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        Object written = previous.get(key);
        root.put(key, written != null && value.equals(String.valueOf(written)) ? written : value);
    }

    /**
     * Whether the properties of {@code file} mark the note favorite or pinned: keys the user
     * wrote (Jylos keeps its marks in {@code .jylos/}).
     */
    public static boolean claimsMarks(String file) {
        FrontmatterBlock block = file != null ? extractLeadingFrontmatter(file) : null;
        Map<String, Object> properties = block != null ? readableYaml(block.yaml()) : null;
        return properties != null && (booleanValue(properties.get("favorite")) || booleanValue(properties.get("pinned")));
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

    /**
     * Every note link of a file, as Obsidian counts them: first the links written in its
     * properties ({@code up: "[[Índice]]"}, a list of {@code "[[Note]]"}), then those of its
     * body. Same rules as {@code WikiLinkResolver.extractRawLinks}; de-duplicated, in order.
     */
    public static List<String> linksOf(String fileContent) {
        if (fileContent == null) {
            return List.of();
        }
        FrontmatterBlock block = extractLeadingFrontmatter(fileContent);
        LinkedHashSet<String> links = new LinkedHashSet<>();
        if (block != null) {
            for (String value : textValues(block.yaml())) {
                links.addAll(WikiLinkResolver.extractRawLinks(value));
            }
        }
        links.addAll(WikiLinkResolver.extractRawLinks(
                block != null ? block.body() : fileContent));
        return List.copyOf(links);
    }

    /**
     * The text values of a properties block, in order (lists walked into). A link counts in a
     * property only when it is text, as in Obsidian: {@code up: "[[A]]"}; written without
     * quotes YAML reads {@code [[A]]} as a list, not a link. Unreadable YAML has none.
     */
    private static List<String> textValues(String yaml) {
        List<String> values = new ArrayList<>();
        try {
            collectText(parseYaml(yaml).values(), values);
        } catch (RuntimeException e) {
            return List.of();
        }
        return values;
    }

    private static void collectText(Collection<?> items, List<String> into) {
        for (Object item : items) {
            if (item instanceof String text) {
                into.add(text);
            } else if (item instanceof Collection<?> list) {
                collectText(list, into);
            } else if (item instanceof Map<?, ?> map) {
                collectText(map.values(), into);
            }
        }
    }

    /** The text of a file's leading properties block (without the {@code ---} lines), or "". */
    public static String frontmatterText(String fileContent) {
        FrontmatterBlock block = fileContent == null ? null : extractLeadingFrontmatter(fileContent);
        return block != null ? block.yaml() : "";
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
                // The blank lines between the properties and the text are kept apart, to be
                // written back as they were; the text starts at its first line, indentation kept.
                int textStart = bodyStart;
                while (textStart < content.length()
                        && (content.charAt(textStart) == '\n' || content.charAt(textStart) == '\r')) {
                    textStart++;
                }
                return new FrontmatterBlock(content.substring(yamlStart, lineStart),
                        content.substring(textStart), content.substring(bodyStart, textStart),
                        content.substring(0, textStart));
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

    /**
     * @param yaml the properties, without the {@code ---} lines
     * @param body the text, from its first line
     * @param gap  the blank lines between the closing {@code ---} line and the text
     * @param head everything before the text, exactly as written
     */
    private record FrontmatterBlock(String yaml, String body, String gap, String head) {
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

    /**
     * A file without frontmatter (most notes of an Obsidian vault): all of it is the note's text.
     * A first {@code # } line is a heading like any other, not the title, which is the file name
     * (set by the caller): taking it out of the text lost it from the file at the next save.
     */
    private static Note parseWithoutFrontmatter(String content) {
        String body = content;
        Note note = new Note("Untitled", body);
        // A note with no YAML frontmatter at all is common for a vault Jylos did not
        // create (an existing Obsidian vault, a plain synced folder): it has not gone
        // through buildNote() even once, so without this call its inline #tags would
        // never be picked up until Jylos itself wrote the note once.
        extractInlineTags(body, note);
        return note;
    }

    /**
     * The tags {@code tags:} holds: first those it held in {@code previousFile} that the note
     * still has, in their order and spelling, then the note's other tags that its text does not
     * have as {@code #tag}, now or in {@code previousFile} (so, added from the app), by name. Tag
     * names compare without case. The text of a private note is encrypted: its tags are all kept
     * in {@code tags:}.
     */
    private static List<String> propertyTags(Note note, String previousFile) {
        Map<String, String> current = new LinkedHashMap<>();
        note.getTags().stream().map(Tag::getTitle).filter(title -> title != null && !title.isBlank()).sorted()
                .forEach(title -> current.putIfAbsent(foldTag(title), title));
        // A #tag of the text, now or before this edit (one just taken out of the text is still
        // in the note's tags until it is read again), is the text's, not one added from the app.
        Set<String> inline = new LinkedHashSet<>();
        addInlineTags(note.getContent(), inline);
        addInlineTags(previousFile != null ? stripFrontmatter(previousFile) : null, inline);
        List<String> out = new ArrayList<>();
        Set<String> written = new LinkedHashSet<>();
        for (String before : frontmatterTagsOf(previousFile)) {
            if (current.containsKey(foldTag(before)) && written.add(foldTag(before))) {
                out.add(before);
            }
        }
        current.forEach((key, title) -> {
            if (!inline.contains(key) && written.add(key)) {
                out.add(title);
            }
        });
        return out;
    }

    private static void addInlineTags(String text, Set<String> into) {
        if (text != null && !EncryptionService.isEncrypted(text)) {
            ReadingSyntax.inlineTags(text).forEach(tag -> into.add(foldTag(tag)));
        }
    }

    /**
     * Whether these properties were written by Jylos, which always wrote {@code id},
     * {@code favorite}, {@code pinned} and {@code deleted} together; a note of Obsidian
     * practically never has all four. Then its {@code title} is Jylos's, not the user's.
     */
    static boolean isJylosFrontmatter(Map<String, Object> properties) {
        return properties.containsKey("id") && properties.containsKey("favorite")
                && properties.containsKey("pinned") && properties.containsKey("deleted");
    }

    /** The keys of {@code root}: first those {@code previous} had, in its order, then the new ones. */
    private static Map<String, Object> inPreviousOrder(Map<String, Object> root, Map<String, Object> previous) {
        if (previous.isEmpty()) {
            return root;
        }
        Map<String, Object> ordered = new LinkedHashMap<>();
        previous.keySet().stream().filter(root::containsKey).forEach(key -> ordered.put(key, root.get(key)));
        root.forEach(ordered::putIfAbsent);
        return ordered;
    }

    /** The properties of a frontmatter, or {@code null} when it does not read as YAML. */
    private static Map<String, Object> readableYaml(String yaml) {
        try {
            return parseYaml(yaml);
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** The names in the {@code tags:} property of a file, in order; none when it has none or it does not read. */
    private static List<String> frontmatterTagsOf(String file) {
        FrontmatterBlock block = file != null ? extractLeadingFrontmatter(file) : null;
        if (block == null) {
            return List.of();
        }
        Map<String, Object> properties = readableYaml(block.yaml());
        return properties != null ? toStringList(properties.get("tags")) : List.of();
    }

    private static String foldTag(String tag) {
        return tag.strip().toLowerCase(Locale.ROOT);
    }

    private static void putIfNotBlank(Map<String, Object> root, String key, String value) {
        if (value != null && !value.isBlank()) {
            root.put(key, value);
        }
    }

    /**
     * The note's {@code #tags} as the reading view and the editor show them, and as Obsidian
     * finds them ({@link com.example.jylos.util.ReadingSyntax#inlineTags}): never inside code
     * (a shell comment, a CSS id selector, a Dataview {@code FROM #tag}), a link or a URL, and
     * ending at the first character a tag cannot have ({@code #foo.bar} is {@code foo}).
     */
    private static void extractInlineTags(String content, Note note) {
        if (content == null || content.isBlank() || note == null) {
            return;
        }
        for (String tag : ReadingSyntax.inlineTags(content)) {
            note.addTag(new Tag(tag));
        }
    }
}
