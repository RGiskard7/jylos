package com.example.jylos.plugin.builtin.dataview;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.example.jylos.data.models.Note;
import com.example.jylos.data.models.Tag;

/**
 * Extracts query-visible metadata from a note's raw Markdown.
 *
 * <p>Parsing is done here rather than reused from the app's own frontmatter handling so
 * the same rules apply in both storage modes: the filesystem vault exposes parsed YAML
 * on the note model, but SQLite storage does not, and a query must not silently return
 * different results depending on where the vault is stored.</p>
 *
 * <h2>Recognised metadata</h2>
 * <ul>
 *   <li>YAML frontmatter between {@code ---} fences (scalars, inline and block lists)</li>
 *   <li>Inline fields: {@code key:: value} on its own line, or bracketed
 *       {@code [key:: value]} / {@code (key:: value)} anywhere in a line</li>
 *   <li>{@code #tags} in the body plus frontmatter {@code tags}/{@code tag}</li>
 *   <li>Checklist items {@code - [ ] …} / {@code - [x] …}</li>
 * </ul>
 *
 * <p>Fenced code blocks are skipped when scanning the body: a shell comment or a CSS
 * colour inside a code sample is not a tag, and {@code ::} in code is not a field.</p>
 */
final class PageParser {

    private static final Pattern FRONTMATTER = Pattern.compile("^---\\r?\\n(.*?)\\r?\\n---\\s*?(\\r?\\n|$)",
            Pattern.DOTALL);
    private static final Pattern TASK_LINE = Pattern.compile("^(\\s*)[-*+]\\s+\\[(.)\\]\\s*(.*)$");
    private static final Pattern INLINE_FIELD_LINE = Pattern.compile("^([A-Za-z0-9_][\\w \\-/]*?)::\\s*(.*)$");
    private static final Pattern INLINE_FIELD_BRACKET =
            Pattern.compile("[\\[(]([A-Za-z0-9_][\\w \\-/]*?)::\\s*([^\\])]*)[\\])]");
    private static final Pattern TAG = Pattern.compile("(?<![\\w&])#([A-Za-z0-9_][\\w/\\-]*)");
    private static final Pattern WIKI_LINK = Pattern.compile("\\[\\[([^\\[\\]\\n]+?)\\]\\]");

    private PageParser() {
    }

    static Page parse(Note note, String folderName, String path) {
        String raw = note.getContent() == null ? "" : note.getContent();

        Map<String, Object> fields = new LinkedHashMap<>();
        List<String> tags = new ArrayList<>();

        String body = raw;
        Matcher frontmatter = FRONTMATTER.matcher(raw);
        if (frontmatter.find()) {
            parseFrontmatter(frontmatter.group(1), fields, tags);
            body = raw.substring(frontmatter.end());
        } else if (note.getStructuredFrontmatterProperties() != null) {
            // No literal frontmatter in the content we hold (SQLite storage strips it, and
            // an attachment has none) — fall back to whatever the model already carries.
            for (Map.Entry<String, Object> entry : note.getStructuredFrontmatterProperties().entrySet()) {
                fields.put(Page.normalizeKey(entry.getKey()), coerce(entry.getValue()));
            }
            collectTagField(fields, tags);
        }

        List<Task> tasks = new ArrayList<>();
        parseBody(body, note.getTitle(), fields, tags, tasks);

        for (Tag tag : safeTags(note)) {
            if (tag != null && tag.getTitle() != null) {
                addTag(tags, tag.getTitle());
            }
        }

        List<Link> outlinks = new ArrayList<>();
        List<String> targets = note.getLinkTargets();
        if (targets != null && !targets.isEmpty()) {
            for (String target : targets) {
                outlinks.add(Link.to(target));
            }
        } else {
            Matcher links = WIKI_LINK.matcher(body);
            while (links.find()) {
                String target = links.group(1).split("\\|", 2)[0].split("#", 2)[0].trim();
                if (!target.isEmpty()) {
                    outlinks.add(Link.to(target));
                }
            }
        }

        return new Page(
                note.getId(),
                note.getTitle() == null ? "" : note.getTitle(),
                path,
                folderName,
                DqlValue.asDate(note.getCreatedDate()),
                DqlValue.asDate(note.getModifiedDate()),
                raw.length(),
                note.isFavorite(),
                note.isPinned(),
                tags,
                outlinks,
                tasks,
                fields);
    }

    private static List<Tag> safeTags(Note note) {
        List<Tag> noteTags = note.getTags();
        return noteTags == null ? List.of() : noteTags;
    }

