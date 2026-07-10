package com.example.jylos.data.dao.filesystem;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.LinkedHashMap;
import java.util.Map;

import com.example.jylos.data.models.Note;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Encapsulates document metadata persistence for filesystem-backed vaults.
 *
 * <p>Markdown keeps metadata in frontmatter. Canvas files and binary attachments
 * keep Jylos-only document metadata in a private sidecar index inside the vault
 * so metadata changes never rewrite binary payloads or Obsidian canvas JSON by
 * accident.</p>
 */
final class FileSystemDocumentMetadataStore {

    private static final String TECH_DIR = ".jylos";
    private static final String DOCUMENT_INDEX_FILE = "document-metadata.json";
    private final Path rootPath;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private Map<String, DocumentMetadata> documentIndexCache;

    FileSystemDocumentMetadataStore(Path rootPath) {
        this.rootPath = rootPath;
    }

    /**
     * Parses and reserializes canvas content only for real canvas saves/creates.
     * Metadata-only updates must not call this with an empty lightweight note body.
     */
    String normalizeCanvasDocument(String baseContent) {
        return gson.toJson(parseCanvasRoot(baseContent));
    }

    void applyDocumentMetadata(String noteId, Note note) {
        DocumentMetadata metadata = documentIndex().get(normalizeId(noteId));
        if (metadata != null) {
            metadata.applyTo(note);
        }
    }

    void persistDocumentMetadata(Note note) {
        if (note == null || note.getId() == null || note.getId().isBlank()) {
            return;
        }
        Map<String, DocumentMetadata> index = new LinkedHashMap<>(documentIndex());
        DocumentMetadata metadata = DocumentMetadata.fromNote(note);
        String normalizedId = normalizeId(note.getId());
        if (metadata.isEmpty()) {
            index.remove(normalizedId);
        } else {
            index.put(normalizedId, metadata);
        }
        writeBinaryIndex(index);
    }

    void moveDocumentMetadata(String previousId, String currentId) {
        String from = normalizeId(previousId);
        String to = normalizeId(currentId);
        if (from.isBlank() || to.isBlank() || from.equals(to)) {
            return;
        }
        Map<String, DocumentMetadata> index = new LinkedHashMap<>(documentIndex());
        DocumentMetadata metadata = index.remove(from);
        if (metadata != null) {
            index.put(to, metadata);
            writeBinaryIndex(index);
        }
    }

    void deleteDocumentMetadata(String noteId) {
        String normalizedId = normalizeId(noteId);
        if (normalizedId.isBlank()) {
            return;
        }
        Map<String, DocumentMetadata> index = new LinkedHashMap<>(documentIndex());
        if (index.remove(normalizedId) != null) {
            writeBinaryIndex(index);
        }
    }

    private synchronized Map<String, DocumentMetadata> documentIndex() {
        if (documentIndexCache == null) {
            documentIndexCache = loadDocumentIndex();
        }
        return documentIndexCache;
    }

    private Map<String, DocumentMetadata> loadDocumentIndex() {
        Path path = documentIndexPath();
        if (!Files.exists(path)) {
            return new LinkedHashMap<>();
        }
        try {
            String json = Files.readString(path, StandardCharsets.UTF_8);
            JsonElement root = JsonParser.parseString(json);
            if (root == null || !root.isJsonObject()) {
                return new LinkedHashMap<>();
            }
            Map<String, DocumentMetadata> index = new LinkedHashMap<>();
            for (Map.Entry<String, JsonElement> entry : root.getAsJsonObject().entrySet()) {
                if (entry.getValue() != null && entry.getValue().isJsonObject()) {
                    index.put(normalizeId(entry.getKey()), DocumentMetadata.fromJson(entry.getValue().getAsJsonObject()));
                }
            }
            return index;
        } catch (Exception e) {
            return new LinkedHashMap<>();
        }
    }

