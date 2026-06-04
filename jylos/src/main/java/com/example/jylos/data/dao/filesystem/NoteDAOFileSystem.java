package com.example.jylos.data.dao.filesystem;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;

import com.example.jylos.config.LoggerConfig;
import com.example.jylos.data.models.interfaces.Component;
import com.example.jylos.data.dao.interfaces.NoteDAO;
import com.example.jylos.data.models.Folder;
import com.example.jylos.data.models.Note;
import com.example.jylos.data.models.Tag;
import com.example.jylos.exceptions.DataAccessException;
import com.example.jylos.exceptions.InvalidParameterException;

/**
 * File System implementation of NoteDAO.
 * Stores notes as Markdown files with YAML frontmatter.
 */
public class NoteDAOFileSystem implements NoteDAO {

    private static final Logger logger = LoggerConfig.getLogger(NoteDAOFileSystem.class);
    private static final String ROOT_ID = "ROOT";
    // DAO layer must stay locale-neutral; UI is responsible for localized labels.
    private static final String ROOT_TITLE = "ROOT";
    private final Path rootPath;

    // Cache to map Note ID (Relative Path) -> Absolute Path
    private final Map<String, Path> idToPathMap = new ConcurrentHashMap<>();
    // Cache to map Note ID -> Note object (Lightweight)
    private final Map<String, Note> cachedNotes = new ConcurrentHashMap<>();
    // Index: folderId -> notes (direct children only)
    private final Map<String, List<Note>> notesByFolderIndex = new ConcurrentHashMap<>();
    private static final long PRUNE_INTERVAL_MS = 3000L;
    /** Bytes read when building list/preview summaries (frontmatter + body head). */
    private static final int LIGHTWEIGHT_READ_BYTES = 16_384;
    /** Body characters kept on cached notes for the notes list preview. */
    private static final int LIGHTWEIGHT_BODY_CHARS = 900;
    private volatile long lastPruneTimestampMs = 0L;
    private volatile boolean notesByFolderIndexDirty = true;

    public NoteDAOFileSystem(String rootDirectory) {
        this.rootPath = Paths.get(rootDirectory);
        if (!Files.exists(rootPath)) {
            try {
                Files.createDirectories(rootPath);
            } catch (IOException e) {
                logger.log(Level.SEVERE, "Failed to create root directory for notes: " + rootDirectory, e);
                throw new DataAccessException("Could not initialize file storage", e);
            }
        }
        refreshCache();
    }

    public void refreshCache() {
        FileSystemIoLock.LOCK.lock();
        try {
        idToPathMap.clear();
        cachedNotes.clear();
        try (Stream<Path> walk = Files.walk(rootPath)) {
            // Using parallel stream for faster initial load of thousands of headers
            walk.filter(Files::isRegularFile)
                    // Markdown notes plus viewable attachments (PDF, images) so the vault
                    // lists them alongside notes (Obsidian-style).
                    .filter(p -> p.toString().endsWith(".md")
                            || com.example.jylos.util.AttachmentType.isAttachment(p.getFileName().toString()))
                    .filter(p -> !p.toString().contains(File.separator + ".")) // Exclude hidden files and folders
                                                                               // (.trash, .git)
                    .parallel()
                    .forEach(path -> {
                        String relativePath = normalizeId(rootPath.relativize(path).toString());
                        idToPathMap.put(relativePath, path);
                        // Accessing created note immediately creates race condition if not thread safe
                        // NoteDAOFileSystem methods are synchronized or use concurrent maps
                        Note note = createLightweightNote(relativePath, path);
                        cachedNotes.put(relativePath, note);
                    });
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Failed to walk directory for cache refresh", e);
        }
        notesByFolderIndexDirty = true;
        } finally {
            FileSystemIoLock.LOCK.unlock();
        }
    }

