package com.example.jylos.plugin.builtin.dataview;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * A note as the query language sees it: implicit {@code file.*} attributes plus the
 * user-defined fields harvested from frontmatter and inline {@code key:: value} syntax.
 *
 * <p>Field names are matched case-insensitively and with spaces, hyphens and underscores
 * treated as equivalent, so {@code due-date}, {@code Due Date} and {@code due_date} all
 * resolve to the same field. Frontmatter is hand-written prose, not a schema, and users
 * are not consistent about the separator.</p>
 */
final class Page {

    private final String id;
    private final String title;
    private final String path;
    private final String folder;
    private final Object created;
    private final Object modified;
    private final long size;
    private final boolean favorite;
    private final boolean pinned;
    private final List<String> tags;
    private final List<Link> outlinks;
    private final List<Task> tasks;
    private final Map<String, Object> fields;

    /** Filled in by {@link DataviewIndex} once every page is known. */
    private final Set<String> inlinks = new LinkedHashSet<>();

    Page(String id, String title, String path, String folder, Object created, Object modified,
            long size, boolean favorite, boolean pinned, List<String> tags, List<Link> outlinks,
            List<Task> tasks, Map<String, Object> fields) {
        this.id = id;
        this.title = title;
        this.path = path;
        this.folder = folder;
        this.created = created;
        this.modified = modified;
        this.size = size;
        this.favorite = favorite;
        this.pinned = pinned;
        this.tags = tags;
        this.outlinks = outlinks;
        this.tasks = tasks;
        this.fields = fields;
    }

    String id() {
        return id;
    }

    String title() {
        return title;
    }

    String folder() {
        return folder;
    }

    List<String> tags() {
        return tags;
    }

    List<Link> outlinks() {
        return outlinks;
    }

    List<Task> tasks() {
        return tasks;
    }

    Link link() {
        return Link.to(title);
    }

    void addInlink(String sourceTitle) {
        inlinks.add(sourceTitle);
    }

    /**
     * Normalises a field name so lookups ignore case and separator style. Applied to both
     * sides (stored keys and query identifiers) so they always meet in the middle.
     */
    static String normalizeKey(String name) {
        if (name == null) {
            return "";
        }
        return name.trim().toLowerCase(Locale.ROOT).replace('-', ' ').replace('_', ' ');
    }

    /**
     * Resolves a top-level identifier: the implicit {@code file} object, otherwise a
     * user field. Returns {@code null} for unknown names, which queries treat as an
     * absent value rather than an error.
     */
    Object resolve(String name) {
        if ("file".equalsIgnoreCase(name)) {
            return fileObject();
        }
        return fields.get(normalizeKey(name));
    }

    boolean hasField(String name) {
        return fields.containsKey(normalizeKey(name));
    }

    Map<String, Object> userFields() {
        return fields;
    }

    /** The implicit {@code file.*} namespace, matching Dataview's attribute names. */
    Map<String, Object> fileObject() {
        Map<String, Object> file = new LinkedHashMap<>();
        file.put("name", title);
        file.put("path", path);
        file.put("folder", folder);
        file.put("link", link());
        file.put("size", (double) size);
        file.put("ctime", created);
        file.put("cday", dayOf(created));
        file.put("mtime", modified);
        file.put("mday", dayOf(modified));
        // `tags` keeps the leading '#' (Dataview's `etags`); `etags` is kept as an alias
        // so queries written for either spelling behave the same.
        List<Object> tagValues = new ArrayList<>(tags);
        file.put("tags", tagValues);
        file.put("etags", tagValues);
        file.put("outlinks", new ArrayList<Object>(outlinks));
        List<Object> inlinkValues = new ArrayList<>();
        for (String source : inlinks) {
            inlinkValues.add(Link.to(source));
        }
        file.put("inlinks", inlinkValues);
        file.put("tasks", new ArrayList<Object>(tasks));
        file.put("starred", favorite);
        file.put("pinned", pinned);
        return file;
    }

    private static Object dayOf(Object timestamp) {
        Object date = DqlValue.asDate(timestamp);
        if (date instanceof java.time.LocalDateTime dateTime) {
            return dateTime.toLocalDate();
        }
        return date;
    }

    @Override
    public String toString() {
        return title;
    }
}