    // ── Frontmatter ──────────────────────────────────────────────────────────

    private static void parseFrontmatter(String yaml, Map<String, Object> fields, List<String> tags) {
        String[] lines = yaml.split("\r?\n");
        String pendingKey = null;
        List<Object> pendingList = null;

        for (String line : lines) {
            if (line.isBlank() || line.trim().startsWith("#")) {
                continue;
            }
            String trimmed = line.trim();

            // A "- item" line continues the list opened by the previous "key:" line.
            if (trimmed.startsWith("- ") || trimmed.equals("-")) {
                if (pendingKey != null) {
                    if (pendingList == null) {
                        pendingList = new ArrayList<>();
                    }
                    pendingList.add(coerce(stripQuotes(trimmed.length() > 1 ? trimmed.substring(1).trim() : "")));
                }
                continue;
            }

            if (pendingKey != null) {
                fields.put(Page.normalizeKey(pendingKey), pendingList != null ? pendingList : null);
                pendingKey = null;
                pendingList = null;
            }

            int separator = trimmed.indexOf(':');
            if (separator <= 0) {
                continue;
            }
            String key = trimmed.substring(0, separator).trim();
            String value = trimmed.substring(separator + 1).trim();
            if (value.isEmpty()) {
                pendingKey = key;
                continue;
            }
            fields.put(Page.normalizeKey(key), coerceYamlValue(value));
        }
        if (pendingKey != null) {
            fields.put(Page.normalizeKey(pendingKey), pendingList != null ? pendingList : null);
        }
        collectTagField(fields, tags);
    }

    /** Promotes a frontmatter {@code tags}/{@code tag} field into the page's tag list. */
    private static void collectTagField(Map<String, Object> fields, List<String> tags) {
        for (String key : new String[] { "tags", "tag" }) {
            Object value = fields.get(key);
            if (value == null) {
                continue;
            }
            for (Object element : DqlValue.asList(value)) {
                addTag(tags, DqlValue.toDisplayString(element));
            }
        }
    }

    private static Object coerceYamlValue(String value) {
        String text = value.trim();
        if (text.startsWith("[") && text.endsWith("]")) {
            List<Object> list = new ArrayList<>();
            String inner = text.substring(1, text.length() - 1).trim();
            if (!inner.isEmpty()) {
                for (String element : splitTopLevel(inner)) {
                    list.add(coerce(stripQuotes(element.trim())));
                }
            }
            return list;
        }
        return coerce(stripQuotes(text));
    }