    private Note createLightweightNote(String id, Path path) {
        String filename = path.getFileName().toString();
        String title = filename.endsWith(".md") ? filename.substring(0, filename.length() - 3) : filename;

        // Attachments (PDF, images) are binary: never parse them as Markdown/frontmatter.
        // Keep the full filename as title so the list shows "report.pdf", "diagram.png".
        if (com.example.jylos.util.AttachmentType.isAttachment(filename)) {
            Note attachment = new Note(id, title, "");
            applyFileTimestampsIfMissing(attachment, path);
            return attachment;
        }

        if (!Files.exists(path)) {
            return new Note(id, title, "");
        }

        try {
            String head = readFileHead(path, LIGHTWEIGHT_READ_BYTES);
            Note note = FrontmatterHandler.parseLightweight(head, LIGHTWEIGHT_BODY_CHARS);
            note.setId(id);
            note.setTitle(title);
            applyFileTimestampsIfMissing(note, path);
            return note;
        } catch (IOException e) {
            logger.log(Level.FINE, "Failed lightweight read for: " + path, e);
            Note fallback = new Note(id, title, "");
            applyFileTimestampsIfMissing(fallback, path);
            return fallback;
        }
    }

    private static String readFileHead(Path path, int maxBytes) throws IOException {
        long size = Files.size(path);
        int toRead = (int) Math.min(size, Math.max(0, maxBytes));
        if (toRead == 0) {
            return "";
        }
        byte[] buffer = new byte[toRead];
        try (var in = Files.newInputStream(path)) {
            int read = in.read(buffer);
            if (read <= 0) {
                return "";
            }
            return new String(buffer, 0, read, StandardCharsets.UTF_8);
        }
    }

    private void applyFileTimestampsIfMissing(Note note, Path path) {
        try {
            BasicFileAttributes attrs = Files.readAttributes(path, BasicFileAttributes.class);
            if (note.getCreatedDate() == null || note.getCreatedDate().isBlank()) {
                note.setCreatedDate(DateTimeFormatter.ISO_INSTANT.format(attrs.creationTime().toInstant()));
            }
            if (note.getModifiedDate() == null || note.getModifiedDate().isBlank()) {
                note.setModifiedDate(DateTimeFormatter.ISO_INSTANT.format(attrs.lastModifiedTime().toInstant()));
            }
        } catch (IOException e) {
            logger.log(Level.FINE, "Could not read file timestamps for: " + path, e);
        }
    }

