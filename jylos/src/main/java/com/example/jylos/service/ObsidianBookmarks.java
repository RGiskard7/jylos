package com.example.jylos.service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;

/**
 * Obsidian's bookmarks of a vault, read from {@code .obsidian/bookmarks.json}, which is never
 * written: Jylos does not touch another app's settings. A bookmark of a file (a note, a canvas,
 * a PDF…) is what a favorite is in Jylos; bookmarks of searches, folders, headings or blocks
 * have no equivalent and are only counted.
 *
 * <p>The format is Obsidian's own, not documented: {@code {"items": [...]}}, a file being
 * {@code {"type": "file", "path": "Folder/Note.md", ...}} and a group any item with its own
 * {@code items}. A file in another shape is reported as not understood rather than read as
 * having no bookmarks.</p>
 *
 * @author Edu Díaz (RGiskard7)
 * @since 2.6.0
 */
public final class ObsidianBookmarks {

    /**
     * @param files   the paths of the files bookmarked, in order, once each
     * @param ignored the other bookmarks: searches, folders, headings, blocks…
     */
    public record Found(List<String> files, int ignored) {
    }

    private ObsidianBookmarks() {
    }

    /**
     * The bookmarks of the vault at {@code vaultRoot}, or empty when it has none (no file).
     *
     * @throws IllegalArgumentException when the file is not in the shape Obsidian writes
     */
    public static Optional<Found> read(Path vaultRoot) {
        Path file = vaultRoot.resolve(".obsidian").resolve("bookmarks.json");
        if (!Files.isRegularFile(file)) {
            return Optional.empty();
        }
        JsonElement root;
        try {
            root = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8));
        } catch (IOException | JsonParseException e) {
            throw new IllegalArgumentException("Could not read " + file + ": " + e.getMessage(), e);
        }
        JsonArray items = root != null && root.isJsonObject() ? array(root.getAsJsonObject(), "items") : null;
        if (items == null) {
            throw new IllegalArgumentException("Not Obsidian's bookmarks: " + file);
        }
        List<String> files = new ArrayList<>();
        int[] ignored = {0};
        collect(items, files, ignored);
        return Optional.of(new Found(List.copyOf(files), ignored[0]));
    }

    private static void collect(JsonArray items, List<String> files, int[] ignored) {
        for (JsonElement element : items) {
            if (!element.isJsonObject()) {
                ignored[0]++;
                continue;
            }
            JsonObject item = element.getAsJsonObject();
            JsonArray inner = array(item, "items");
            if (inner != null) {
                collect(inner, files, ignored); // a group
            } else if ("file".equals(text(item, "type")) && text(item, "path") != null && text(item, "subpath") == null) {
                if (!files.contains(text(item, "path"))) {
                    files.add(text(item, "path"));
                }
            } else {
                ignored[0]++; // a search, a folder, a heading or block of a file…
            }
        }
    }

    private static JsonArray array(JsonObject object, String key) {
        JsonElement value = object.get(key);
        return value != null && value.isJsonArray() ? value.getAsJsonArray() : null;
    }

    private static String text(JsonObject object, String key) {
        JsonElement value = object.get(key);
        return value != null && value.isJsonPrimitive() && !value.getAsString().isBlank() ? value.getAsString() : null;
    }
}