    private synchronized void writeBinaryIndex(Map<String, DocumentMetadata> index) {
        Path path = documentIndexPath();
        try {
            if (index.isEmpty()) {
                Files.deleteIfExists(path);
                documentIndexCache = new LinkedHashMap<>();
                return;
            }
            Files.createDirectories(path.getParent());
            JsonObject root = new JsonObject();
            for (Map.Entry<String, DocumentMetadata> entry : index.entrySet()) {
                root.add(entry.getKey(), entry.getValue().toJson());
            }
            Files.writeString(path, gson.toJson(root), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            documentIndexCache = new LinkedHashMap<>(index);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to write document metadata sidecar", e);
        }
    }

    private Path documentIndexPath() {
        return rootPath.resolve(TECH_DIR).resolve(DOCUMENT_INDEX_FILE);
    }

    /**
     * Returns a valid Obsidian canvas root while preserving unknown fields when the
     * source is valid JSON.
     */
    static JsonObject parseCanvasRoot(String content) {
        if (content == null || content.isBlank()) {
            JsonObject root = new JsonObject();
            root.add("nodes", new JsonArray());
            root.add("edges", new JsonArray());
            return root;
        }
        try {
            JsonElement element = JsonParser.parseString(content);
            JsonObject root = element != null && element.isJsonObject() ? element.getAsJsonObject() : new JsonObject();
            if (!root.has("nodes")) {
                root.add("nodes", new JsonArray());
            }
            if (!root.has("edges")) {
                root.add("edges", new JsonArray());
            }
            return root;
        } catch (Exception e) {
            JsonObject root = new JsonObject();
            root.add("nodes", new JsonArray());
            root.add("edges", new JsonArray());
            return root;
        }
    }

    private static String normalizeId(String id) {
        return id == null ? "" : id.replace("\\", "/");
    }

    private record DocumentMetadata(
            boolean favorite,
            boolean pinned,
            boolean isPrivate,
            boolean deleted,
            String deletedDate,
            String status) {

        static DocumentMetadata fromNote(Note note) {
            return new DocumentMetadata(
                    note.isFavorite(),
                    note.isPinned(),
                    note.isPrivate(),
                    note.isDeleted(),
                    note.getDeletedDate(),
                    note.getStatus());
        }

        static DocumentMetadata fromJson(JsonObject root) {
            return new DocumentMetadata(
                    bool(root, "favorite"),
                    bool(root, "pinned"),
                    bool(root, "private"),
                    bool(root, "deleted"),
                    str(root, "deleted_date"),
                    str(root, "status"));
        }

        void applyTo(Note note) {
            note.setFavorite(favorite);
            note.setPinned(pinned);
            note.setPrivate(isPrivate);
            note.setDeleted(deleted);
            note.setDeletedDate(deletedDate);
            note.setStatus(status);
        }

        JsonObject toJson() {
            JsonObject root = new JsonObject();
            root.addProperty("favorite", favorite);
            root.addProperty("pinned", pinned);
            root.addProperty("private", isPrivate);
            root.addProperty("deleted", deleted);
            applyOptionalString(root, "deleted_date", deletedDate);
            applyOptionalString(root, "status", status);
            return root;
        }

        boolean isEmpty() {
            return !favorite
                    && !pinned
                    && !isPrivate
                    && !deleted
                    && (deletedDate == null || deletedDate.isBlank())
                    && (status == null || status.isBlank());
        }

        private static boolean bool(JsonObject root, String key) {
            JsonElement value = root.get(key);
            return value != null && value.isJsonPrimitive() && value.getAsBoolean();
        }

        private static String str(JsonObject root, String key) {
            JsonElement value = root.get(key);
            return value != null && value.isJsonPrimitive() ? value.getAsString() : "";
        }

        private static void applyOptionalString(JsonObject root, String key, String value) {
            if (value == null || value.isBlank()) {
                root.remove(key);
            } else {
                root.addProperty(key, value);
            }
        }
    }
}