    @Override
    public String createNote(Note note) {
        FileSystemIoLock.LOCK.lock();
        try {
            if (note == null)
                throw new InvalidParameterException("Note cannot be null");
            if (note.getTitle() == null || note.getTitle().isBlank()) {
                note.setTitle("Untitled");
            }

            // Determine parent directory
            Path parentDir = rootPath;
            String suggestedId = normalizeId(note.getId());

            if (!suggestedId.isEmpty()) {
                // Check if the ID implies a folder path (e.g. "Folder/Note.md" or just
                // "Folder/")
                // If the ID comes from MainController as "Folder/New Note", we want to use
                // "Folder" as parent.
                if (suggestedId.contains("/")) {
                    int lastSeparator = suggestedId.lastIndexOf('/');
                    String folderPath = suggestedId.substring(0, lastSeparator);
                    Path potentialDir = rootPath.resolve(folderPath.replace("/", File.separator));
                    if (Files.exists(potentialDir) && Files.isDirectory(potentialDir)) {
                        parentDir = potentialDir;
                    }
                }
            }

            String filename = sanitizeFilename(note.getTitle()) + ".md";

            Path filePath = parentDir.resolve(filename);
            // Handle duplicate filenames
            int counter = 1;
            while (Files.exists(filePath)) {
                filename = sanitizeFilename(note.getTitle()) + " (" + counter + ").md";
                filePath = parentDir.resolve(filename);
                counter++;
            }

            if (note.getCreatedDate() == null) {
                note.setCreatedDate(DateTimeFormatter.ISO_INSTANT.format(Instant.now()));
            }

            // Set the ID to relative path
            String relativePath = normalizeId(rootPath.relativize(filePath).toString());
            note.setId(relativePath);

            try {
                String fileContent = FrontmatterHandler.generate(note);
                Files.writeString(filePath, fileContent, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
                idToPathMap.put(relativePath, filePath);
                cachedNotes.put(relativePath, note);
                notesByFolderIndexDirty = true;
                return relativePath;
            } catch (IOException e) {
                logger.log(Level.SEVERE, "Failed to write note file: " + filePath, e);
                return null;
            }
        } finally {
            FileSystemIoLock.LOCK.unlock();
        }
    }

    @Override
    public java.util.Optional<Path> resolveFilePath(String id) {
        if (id == null) {
            return java.util.Optional.empty();
        }
        Path path = idToPathMap.get(id);
        if (path == null) {
            path = idToPathMap.get(normalizeId(id));
        }
        if (path == null) {
            Path potential = rootPath.resolve(normalizeId(id).replace("/", File.separator));
            if (Files.exists(potential)) {
                path = potential.toAbsolutePath().normalize();
            }
        }
        return java.util.Optional.ofNullable(path).filter(Files::exists);
    }

    @Override
    public Note getNoteById(String id) {
        if (id == null)
            return null;

        String normalizedId = normalizeId(id);
        Path path = idToPathMap.get(id);
        if (path == null) {
            path = idToPathMap.get(normalizedId);
        } else if (!Files.exists(path)) {
            idToPathMap.remove(id);
            cachedNotes.remove(id);
            path = null;
        }
        if (path != null && !Files.exists(path)) {
            idToPathMap.remove(normalizedId);
            cachedNotes.remove(normalizedId);
            path = null;
        }
        if (path == null) {
            // Maybe it's a new file not in cache yet
            Path potential = rootPath.resolve(normalizedId.replace("/", File.separator));
            if (Files.exists(potential)) {
                path = potential.toAbsolutePath().normalize();
                idToPathMap.put(normalizedId, path);
            } else {
                return null;
            }
        }

        // Attachments (PDF, images) are binary — return their metadata, never decode
        // the bytes as Markdown text.
        if (com.example.jylos.util.AttachmentType.isAttachment(path.getFileName().toString())) {
            return createLightweightNote(normalizedId, path);
        }

        try {
            // Lenient UTF-8 decode: vault files copied from the web may contain
            // non-UTF-8 bytes; Files.readString would throw MalformedInputException,
            // so decode replacing bad bytes (same approach as the lightweight read).
            String content = new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
            Note note = FrontmatterHandler.parse(content);
            // Override ID with our path-based ID
            note.setId(normalizedId);

            // Sync Title with Filename
            String filename = path.getFileName().toString();
            if (filename.endsWith(".md"))
                filename = filename.substring(0, filename.length() - 3);
            note.setTitle(filename);

            return note;
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Failed to read note file: " + path, e);
            return null;
        }
    }

    @Override
    public void updateNote(Note note) {
        FileSystemIoLock.LOCK.lock();
        try {
        if (note == null || note.getId() == null)
            throw new InvalidParameterException("Invalid note");

        String normalizedId = normalizeId(note.getId());
        note.setId(normalizedId);
        Path path = idToPathMap.get(note.getId());
        if (path == null) {
            path = idToPathMap.get(normalizedId);
        }
        if (path == null) {
            // Check if file exists at ID location
            path = rootPath.resolve(normalizedId.replace("/", File.separator));
            if (!Files.exists(path)) {
                logger.warning("Attempted to update non-existent note: " + note.getId());
                return;
            }
        }

        // Rename logic if Title changed
        String currentFilename = path.getFileName().toString();
        String expectedFilename = sanitizeFilename(note.getTitle()) + ".md";

        if (!currentFilename.equals(expectedFilename)) {
            Path newPath = path.resolveSibling(expectedFilename);
            if (!Files.exists(newPath)) {
                try {
                    Files.move(path, newPath);
                    // Update ID map and Cache
                    String oldId = normalizedId;
                    idToPathMap.remove(oldId);
                    cachedNotes.remove(oldId);

                    String newId = normalizeId(rootPath.relativize(newPath).toString());
                    idToPathMap.put(newId, newPath);
                    note.setId(newId); // Update object ID
                    path = newPath;
                    cachedNotes.put(newId, note);
                    notesByFolderIndexDirty = true;
                } catch (IOException e) {
                    logger.warning("Failed to rename note file during update: " + e.getMessage());
                }
            }
        }

        note.setModifiedDate(DateTimeFormatter.ISO_INSTANT.format(Instant.now()));

        try {
            String fileContent = FrontmatterHandler.generate(note);
            Files.writeString(path, fileContent, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            String currentId = normalizeId(note.getId());
            if (!normalizedId.equals(currentId)) {
                cachedNotes.remove(normalizedId);
                idToPathMap.remove(normalizedId);
            }
            cachedNotes.put(currentId, note);
            idToPathMap.put(currentId, path);
            notesByFolderIndexDirty = true;
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Failed to update note file: " + path, e);
        }
        } finally {
            FileSystemIoLock.LOCK.unlock();
        }
    }

    @Override
    public void deleteNote(String id) {
        FileSystemIoLock.LOCK.lock();
        try {
        if (id == null || id.isBlank()) {
            return;
        }
        String normalizedId = normalizeId(id);

        Path sourcePath = idToPathMap.get(id);
        if (sourcePath == null) {
            sourcePath = idToPathMap.get(normalizedId);
        }
        if (sourcePath == null) {
            Path candidate = rootPath.resolve(normalizedId.replace("/", File.separator));
            if (Files.exists(candidate)) {
                sourcePath = candidate;
            }
        }
        if (sourcePath == null) {
            for (Map.Entry<String, Path> entry : idToPathMap.entrySet()) {
                if (entry.getKey() != null && entry.getKey().replace("\\", "/").equals(normalizedId)) {
                    sourcePath = entry.getValue();
                    break;
                }
            }
        }

        if (sourcePath != null && Files.exists(sourcePath)) {
            try {
                // Determine target path in trash while preserving relative structure
                Path trashDir = rootPath.resolve(".trash");
                Path targetPath = trashDir.resolve(normalizedId.replace("/", File.separator));

                // Ensure target parent directories exist in trash
                if (targetPath.getParent() != null && !Files.exists(targetPath.getParent())) {
                    Files.createDirectories(targetPath.getParent());
                }

                // Avoid collision in trash
                if (Files.exists(targetPath)) {
                    String filename = targetPath.getFileName().toString();
                    String name = filename.endsWith(".md") ? filename.substring(0, filename.length() - 3) : filename;
                    targetPath = targetPath.getParent().resolve(name + "_" + System.currentTimeMillis() + ".md");
                }

                Files.move(sourcePath, targetPath);

                // Remove from cache (by exact and normalized key/path)
                idToPathMap.remove(id);
                idToPathMap.remove(normalizedId);
                cachedNotes.remove(id);
                cachedNotes.remove(normalizedId);
                final Path sourcePathFinal = sourcePath;
                idToPathMap.entrySet().removeIf(e -> {
                    String key = e.getKey() == null ? "" : e.getKey().replace("\\", "/");
                    return key.equals(normalizedId) || (e.getValue() != null && e.getValue().equals(sourcePathFinal));
                });
                cachedNotes.entrySet().removeIf(e -> {
                    String key = e.getKey() == null ? "" : e.getKey().replace("\\", "/");
                    Note note = e.getValue();
                    return key.equals(normalizedId) || (note != null && note.getId() != null
                            && note.getId().replace("\\", "/").equals(normalizedId));
                });
                notesByFolderIndexDirty = true;

            } catch (IOException e) {
                logger.log(Level.SEVERE, "Failed to move note to trash: " + sourcePath, e);
            }
        } else {
            // Idempotent delete: if already in trash or already gone, do not treat as warning.
            Path trashedPath = rootPath.resolve(".trash").resolve(normalizedId.replace("/", File.separator));
            if (Files.exists(trashedPath)) {
                logger.fine("Delete request ignored because note is already in trash: " + normalizedId);
            } else {
                logger.fine("Delete request ignored for non-resolvable note id: " + normalizedId);
            }
        }
        } finally {
            FileSystemIoLock.LOCK.unlock();
        }
    }

    @Override
    public List<Note> fetchTrashNotes() {
        FileSystemIoLock.LOCK.lock();
        try {
            List<Note> deletedNotes = new ArrayList<>();
            Path trashPath = rootPath.resolve(".trash");
            if (Files.exists(trashPath) && Files.isDirectory(trashPath)) {
                try (Stream<Path> stream = Files.walk(trashPath)) {
                    stream.filter(Files::isRegularFile)
                            .filter(p -> p.toString().endsWith(".md"))
                            .filter(p -> !p.getFileName().toString().startsWith(".")) // Ignore hidden files
                            .filter(p -> {
                                // Only skip truly hidden system files like .DS_Store or .obsidian
                                // but allow trashed folders starting with dot
                                String filename = p.getFileName().toString();
                                return !filename.startsWith(".") || filename.endsWith(".md");
                            })
                            .forEach(p -> {
                                // Create note with ID relative to root (e.g. .trash/sub/note.md)
                                String trashId = rootPath.relativize(p).toString().replace("\\", "/");
                                Note note = createLightweightNote(trashId, p);
                                note.setDeleted(true);
                                deletedNotes.add(note);
                            });
                } catch (IOException e) {
                    logger.warning("Error reading trash folder: " + e.getMessage());
                }
            }
            return deletedNotes;
        } finally {
            FileSystemIoLock.LOCK.unlock();
        }
    }

    @Override
    public void restoreNote(String id) {
        FileSystemIoLock.LOCK.lock();
        try {
        if (id == null || id.isBlank()) {
            return;
        }
        String normalizedId = id.replace("\\", "/");
        // ID is relative path like .trash/note.md or .trash/Folder/note.md
        try {
            Path source = rootPath.resolve(normalizedId.replace("/", File.separator));
            if (!Files.exists(source)) {
                // Fallback: resolve with raw id (legacy behavior)
                Path rawSource = rootPath.resolve(id);
                if (Files.exists(rawSource)) {
                    source = rawSource;
                }
            }
            if (!Files.exists(source)) {
                // Try fallback to just filename if id is not found (for old trashed notes)
                String filename = Paths.get(normalizedId).getFileName().toString();
                source = rootPath.resolve(".trash").resolve(filename);
            }

            if (Files.exists(source)) {
                // Calculate original relative path
                String originalRelPath = normalizedId;
                if (normalizedId.startsWith(".trash/")) {
                    originalRelPath = normalizedId.substring(".trash/".length());
                } else if (normalizedId.startsWith(".trash")) {
                    originalRelPath = normalizedId.substring(6);
                    if (originalRelPath.startsWith("/")) {
                        originalRelPath = originalRelPath.substring(1);
                    }
                }

                Path target = rootPath.resolve(originalRelPath.replace("/", File.separator));

                // If parent folder was deleted (not restored), restore to root instead of
                // recreating the folder
                if (target.getParent() != null && !Files.exists(target.getParent())) {
                    String filenameOrig = target.getFileName().toString();
                    target = rootPath.resolve(filenameOrig);
                }

                // Handle conflicts
                if (Files.exists(target)) {
                    String filename = target.getFileName().toString();
                    String name = filename.endsWith(".md") ? filename.substring(0, filename.length() - 3) : filename;
                    target = target.getParent().resolve(name + "_restored_" + System.currentTimeMillis() + ".md");
                }

                // Ensure parent exists (always true if it's rootPath, but safe to keep)
                if (target.getParent() != null && !Files.exists(target.getParent())) {
                    Files.createDirectories(target.getParent());
                }

                Files.move(source, target);

                // Update cache
                refreshCache();
            }
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Failed to restore note: " + id, e);
        }
        } finally {
            FileSystemIoLock.LOCK.unlock();
        }
    }

    @Override
    public void permanentlyDeleteNote(String id) {
        FileSystemIoLock.LOCK.lock();
        try {
        try {
            String normalizedId = normalizeId(id);
            Path path = rootPath.resolve(normalizedId.replace("/", File.separator)); // id starts with .trash/ usually

            if (Files.exists(path)) {
                Files.delete(path);
                idToPathMap.remove(id);
                idToPathMap.remove(normalizedId);
                cachedNotes.remove(id);
                cachedNotes.remove(normalizedId);
                notesByFolderIndexDirty = true;
            } else {
                // Try fallback to filename in trash root
                String filename = path.getFileName().toString();
                Path fallback = rootPath.resolve(".trash").resolve(filename);
                if (Files.exists(fallback)) {
                    Files.delete(fallback);
                }
            }
            // Also remove from cache
            cachedNotes.remove(id);
            cachedNotes.remove(normalizeId(id));
            notesByFolderIndexDirty = true;
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Failed to permanently delete note: " + id, e);
        }
        } finally {
            FileSystemIoLock.LOCK.unlock();
        }
    }

    @Override
    public List<Note> fetchNotesByFolderId(String folderId) {
        pruneStaleCacheEntriesIfNeeded();
        if (cachedNotes.isEmpty()) {
            refreshCache();
        }
        ensureFolderIndex();
        List<Note> notes = new ArrayList<>();
        String normalizedFolderId = normalizeFolderKey(folderId);
        List<Note> folderNotes = notesByFolderIndex.getOrDefault(normalizedFolderId, List.of());
        for (Note note : folderNotes) {
            if (note != null && !note.isDeleted()) {
                notes.add(note);
            }
        }
        return notes;
    }

    @Override
    public void fetchNotesByFolderId(Folder folder) {
        List<Note> notes = fetchNotesByFolderId(folder.getId());
        // Cast to Component list for addAll
        List<Component> components = new ArrayList<>(notes);
        folder.addAll(components);
    }

    @Override
    public List<Note> fetchAllNotes() {
        pruneStaleCacheEntriesIfNeeded();
        if (cachedNotes.isEmpty()) {
            refreshCache();
        }
        return new ArrayList<>(cachedNotes.values());
    }

    @Override
    public Folder getFolderOfNote(String noteId) {
        if (noteId == null || noteId.isEmpty()) {
            return new Folder(ROOT_ID, ROOT_TITLE);
        }

        Path notePath = Paths.get(noteId.replace("\\", "/"));
        Path parent = notePath.getParent();
        if (parent == null) {
            return new Folder(ROOT_ID, ROOT_TITLE);
        }

        String folderId = parent.toString().replace("\\", "/");
        String folderName = parent.getFileName() != null ? parent.getFileName().toString() : ROOT_TITLE;
        return new Folder(folderId, folderName);
    }

    @Override
    public void addTag(String noteId, String tagId) {
        if (noteId == null || noteId.isEmpty() || tagId == null || tagId.isEmpty()) {
            return;
        }

        Note note = getNoteById(noteId);
        if (note == null) {
            return;
        }

        Tag tag = new Tag(tagId, tagId);
        addTag(note, tag);
    }

    @Override
    public void addTag(Note note, Tag tag) {
        FileSystemIoLock.LOCK.lock();
        try {
            note.addTag(tag);
            updateNote(note);
        } finally {
            FileSystemIoLock.LOCK.unlock();
        }
    }

    @Override
    public void removeTag(String noteId, String tagId) {
        if (noteId == null || noteId.isEmpty() || tagId == null || tagId.isEmpty()) {
            return;
        }

        Note note = getNoteById(noteId);
        if (note == null) {
            return;
        }

        Tag tag = new Tag(tagId, tagId);
        removeTag(note, tag);
    }

    @Override
    public void removeTag(Note note, Tag tag) {
        FileSystemIoLock.LOCK.lock();
        try {
            note.removeTag(tag);
            updateNote(note);
        } finally {
            FileSystemIoLock.LOCK.unlock();
        }
    }

    @Override
    public List<Tag> fetchTags(String noteId) {
        Note note = getNoteById(noteId);
        if (note != null)
            return note.getTags();
        return new ArrayList<>();
    }

    @Override
    public void loadTags(Note note) {
        if (note == null || note.getId() == null) {
            return;
        }

        Note persisted = getNoteById(note.getId());
        if (persisted == null) {
            return;
        }

        note.setTags(persisted.getTags());
    }

    @Override
    public List<Note> fetchNotesByTagId(String tagId) {
        if (cachedNotes.isEmpty()) {
            refreshCache();
        }
        List<Note> all = new ArrayList<>();
        for (Note n : cachedNotes.values()) {
            for (Tag t : n.getTags()) {
                if (t.getTitle().equals(tagId)) {
                    all.add(n);
                    break;
                }
            }
        }
        return all;
    }

    private String sanitizeFilename(String title) {
        return title.replaceAll("[^\\p{L}\\p{N}\\.\\-_ ]", "_");
    }

    private String normalizeId(String id) {
        if (id == null) {
            return "";
        }
        return id.replace("\\", "/");
    }

    private void pruneStaleCacheEntries() {
        FileSystemIoLock.LOCK.lock();
        try {
            idToPathMap.entrySet().removeIf(e -> e.getValue() == null || !Files.exists(e.getValue()));

            Map<String, Note> reindexed = new HashMap<>();
            for (Map.Entry<String, Note> entry : cachedNotes.entrySet()) {
                Note note = entry.getValue();
                if (note == null || note.getId() == null || note.getId().isBlank()) {
                    continue;
                }

                String noteId = normalizeId(note.getId());
                Path resolved = rootPath.resolve(noteId.replace("/", File.separator));
                if (!Files.exists(resolved)) {
                    Path mapped = idToPathMap.get(noteId);
                    if (mapped != null && Files.exists(mapped)) {
                        resolved = mapped;
                    } else {
                        continue;
                    }
                }

                idToPathMap.put(noteId, resolved);
                reindexed.put(noteId, note);
            }

            cachedNotes.clear();
            cachedNotes.putAll(reindexed);
            notesByFolderIndexDirty = true;
        } finally {
            FileSystemIoLock.LOCK.unlock();
        }
    }

    private void pruneStaleCacheEntriesIfNeeded() {
        long now = System.currentTimeMillis();
        if (now - lastPruneTimestampMs < PRUNE_INTERVAL_MS) {
            return;
        }
        lastPruneTimestampMs = now;
        pruneStaleCacheEntries();
    }

    private void ensureFolderIndex() {
        if (!notesByFolderIndexDirty) {
            return;
        }
        FileSystemIoLock.LOCK.lock();
        try {
            if (!notesByFolderIndexDirty) {
                return;
            }
            notesByFolderIndex.clear();
            for (Note note : cachedNotes.values()) {
                if (note == null || note.getId() == null || note.getId().isBlank() || note.isDeleted()) {
                    continue;
                }
                String noteId = normalizeId(note.getId());
                String folderKey = extractFolderKeyFromNoteId(noteId);
                notesByFolderIndex.computeIfAbsent(folderKey, k -> new ArrayList<>()).add(note);
            }
            notesByFolderIndexDirty = false;
        } finally {
            FileSystemIoLock.LOCK.unlock();
        }
    }

    private String normalizeFolderKey(String folderId) {
        if (folderId == null || folderId.isBlank() || ROOT_ID.equals(folderId)) {
            return ROOT_ID;
        }
        return normalizeId(folderId);
    }

    private String extractFolderKeyFromNoteId(String noteId) {
        if (noteId == null || noteId.isBlank()) {
            return ROOT_ID;
        }
        String normalized = normalizeId(noteId);
        int idx = normalized.lastIndexOf('/');
        if (idx <= 0) {
            return ROOT_ID;
        }
        return normalized.substring(0, idx);
    }
}
