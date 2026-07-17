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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
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
import com.example.jylos.util.AttachmentType;
import com.example.jylos.util.WikiLinkResolver;

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
    private final FileSystemDocumentMetadataStore metadataStore;

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

    // Deferred (background) content-load coordination — see the two-arg constructor.
    private final CountDownLatch contentLoadedLatch = new CountDownLatch(1);
    private final Object contentLoadedLock = new Object();
    private volatile boolean contentLoaded = false;
    private Runnable onContentLoaded; // guarded by contentLoadedLock

    /**
     * Builds the DAO and loads note contents <em>synchronously</em> (original
     * behavior: the cache is fully populated when the constructor returns).
     */
    public NoteDAOFileSystem(String rootDirectory) {
        this(rootDirectory, false);
    }

    /**
     * @param rootDirectory   vault root
     * @param deferContentLoad when {@code true}, the constructor builds only a fast
     *        <em>metadata-only</em> cache (titles from filenames + filesystem
     *        timestamps, <em>no</em> file contents read) and then loads contents,
     *        tags and links on a background daemon thread, invoking the
     *        {@link #setOnContentLoaded(Runnable)} callback when finished. This keeps
     *        startup instant on large or cloud-backed vaults (e.g. iCloud-offloaded
     *        files), where reading every file would block on on-demand downloads.
     *        When {@code false}, contents load synchronously.
     */
    public NoteDAOFileSystem(String rootDirectory, boolean deferContentLoad) {
        this.rootPath = Paths.get(rootDirectory);
        this.metadataStore = new FileSystemDocumentMetadataStore(this.rootPath);
        if (!Files.exists(rootPath)) {
            try {
                Files.createDirectories(rootPath);
            } catch (IOException e) {
                logger.log(Level.SEVERE, "Failed to create root directory for notes: " + rootDirectory, e);
                throw new DataAccessException("Could not initialize file storage", e);
            }
        }
        if (deferContentLoad) {
            refreshCacheMetadataOnly();
            startBackgroundContentLoad();
        } else {
            refreshCache();
            markContentLoaded();
        }
    }

    /**
     * Full cache build: each entry is populated with contents/tags/links (reads a
     * lightweight head of every file). On large or cloud-backed vaults this blocks on
     * per-file I/O; the deferred constructor uses {@link #refreshCacheMetadataOnly()}
     * instead.
     */
    public void refreshCache() {
        rebuildCache(this::createLightweightNote);
    }

    /**
     * Fast cache build that reads only filesystem metadata (filename + timestamps),
     * never file <em>contents</em>. Used by the deferred constructor so startup does
     * not block on reading (and, on iCloud, downloading) every file.
     */
    private void refreshCacheMetadataOnly() {
        rebuildCache(this::createMetadataNote);
    }

    /**
     * Walks the vault and rebuilds {@code idToPathMap}/{@code cachedNotes}, building
     * each note with {@code noteFactory}. The factory decides how much I/O happens per
     * file (full head read vs metadata only) — that is the only difference between the
     * full and metadata-only refreshes.
     */
    private void rebuildCache(java.util.function.BiFunction<String, Path, Note> noteFactory) {
        FileSystemIoLock.LOCK.lock();
        try {
            idToPathMap.clear();
            cachedNotes.clear();
            try (Stream<Path> walk = Files.walk(rootPath)) {
                // Parallel walk for faster initial load of thousands of files.
                walk.filter(Files::isRegularFile)
                        // Markdown notes plus viewable attachments (PDF, images) so the vault
                        // lists them alongside notes (Obsidian-style).
                        .filter(p -> p.toString().endsWith(".md")
                                || com.example.jylos.util.AttachmentType.isAttachment(p.getFileName().toString()))
                        .filter(p -> !p.toString().contains(File.separator + ".")) // Exclude hidden (.trash, .git)
                        .parallel()
                        .forEach(path -> {
                            String relativePath = normalizeId(rootPath.relativize(path).toString());
                            idToPathMap.put(relativePath, path);
                            // Maps are concurrent, so parallel population is safe.
                            cachedNotes.put(relativePath, noteFactory.apply(relativePath, path));
                        });
            } catch (IOException e) {
                logger.log(Level.SEVERE, "Failed to walk directory for cache refresh", e);
            }
            notesByFolderIndexDirty = true;
        } finally {
            FileSystemIoLock.LOCK.unlock();
        }
    }

    /**
     * Loads note contents/tags/links in the background (deferred mode), upgrading the
     * metadata-only entries in place, then fires {@link #onContentLoaded}. Reads are
     * parallelised over a small bounded pool (I/O-bound, dominated by per-file latency)
     * and do <em>not</em> hold the global IO lock, so the UI stays responsive — a
     * folder click or note open during loading is not blocked.
     */
    private void startBackgroundContentLoad() {
        Thread loader = new Thread(() -> {
            try {
                List<Map.Entry<String, Path>> snapshot = new ArrayList<>(idToPathMap.entrySet());
                int threads = Math.max(4, Math.min(32, Runtime.getRuntime().availableProcessors() * 4));
                ExecutorService pool = Executors.newFixedThreadPool(threads, r -> {
                    Thread t = new Thread(r, "vault-content-loader");
                    t.setDaemon(true);
                    return t;
                });
                try {
                    for (Map.Entry<String, Path> entry : snapshot) {
                        String id = entry.getKey();
                        Path path = entry.getValue();
                        pool.execute(() -> {
                            try {
                                Note enriched = createLightweightNote(id, path);
                                // Only upgrade an entry that still exists (never resurrect a
                                // note deleted while loading).
                                cachedNotes.computeIfPresent(id, (k, old) -> enriched);
                            } catch (RuntimeException ex) {
                                logger.fine("Background content load skipped " + id + ": " + ex.getMessage());
                            }
                        });
                    }
                } finally {
                    pool.shutdown();
                    try {
                        pool.awaitTermination(10, TimeUnit.MINUTES);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                    }
                }
                notesByFolderIndexDirty = true;
                logger.info("Vault content load complete (" + snapshot.size() + " notes)");
            } finally {
                markContentLoaded();
            }
        }, "vault-content-loader-main");
        loader.setDaemon(true);
        loader.start();
    }

    /** Marks the content load finished, releasing waiters and running the callback once. */
    private void markContentLoaded() {
        Runnable cb;
        synchronized (contentLoadedLock) {
            contentLoaded = true;
            cb = onContentLoaded;
        }
        contentLoadedLatch.countDown();
        if (cb != null) {
            runCallbackSafely(cb);
        }
    }

    /** Invokes {@code cb} and swallows any {@link RuntimeException} so the caller's state is not corrupted. */
    private void runCallbackSafely(Runnable cb) {
        try {
            cb.run();
        } catch (RuntimeException e) {
            logger.log(Level.WARNING, "onContentLoaded callback failed", e);
        }
    }

    /** Registers (or replaces) the callback; runs it immediately if content is already loaded. */
    @Override
    public void setOnContentLoaded(Runnable callback) {
        boolean runNow;
        synchronized (contentLoadedLock) {
            this.onContentLoaded = callback;
            runNow = contentLoaded;
        }
        if (runNow && callback != null) {
            runCallbackSafely(callback);
        }
    }

    /** Test/diagnostic hook: blocks until the background content load finishes. */
    public boolean awaitContentLoaded(long timeoutMs) throws InterruptedException {
        return contentLoadedLatch.await(timeoutMs, TimeUnit.MILLISECONDS);
    }

    /** Builds a note from filename + filesystem timestamps only — never reads contents. */
    private Note createMetadataNote(String id, Path path) {
        String filename = path.getFileName().toString();
        String title = filename.endsWith(".md") ? filename.substring(0, filename.length() - 3) : filename;
        Note note = new Note(id, title, "");
        note.setContentComplete(false);
        applyFileTimestampsIfMissing(note, path);
        if (com.example.jylos.util.AttachmentType.isAttachment(filename)) {
            metadataStore.applyDocumentMetadata(id, note);
        }
        return note;
    }

    /** Reads up to {@link #LIGHTWEIGHT_READ_BYTES} of the file to populate title, frontmatter, body preview and link targets. */
    private Note createLightweightNote(String id, Path path) {
        String filename = path.getFileName().toString();
        if (isCanvasFile(filename)) {
            Note canvas = createMetadataNote(id, path);
            canvas.setTitle(filename);
            return canvas;
        }
        String title = filename.endsWith(".md") ? filename.substring(0, filename.length() - 3) : filename;

        // Attachments (PDF, images) are binary: never parse them as Markdown/frontmatter.
        // Keep the full filename as title so the list shows "report.pdf", "diagram.png".
        if (com.example.jylos.util.AttachmentType.isAttachment(filename)) {
            Note attachment = new Note(id, title, "");
            attachment.setContentComplete(false);
            applyFileTimestampsIfMissing(attachment, path);
            metadataStore.applyDocumentMetadata(id, attachment);
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
            note.setContentComplete(false);
            applyFileTimestampsIfMissing(note, path);
            // Index outgoing links from the (untruncated) content head already read, so the
            // backlink index needs no second full-file read per note — critical on large or
            // cloud-backed vaults where each read can block on an on-demand download.
            note.setLinkTargets(WikiLinkResolver.extractLinkTargets(FrontmatterHandler.stripFrontmatter(head)));
            return note;
        } catch (IOException e) {
            logger.log(Level.FINE, "Failed lightweight read for: " + path, e);
            Note fallback = new Note(id, title, "");
            fallback.setContentComplete(false);
            applyFileTimestampsIfMissing(fallback, path);
            return fallback;
        }
    }

    private static boolean isCanvasFile(String filename) {
        return com.example.jylos.util.AttachmentType.fromName(filename) == com.example.jylos.util.AttachmentType.CANVAS;
    }

    private Note readCanvasNote(String id, Path path) throws IOException {
        String filename = path.getFileName().toString();
        String content = Files.exists(path) ? Files.readString(path) : "";
        Note note = new Note(id, filename, content);
        note.setContentComplete(true);
        metadataStore.applyDocumentMetadata(id, note);
        applyFileTimestampsIfMissing(note, path);
        return note;
    }

    /** Reads at most {@code maxBytes} from the start of a file, returning the bytes decoded as UTF-8. */
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

    /** Fills in missing {@code createdDate} / {@code modifiedDate} on {@code note} from the file's OS attributes. */
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

    /** Writes a new Markdown file for {@code note}, resolving the parent directory from its ID and handling filename conflicts. */
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

            // Attachments (e.g. .canvas) keep their own extension and are written as raw
            // bytes — no ".md" suffix and no frontmatter wrapper.
            boolean attachment = com.example.jylos.util.AttachmentType.isAttachment(note.getTitle());
            String base;
            String ext;
            if (attachment) {
                String sanitized = sanitizeFilename(note.getTitle());
                int dot = sanitized.lastIndexOf('.');
                base = sanitized.substring(0, dot);
                ext = sanitized.substring(dot); // includes the leading dot
            } else {
                base = sanitizeFilename(note.getTitle());
                ext = ".md";
            }

            String filename = base + ext;
            Path filePath = parentDir.resolve(filename);
            // Handle duplicate filenames
            int counter = 1;
            while (Files.exists(filePath)) {
                filename = base + " (" + counter + ")" + ext;
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
                String fileContent;
                if (!attachment) {
                    fileContent = FrontmatterHandler.generate(note);
                } else if (isCanvasFile(filename)) {
                    fileContent = metadataStore.normalizeCanvasDocument(note.getContent());
                } else {
                    fileContent = note.getContent() != null ? note.getContent() : "";
                }
                Files.writeString(filePath, fileContent, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
                if (attachment) {
                    metadataStore.persistDocumentMetadata(note);
                }
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

    /** Reads and fully parses the Markdown file for {@code id}, resolving the path from the cache or disk. */
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
            if (isCanvasFile(path.getFileName().toString())) {
                try {
                    return readCanvasNote(normalizedId, path);
                } catch (IOException e) {
                    logger.warning("Could not read canvas file (skipped): " + path + " — " + e.getMessage());
                    return null;
                }
            }
            Note attachment = createLightweightNote(normalizedId, path);
            return attachment;
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

            // Full read → full-body link coverage for the backlink index.
            note.setLinkTargets(WikiLinkResolver.extractLinkTargets(FrontmatterHandler.stripFrontmatter(content)));

            return note;
        } catch (IOException e) {
            // A single failed read is recoverable: callers handle the null return. Log at
            // WARNING without a stack trace so a slow/offline drive or an iCloud-offloaded
            // file ("Operation timed out") cannot flood the log with SEVERE entries.
            logger.warning("Could not read note file (skipped): " + path + " — " + e.getMessage());
            return null;
        }
    }

    /** Persists changes to an existing note file; renames the file when the title has changed. */
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
            }

            boolean attachment = isAttachmentNote(path, note);
            boolean canvas = isCanvasFile(path.getFileName().toString());
            boolean missingOnDisk = !Files.exists(path);
            if (missingOnDisk && attachment && !canvas) {
                logger.warning("Attempted to update missing binary attachment: " + note.getId());
                removeCacheAliasesForIds(normalizedId, normalizedId);
                notesByFolderIndexDirty = true;
                throw new DataAccessException("Cannot update missing binary attachment: " + note.getId(), null);
            }
            String currentFilename = path.getFileName().toString();
            String expectedFilename = attachment
                    ? expectedAttachmentFilename(currentFilename, note.getTitle())
                    : sanitizeFilename(note.getTitle()) + ".md";

            if (!currentFilename.equals(expectedFilename)) {
                Path newPath = path.resolveSibling(expectedFilename);
                if (!Files.exists(newPath) && missingOnDisk) {
                    String oldId = normalizedId;
                    String newId = normalizeId(rootPath.relativize(newPath).toString());
                    idToPathMap.remove(oldId);
                    cachedNotes.remove(oldId);
                    note.setId(newId);
                    path = newPath;
                    missingOnDisk = true;
                    notesByFolderIndexDirty = true;
                } else if (!Files.exists(newPath)) {
                    try {
                        Files.move(path, newPath);
                        String oldId = normalizedId;
                        idToPathMap.remove(oldId);
                        cachedNotes.remove(oldId);

                        String newId = normalizeId(rootPath.relativize(newPath).toString());
                        if (attachment) {
                            metadataStore.moveDocumentMetadata(oldId, newId);
                        }
                        idToPathMap.put(newId, newPath);
                        note.setId(newId);
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
                if (missingOnDisk && path.getParent() != null) {
                    Files.createDirectories(path.getParent());
                }
                if (!attachment) {
                    ensureMarkdownContentComplete(note, path);
                    Files.writeString(path, FrontmatterHandler.generate(note),
                            StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
                } else if (isCanvasFile(path.getFileName().toString())) {
                    String content = note.getContent();
                    // Canvas list entries are metadata-only. Do not let a favorite/pinned
                    // toggle rewrite a real .canvas file with an empty lightweight body.
                    if (content != null && !content.isBlank()) {
                        Files.writeString(path, metadataStore.normalizeCanvasDocument(content),
                                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
                    }
                } else {
                    metadataStore.persistDocumentMetadata(note);
                }
                if (attachment && isCanvasFile(path.getFileName().toString())) {
                    metadataStore.persistDocumentMetadata(note);
                }
                String currentId = normalizeId(note.getId());
                removeCacheAliasesForIds(normalizedId, currentId);
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

    private void ensureMarkdownContentComplete(Note note, Path path) throws IOException {
        if (note == null || note.isContentComplete() || path == null || !Files.exists(path)) {
            return;
        }
        String persisted = new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
        Note full = FrontmatterHandler.parse(persisted);
        if ((note.getCreatedDate() == null || note.getCreatedDate().isBlank()) && full.getCreatedDate() != null) {
            note.setCreatedDate(full.getCreatedDate());
        }
        if ((note.getDeletedDate() == null || note.getDeletedDate().isBlank()) && full.getDeletedDate() != null) {
            note.setDeletedDate(full.getDeletedDate());
        }
        if ((note.getStatus() == null || note.getStatus().isBlank()) && full.getStatus() != null) {
            note.setStatus(full.getStatus());
        }
        if ((note.getAuthor() == null || note.getAuthor().isBlank()) && full.getAuthor() != null) {
            note.setAuthor(full.getAuthor());
        }
        if ((note.getSourceUrl() == null || note.getSourceUrl().isBlank()) && full.getSourceUrl() != null) {
            note.setSourceUrl(full.getSourceUrl());
        }
        if ((note.getCustomProperties() == null || note.getCustomProperties().isEmpty())
                && full.getCustomProperties() != null) {
            note.setCustomProperties(full.getCustomProperties());
        }
        if (note.getTags().isEmpty() && !full.getTags().isEmpty()) {
            note.setTags(full.getTags());
        }
        note.setContent(full.getContent());
        note.setContentComplete(true);
    }

    /** Moves the note file to the {@code .trash} subfolder, removing it from all caches (soft delete). */
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
                    int dot = filename.lastIndexOf('.');
                    String name = dot > 0 ? filename.substring(0, dot) : filename;
                    String extension = dot > 0 ? filename.substring(dot) : "";
                    targetPath = targetPath.getParent()
                            .resolve(name + "_" + System.currentTimeMillis() + extension);
                }

                Files.move(sourcePath, targetPath);
                if (usesSidecarMetadata(sourcePath)) {
                    String trashedId = normalizeId(rootPath.relativize(targetPath).toString());
                    metadataStore.moveDocumentMetadata(normalizedId, trashedId);
                }

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

    /** Walks the {@code .trash} directory and returns lightweight Note objects for all trashed Markdown files. */
    @Override
    public List<Note> fetchTrashNotes() {
        FileSystemIoLock.LOCK.lock();
        try {
            List<Note> deletedNotes = new ArrayList<>();
            Path trashPath = rootPath.resolve(".trash");
            if (Files.exists(trashPath) && Files.isDirectory(trashPath)) {
                try (Stream<Path> stream = Files.walk(trashPath)) {
                    stream.filter(Files::isRegularFile)
                            .filter(p -> !p.getFileName().toString().startsWith(".")) // Ignore hidden files
                            .filter(p -> {
                                String filename = p.getFileName().toString();
                                if (filename.startsWith(".")) {
                                    return false;
                                }
                                AttachmentType type = AttachmentType.fromName(filename);
                                return filename.endsWith(".md") || type.isAttachment();
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

    /** Moves a trashed note file back to its original vault location (or root if the parent folder no longer exists). */
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
                if (usesSidecarMetadata(target)) {
                    String restoredId = normalizeId(rootPath.relativize(target).toString());
                    metadataStore.moveDocumentMetadata(normalizedId, restoredId);
                }

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

    /** Irrecoverably deletes the file (typically under {@code .trash}) and removes it from all caches. */
    @Override
    public void permanentlyDeleteNote(String id) {
        FileSystemIoLock.LOCK.lock();
        try {
        try {
            String normalizedId = normalizeId(id);
            Path path = rootPath.resolve(normalizedId.replace("/", File.separator)); // id starts with .trash/ usually

            if (Files.exists(path)) {
                if (usesSidecarMetadata(path)) {
                    metadataStore.deleteDocumentMetadata(normalizedId);
                }
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

    /** Returns all non-deleted notes whose immediate parent directory matches {@code folderId} (or root when null/ROOT). */
    @Override
    public List<Note> fetchNotesByFolderId(String folderId) {
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

    /** Loads all direct-child notes of {@code folder} into the folder's component list. */
    @Override
    public void fetchNotesByFolderId(Folder folder) {
        List<Note> notes = fetchNotesByFolderId(folder.getId());
        // Cast to Component list for addAll
        List<Component> components = new ArrayList<>(notes);
        folder.addAll(components);
    }

    /** Returns a snapshot of all cached notes (lightweight), refreshing the cache if it is empty. */
    @Override
    public List<Note> fetchAllNotes() {
        if (cachedNotes.isEmpty()) {
            refreshCache();
        }
        return new ArrayList<>(cachedNotes.values());
    }

    /** Derives the parent {@link Folder} from the note's relative path; returns a ROOT folder when the note is at vault root. */
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

    /** Looks up the note by ID and attaches the tag identified by {@code tagId} to it, persisting via {@link #updateNote}. */
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

    /** Attaches {@code tag} to {@code note} in memory and writes the updated frontmatter to disk. */
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

    /** Looks up the note by ID and removes the tag identified by {@code tagId}, persisting the change. */
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

    /** Removes {@code tag} from {@code note} in memory and writes the updated frontmatter to disk. */
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

    /** Returns the tag list of the note with the given ID, or an empty list if the note is not found. */
    @Override
    public List<Tag> fetchTags(String noteId) {
        Note note = getNoteById(noteId);
        if (note != null)
            return note.getTags();
        return new ArrayList<>();
    }

    /** Reads the persisted tag list and injects it into {@code note}, overwriting any in-memory state. */
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

    /** Scans the cache and returns every note whose tag list contains a tag with title equal to {@code tagId}. */
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

    /** Replaces characters that are illegal in filenames with underscores while preserving Unicode letters and digits. */
    private String sanitizeFilename(String title) {
        String sanitized = title.replaceAll("[^\\p{L}\\p{N}\\.\\-_ ]", "_").trim();
        return sanitized.isBlank() ? "note" : sanitized;
    }

    private boolean isAttachmentNote(Path path, Note note) {
        return (path != null && com.example.jylos.util.AttachmentType.isAttachment(path.getFileName().toString()))
                || (note != null && com.example.jylos.util.AttachmentType.isAttachment(note.getTitle()));
    }

    private boolean usesSidecarMetadata(Path path) {
        return path != null
                && com.example.jylos.util.AttachmentType.isAttachment(path.getFileName().toString());
    }

    private String expectedAttachmentFilename(String currentFilename, String requestedTitle) {
        String sanitized = sanitizeFilename(requestedTitle);
        String currentExtension = com.example.jylos.util.AttachmentType.extensionOf(currentFilename);
        String extension = currentExtension;
        String base = sanitized;

        if (!currentExtension.isEmpty()) {
            int dot = sanitized.lastIndexOf('.');
            base = dot > 0 ? sanitized.substring(0, dot) : sanitized;
        }

        return extension.isEmpty() ? base : base + "." + extension;
    }

    /** Normalizes a note ID to use forward slashes and returns an empty string for null input. */
    private String normalizeId(String id) {
        if (id == null) {
            return "";
        }
        return id.replace("\\", "/");
    }

    /**
     * Removes stale alias entries that can appear after filesystem moves/renames
     * performed outside the NoteDAO cache ownership path.
     */
    private void removeCacheAliasesForIds(String previousId, String currentId) {
        String normalizedPreviousId = normalizeId(previousId);
        String normalizedCurrentId = normalizeId(currentId);
        if (normalizedCurrentId.equals(normalizedPreviousId) && cachedNotes.containsKey(normalizedCurrentId)) {
            return;
        }

        List<String> aliasKeys = new ArrayList<>();
        for (Map.Entry<String, Note> entry : cachedNotes.entrySet()) {
            String entryKey = normalizeId(entry.getKey());
            Note cached = entry.getValue();
            String cachedId = cached != null ? normalizeId(cached.getId()) : "";

            boolean previousAlias = !normalizedPreviousId.isBlank()
                    && cachedId.equals(normalizedPreviousId)
                    && !entryKey.equals(normalizedPreviousId);
            boolean currentAlias = !normalizedCurrentId.isBlank()
                    && cachedId.equals(normalizedCurrentId)
                    && !entryKey.equals(normalizedCurrentId);

            if (previousAlias || currentAlias) {
                aliasKeys.add(entry.getKey());
            }
        }

        for (String aliasKey : aliasKeys) {
            cachedNotes.remove(aliasKey);
            cachedNotes.remove(normalizeId(aliasKey));
            idToPathMap.remove(aliasKey);
            idToPathMap.remove(normalizeId(aliasKey));
        }
    }

    /** Removes cache entries whose backing files no longer exist on disk, then re-indexes the surviving entries. */
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

            // Safety: never let a transient filesystem hiccup wipe the whole cache. If
            // pruning would drop *every* note while we still hold some (e.g. iCloud
            // dataless files or Unicode-normalised names making Files.exists momentarily
            // false), skip this pass and keep the existing entries; the next interval
            // re-checks. Without this, the notes panel can flash completely empty.
            if (reindexed.isEmpty() && !cachedNotes.isEmpty()) {
                return;
            }
            // Update/insert the surviving entries first, then drop only the stale keys.
            // A plain clear()+putAll() leaves the (concurrent-read) cache momentarily
            // EMPTY between the two calls — a notes-list background load reading it in
            // that window would render an empty panel. Doing it this way means a reader
            // always sees a consistent, non-empty cache.
            cachedNotes.putAll(reindexed);
            cachedNotes.keySet().retainAll(reindexed.keySet());
            notesByFolderIndexDirty = true;
        } finally {
            FileSystemIoLock.LOCK.unlock();
        }
    }

    /** Calls {@link #pruneStaleCacheEntries()} at most once per {@link #PRUNE_INTERVAL_MS} to cap I/O overhead. */
    private void pruneStaleCacheEntriesIfNeeded() {
        long now = System.currentTimeMillis();
        if (now - lastPruneTimestampMs < PRUNE_INTERVAL_MS) {
            return;
        }
        lastPruneTimestampMs = now;
        pruneStaleCacheEntries();
    }

    /** Rebuilds the folder-key → notes index when the {@link #notesByFolderIndexDirty} flag is set (double-checked locking). */
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

    /** Returns the canonical index key for {@code folderId}, mapping null, blank, or "ROOT" to the constant {@link #ROOT_ID}. */
    private String normalizeFolderKey(String folderId) {
        if (folderId == null || folderId.isBlank() || ROOT_ID.equals(folderId)) {
            return ROOT_ID;
        }
        return normalizeId(folderId);
    }

    /** Extracts the parent directory portion of a note's relative path to use as the folder-index key. */
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