    /** Splits a YAML flow sequence on commas that are not inside quotes or brackets. */
    private static List<String> splitTopLevel(String text) {
        List<String> parts = new ArrayList<>();
        int depth = 0;
        char quote = 0;
        StringBuilder current = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (quote != 0) {
                if (c == quote) {
                    quote = 0;
                }
                current.append(c);
                continue;
            }
            switch (c) {
                case '"', '\'' -> {
                    quote = c;
                    current.append(c);
                }
                case '[', '{' -> {
                    depth++;
                    current.append(c);
                }
                case ']', '}' -> {
                    depth--;
                    current.append(c);
                }
                case ',' -> {
                    if (depth == 0) {
                        parts.add(current.toString());
                        current.setLength(0);
                    } else {
                        current.append(c);
                    }
                }
                default -> current.append(c);
            }
        }
        if (current.length() > 0) {
            parts.add(current.toString());
        }
        return parts;
    }

    private static String stripQuotes(String value) {
        if (value.length() >= 2
                && ((value.startsWith("\"") && value.endsWith("\""))
                        || (value.startsWith("'") && value.endsWith("'")))) {
            return value.substring(1, value.length() - 1);
        }
        return value;
    }

    // ── Body ─────────────────────────────────────────────────────────────────

    private static void parseBody(String body, String noteTitle, Map<String, Object> fields,
            List<String> tags, List<Task> tasks) {
        String[] lines = body.split("\r?\n", -1);
        boolean inFence = false;

        for (int lineNumber = 0; lineNumber < lines.length; lineNumber++) {
            String line = lines[lineNumber];
            String trimmed = line.trim();

            if (trimmed.startsWith("```") || trimmed.startsWith("~~~")) {
                inFence = !inFence;
                continue;
            }
            if (inFence) {
                continue;
            }

            Matcher task = TASK_LINE.matcher(line);
            if (task.matches()) {
                String status = task.group(2);
                String text = task.group(3);
                Map<String, Object> taskFields = new LinkedHashMap<>();
                text = extractBracketFields(text, taskFields);
                collectTags(text, tags);
                tasks.add(new Task(text.trim(), "x".equalsIgnoreCase(status), status,
                        lineNumber, task.group(1).length(), noteTitle == null ? "" : noteTitle,
                        taskFields));
                continue;
            }

            String remaining = extractBracketFields(line, fields);

            Matcher fieldLine = INLINE_FIELD_LINE.matcher(remaining.trim());
            if (fieldLine.matches()) {
                fields.put(Page.normalizeKey(fieldLine.group(1)), coerce(fieldLine.group(2).trim()));
                collectTags(fieldLine.group(2), tags);
                continue;
            }

            collectTags(remaining, tags);
        }
    }

    /** Removes {@code [key:: value]} / {@code (key:: value)} spans, recording each field. */
    private static String extractBracketFields(String line, Map<String, Object> into) {
        Matcher matcher = INLINE_FIELD_BRACKET.matcher(line);
        if (!matcher.find()) {
            return line;
        }
        matcher.reset();
        StringBuilder cleaned = new StringBuilder();
        while (matcher.find()) {
            into.put(Page.normalizeKey(matcher.group(1)), coerce(matcher.group(2).trim()));
            matcher.appendReplacement(cleaned, "");
        }
        matcher.appendTail(cleaned);
        return cleaned.toString();
    }

    private static void collectTags(String text, List<String> tags) {
        Matcher matcher = TAG.matcher(text);
        while (matcher.find()) {
            addTag(tags, matcher.group(1));
        }
    }

    private static void addTag(List<String> tags, String rawTag) {
        if (rawTag == null || rawTag.isBlank()) {
            return;
        }
        String normalized = rawTag.trim();
        if (!normalized.startsWith("#")) {
            normalized = "#" + normalized;
        }
        for (String existing : tags) {
            if (existing.equalsIgnoreCase(normalized)) {
                return;
            }
        }
        tags.add(normalized);
    }

    // ── Scalar coercion ──────────────────────────────────────────────────────

    /** Turns raw text into the most specific value type it clearly represents. */
    static Object coerce(Object raw) {
        if (raw == null) {
            return null;
        }
        if (!(raw instanceof CharSequence)) {
            if (raw instanceof Number number) {
                return number.doubleValue();
            }
            if (raw instanceof List<?> list) {
                List<Object> coerced = new ArrayList<>(list.size());
                for (Object element : list) {
                    coerced.add(coerce(element));
                }
                return coerced;
            }
            return raw;
        }

        String text = raw.toString().trim();
        if (text.isEmpty()) {
            return "";
        }
        if ("true".equalsIgnoreCase(text) || "false".equalsIgnoreCase(text)) {
            return Boolean.parseBoolean(text);
        }
        if ("null".equalsIgnoreCase(text) || "~".equals(text)) {
            return null;
        }

        Matcher link = WIKI_LINK.matcher(text);
        if (link.matches()) {
            String inner = link.group(1);
            String[] parts = inner.split("\\|", 2);
            String target = parts[0].split("#", 2)[0].trim();
            return new Link(target, parts.length > 1 ? parts[1].trim() : target);
        }

        // A comma-separated inline field is a list, matching how Dataview reads
        // "genres:: sci-fi, drama" — but only when it is not just prose with commas.
        if (text.contains(",") && text.length() < 300 && !text.contains(". ")) {
            String[] parts = text.split(",");
            if (parts.length > 1) {
                List<Object> list = new ArrayList<>(parts.length);
                boolean allShort = true;
                for (String part : parts) {
                    String element = part.trim();
                    if (element.isEmpty() || element.contains(" ") && element.length() > 40) {
                        allShort = false;
                        break;
                    }
                    list.add(coerceLeaf(element));
                }
                if (allShort) {
                    return list;
                }
            }
        }

        return coerceLeaf(text);
    }

    /** Coerces a single scalar without list splitting, avoiding infinite recursion. */
    private static Object coerceLeaf(String text) {
        try {
            return Double.parseDouble(text);
        } catch (NumberFormatException ignored) {
            // not numeric — try date next
        }
        Object date = DqlValue.asDate(text);
        if (date != null && text.length() >= 8 && text.charAt(4) == '-') {
            return date;
        }
        Matcher link = WIKI_LINK.matcher(text);
        if (link.matches()) {
            String inner = link.group(1);
            String[] parts = inner.split("\\|", 2);
            String target = parts[0].split("#", 2)[0].trim();
            return new Link(target, parts.length > 1 ? parts[1].trim() : target);
        }
        return text;
    }
}
