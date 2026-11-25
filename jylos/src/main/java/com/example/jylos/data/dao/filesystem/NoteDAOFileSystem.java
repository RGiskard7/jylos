package com.example.jylos.data.dao.filesystem;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;

import com.example.jylos.config.LoggerConfig;
import com.example.jylos.data.models.interfaces.Component;
import com.example.jylos.data.dao.ExternalChanges;
import com.example.jylos.data.dao.interfaces.NoteDAO;
import com.example.jylos.data.models.Folder;
import com.example.jylos.data.models.Note;
import com.example.jylos.data.models.Tag;
import com.example.jylos.exceptions.DataAccessException;
import com.example.jylos.exceptions.InvalidParameterException;
import com.example.jylos.service.EncryptionService;
import com.example.jylos.util.AttachmentType;
import com.example.jylos.util.PathContainment;
import com.example.jylos.util.WikiLinkResolver;

/**
 * File System implementation of NoteDAO.
 * Stores notes as Markdown files with YAML frontmatter.
 *
 * @author Edu Díaz (RGiskard7)
 * @since 1.0.0
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
    /** Where folders went during this session; shared with the folder DAO of the same vault. */
    private FileSystemPathRedirects redirects = new FileSystemPathRedirects();
    // Cache to map Note ID -> Note object (Lightweight)
    private final Map<String, Note> cachedNotes = new ConcurrentHashMap<>();
    // Index: folderId -> notes (direct children only)
    private final Map<String, List<Note>> notesByFolderIndex = new ConcurrentHashMap<>();
    private static final long PRUNE_INTERVAL_MS = 3000L;
    /** Bytes read to tell whether a note in the trash is private. */
    private static final int LIGHTWEIGHT_READ_BYTES = 16_384;
    private volatile long lastPruneTimestampMs = 0L;

    // What each note says, kept between sessions so an unchanged note is not read again
    // (see NoteMetadataCache).
    /** Reads the stored cache; called once, on first use (see {@link #metadataCache()}). */
    private final Supplier<NoteMetadataCache> metadataCacheLoader;
    private final Object metadataCacheLock = new Object();
    private volatile NoteMetadataCache metadataCache;
    private final AtomicBoolean metadataCacheSaveScheduled = new AtomicBoolean();
    /** Set by {@link #close()}: the session is over and the cache is no longer written. */
    private volatile boolean closed;

    /** The modified time and size of a file, as last listed or written. */
    private record FileStamp(long modified, long size) {
        static FileStamp of(BasicFileAttributes attrs) {
            return new FileStamp(attrs.lastModifiedTime().toMillis(), attrs.size());
        }
    }

    /**
     * Each listed file's modified time and size as the vault last listed or wrote it: a file a
     * watcher reports with the same ones has not changed (the app's own write, or iCloud taking
     * its content off the disk) and is not read.
     */
    private final Map<String, FileStamp> knownStamps = new ConcurrentHashMap<>();

    /**
     * While a batch of outside changes is applied: the notes that left the list, by the modified
     * time and size they had ("" when two had the same ones). Moving a file keeps both, so a note
     * that arrives with them is that note moved: it takes what the cache knew of it, unread.
     */
    private final Map<FileStamp, String> leftThisBatch = new ConcurrentHashMap<>();
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
        this(rootDirectory, deferContentLoad, (Path) null);
    }

    /**
     * @param metadataCacheFile where what each note says is kept between sessions, or
     *        {@code null} to read every note on every start (see {@link NoteMetadataCache})
     */
    public NoteDAOFileSystem(String rootDirectory, boolean deferContentLoad, Path metadataCacheFile) {
        this(rootDirectory, deferContentLoad,
                () -> metadataCacheFile != null ? NoteMetadataCache.load(metadataCacheFile)
                        : NoteMetadataCache.inMemory());
    }

    /**
     * @param metadataCacheLoader reads the stored cache. With a deferred load it runs on the
     *        background loader: the vault is opened on the JavaFX thread while the window is
     *        built, and the cache of a big vault is megabytes of JSON.
     */
    NoteDAOFileSystem(String rootDirectory, boolean deferContentLoad,
            Supplier<NoteMetadataCache> metadataCacheLoader) {
        this.metadataCacheLoader = metadataCacheLoader;
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
            metadataStore.clearCache();
            rebuildCache(this::createLightweightNote);
            keepOnlyListedNotesInMetadataCache();
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
        metadataStore.clearCache();
        rebuildCache(this::createLightweightNote);
        // Called on the JavaFX thread by a refresh: the cache file (megabytes on a big vault)
        // is written on a background thread.
        metadataCache().retainOnly(Set.copyOf(idToPathMap.keySet()));
        saveMetadataCacheSoon();
    }

    /** The metadata cache, read from disk the first time it is needed. */
    private NoteMetadataCache metadataCache() {
        NoteMetadataCache cache = metadataCache;
        if (cache == null) {
            synchronized (metadataCacheLock) {
                cache = metadataCache;
                if (cache == null) {
                    cache = metadataCacheLoader.get();
                    metadataCache = cache;
                }
            }
        }
        return cache;
    }

    /**
     * Drops the cached notes no longer in the vault and writes the cache if it changed, on the
     * calling thread: the loader's (background, or the synchronous constructor's).
     */
    private void keepOnlyListedNotesInMetadataCache() {
        NoteMetadataCache cache = metadataCache();
        cache.retainOnly(Set.copyOf(idToPathMap.keySet()));
        cache.save();
    }

    /**
     * Writes the metadata cache now if it was read this session, and stops writing it: a write
     * scheduled before must not overwrite the file of the session that opens the vault next.
     */
    @Override
    public void close() {
        closed = true;
        NoteMetadataCache cache = metadataCache;
        if (cache != null) {
            cache.save();
        }
    }

    /** Writes the metadata cache a little later, once, however many notes change meanwhile. */
    private void saveMetadataCacheSoon() {
        if (closed || !metadataCacheSaveScheduled.compareAndSet(false, true)) {
            return;
        }
        Thread saver = new Thread(() -> {
            try {
                Thread.sleep(2000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            metadataCacheSaveScheduled.set(false);
            if (!closed) {
                metadataCache().save();
            }
        }, "vault-metadata-cache-save");
        saver.setDaemon(true);
        saver.start();
    }

    @Override
    public void reindexMovedNote(String previousNoteId, Note movedNote) {
        if (movedNote == null || movedNote.getId() == null) {
            return;
        }
        FileSystemIoLock.LOCK.lock();
        try {
            String previousId = FileSystemNames.normalizeId(previousNoteId);
            String currentId = FileSystemNames.normalizeId(movedNote.getId());
            Path currentPath = rootPath.resolve(currentId.replace("/", File.separator)).toAbsolutePath().normalize();

            if (!previousId.isBlank() && !previousId.equals(currentId)) {
                cachedNotes.remove(previousId);
                idToPathMap.remove(previousId);
            }
            removeCacheAliasesForIds(previousId, currentId);
            metadataStore.clearCache();

            if (Files.exists(currentPath)) {
                idToPathMap.put(currentId, currentPath);
                cachedNotes.put(currentId, movedNote);
                carryMovedFile(previousId, currentId, currentPath);
            } else {
                idToPathMap.remove(currentId);
                cachedNotes.remove(currentId);
            }
            notesByFolderIndexDirty = true;
        } finally {
            FileSystemIoLock.LOCK.unlock();
        }
    }

    /**
     * Fast cache build that reads only filesystem metadata (filename + timestamps),
     * never file <em>contents</em>. Used by the deferred constructor so startup does
     * not block on reading (and, on iCloud, downloading) every file.
     */
    private void refreshCacheMetadataOnly() {
        metadataStore.clearCache();
        rebuildCache(this::createMetadataNote);
    }

    /**
     * Walks the vault and rebuilds {@code idToPathMap}/{@code cachedNotes}, building
     * each note with {@code noteFactory}. The factory decides how much I/O happens per
     * file (full head read vs metadata only) — that is the only difference between the
     * full and metadata-only refreshes.
     */
    private void rebuildCache(BiFunction<String, Path, Note> noteFactory) {
        FileSystemIoLock.LOCK.lock();
        try {
            idToPathMap.clear();
            cachedNotes.clear();
            knownStamps.clear();
            try (Stream<Path> walk = Files.walk(rootPath)) {
                // Parallel walk for faster initial load of thousands of files.
                walk.filter(Files::isRegularFile)
                        // Markdown notes plus viewable attachments (PDF, images) so the vault
                        // lists them alongside notes (Obsidian-style).
                        .filter(p -> p.toString().endsWith(".md")
                                || AttachmentType.isAttachment(p.getFileName().toString()))
                        // Exclude hidden entries inside the vault (.trash, .git); a hidden
                        // directory *above* the vault (e.g. ~/.config) must not hide it all.
                        .filter(p -> !FileSystemNames.isHiddenWithinVault(rootPath, p))
                        .parallel()
                        .forEach(path -> {
                            String relativePath = FileSystemNames.normalizeId(rootPath.relativize(path).toString());
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
                metadataCache();   // here, before the notes that need it are read, and off the UI thread
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
                        pool.awaitTermination(CONTENT_LOAD_LIMIT_MS, TimeUnit.MILLISECONDS);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                    }
                }
                notesByFolderIndexDirty = true;
                logger.info("Vault content load complete (" + snapshot.size() + " notes)");
                keepOnlyListedNotesInMetadataCache();
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
        if (AttachmentType.isAttachment(filename)) {
            metadataStore.applyDocumentMetadata(id, note);
        }
        return note;
    }

    /**
     * The note the list shows: its properties, the start of its text, its tags and its links.
     * They come from the metadata cache when the file has the modified time and size they were
     * read from, without reading it; otherwise the file is read whole once and the cache keeps
     * what it says.
     */
    private Note createLightweightNote(String id, Path path) {
        String filename = path.getFileName().toString();
        if (isCanvasFile(filename)) {
            Note canvas = createMetadataNote(id, path);
            canvas.setTitle(filename);
            rememberStamp(id, path);
            return canvas;
        }
        String title = filename.endsWith(".md") ? filename.substring(0, filename.length() - 3) : filename;

        // Attachments (PDF, images) are binary: never parse them as Markdown/frontmatter.
        // Keep the full filename as title so the list shows "report.pdf", "diagram.png".
        if (AttachmentType.isAttachment(filename)) {
            Note attachment = new Note(id, title, "");
            attachment.setContentComplete(false);
            applyFileTimestampsIfMissing(attachment, path);
            metadataStore.applyDocumentMetadata(id, attachment);
            rememberStamp(id, path);
            return attachment;
        }

        BasicFileAttributes attrs;
        try {
            attrs = Files.readAttributes(path, BasicFileAttributes.class);
        } catch (IOException e) {
            return new Note(id, title, "");
        }
        knownStamps.put(id, FileStamp.of(attrs));
        try {
            NoteMetadataCache.Metadata metadata = metadataOf(id, path, attrs);
            Note note = FrontmatterHandler.parseLightweight(metadata.lightweight());
            note.setId(id);
            note.setTitle(title);
            note.setContentComplete(false);
            metadataStore.applyNoteMarks(id, note);
            applyFileTimestampsIfMissing(note, attrs);
            note.setLinkTargets(metadata.links());
            // The text of a private note is encrypted on disk: what it says is not known here.
            if (!note.isPrivate()) {
                note.setFileLinkTargets(metadata.fileLinks());
                note.setMetadataComplete(true);
            }
            return note;
        } catch (IOException | RuntimeException e) {
            logger.log(Level.FINE, "Failed lightweight read for: " + path, e);
            Note fallback = new Note(id, title, "");
            fallback.setContentComplete(false);
            applyFileTimestampsIfMissing(fallback, attrs);
            return fallback;
        }
    }

    /** What the note of {@code path} says: from the cache, or read from the file and cached. */
    private NoteMetadataCache.Metadata metadataOf(String id, Path path, BasicFileAttributes attrs)
            throws IOException {
        long modified = attrs.lastModifiedTime().toMillis();
        long size = attrs.size();
        Optional<NoteMetadataCache.Metadata> cached = metadataCache().get(id, modified, size);
        if (cached.isPresent()) {
            return cached.get();
        }
        String movedFrom = leftThisBatch.get(FileStamp.of(attrs));
        Optional<NoteMetadataCache.Metadata> moved = movedFrom != null && !movedFrom.isEmpty()
                ? metadataCache().get(movedFrom, modified, size) : Optional.empty();
        if (moved.isPresent()) {
            metadataCache().put(id, modified, size, moved.get());
            return moved.get();
        }
        // Read after the stat: if the file changes meanwhile, the entry does not match it and
        // the note is read again next time.
        NoteMetadataCache.Metadata read = NoteMetadataCache.Metadata.of(readText(path));
        metadataCache().put(id, modified, size, read);
        return read;
    }

    /** Keeps the modified time and size of the file of {@code id} as it is now. */
    private void rememberStamp(String id, Path path) {
        try {
            knownStamps.put(id, FileStamp.of(Files.readAttributes(path, BasicFileAttributes.class)));
        } catch (IOException e) {
            knownStamps.remove(id);
        }
    }

    /**
     * After the app wrote the file of {@code id}: keeps its modified time and size, so a
     * watcher's report of this write is not taken for a change from outside, and caches what
     * the note says from the text written, so the next start does not read it.
     *
     * @param markdown the text written, for a note; null for an attachment or canvas
     */
    private void rememberWritten(String id, Path path, String markdown) {
        try {
            BasicFileAttributes attrs = Files.readAttributes(path, BasicFileAttributes.class);
            knownStamps.put(id, FileStamp.of(attrs));
            if (markdown != null) {
                metadataCache().put(id, attrs.lastModifiedTime().toMillis(), attrs.size(),
                        NoteMetadataCache.Metadata.of(markdown));
                saveMetadataCacheSoon();
            }
        } catch (IOException e) {
            knownStamps.remove(id);
        }
    }

    /** A note the app moved keeps what the cache knows of it, under its new id. */
    private void carryMovedFile(String previousId, String currentId, Path currentPath) {
        FileStamp before = knownStamps.remove(previousId);
        rememberStamp(currentId, currentPath);
        FileStamp now = knownStamps.get(currentId);
        if (before != null && before.equals(now)) {
            metadataCache().get(previousId, before.modified(), before.size())
                    .ifPresent(metadata -> metadataCache().put(currentId, now.modified(), now.size(), metadata));
        }
        metadataCache().remove(previousId);
        saveMetadataCacheSoon();
    }

    /** Forgets the file of {@code id}: listed stamp and cached metadata. */
    private void forgetFile(String id) {
        knownStamps.remove(id);
        metadataCache().remove(id);
    }

    /**
     * Applies the files a watcher reported as changed outside the app, one by one and without
     * walking the vault (see {@link NoteDAO#applyExternalChanges}): a file whose modified time
     * and size are the ones listed is left alone, unread; a new or changed one is listed again
     * (from the metadata cache, or read); a missing one leaves the list with everything under
     * it; a folder brings what it holds. Hidden paths, temporary files of an atomic write and
     * files that are neither notes nor attachments are not notes.
     */
    @Override
    public ExternalChanges applyExternalChanges(Collection<Path> paths) {
        Set<String> created = new LinkedHashSet<>();
        Set<String> changed = new LinkedHashSet<>();
        Set<String> deleted = new LinkedHashSet<>();
        boolean folders = false;
        Map<String, String> renamed = new LinkedHashMap<>();
        Path root = rootPath.toAbsolutePath().normalize();
        // What is gone first: a note moved in this batch then arrives with what it had.
        List<Path> ordered = new ArrayList<>(paths);
        ordered.sort(Comparator.comparing(reported -> Files.exists(reported) ? 1 : 0));
        FileSystemIoLock.LOCK.lock();
        try {
            for (Path reported : ordered) {
                Path path = reported.toAbsolutePath().normalize();
                if (!path.startsWith(root) || FileSystemNames.isHiddenWithinVault(root, path)
                        || path.getFileName().toString().endsWith(".tmp")) {
                    continue;
                }
                if (Files.isDirectory(path)) {
                    // A folder (or the vault itself) is brought up to date both ways: what is in
                    // it now is listed, what the list has under it and is gone leaves the list.
                    folders = true;
                    Set<String> present = new HashSet<>();
                    try (Stream<Path> walk = Files.walk(path)) {
                        for (Path file : walk.filter(Files::isRegularFile)
                                .filter(file -> isListedKind(file) && !FileSystemNames.isHiddenWithinVault(root, file))
                                .toList()) {
                            present.add(listAgain(root, file, created, changed));
                        }
                    } catch (IOException | RuntimeException e) {
                        logger.log(Level.FINE, "Could not read the folder " + path, e);
                        continue;
                    }
                    String inside = path.equals(root) ? "" : FileSystemNames.normalizeId(root.relativize(path).toString()) + "/";
                    for (String known : new ArrayList<>(idToPathMap.keySet())) {
                        if (known.startsWith(inside) && !present.contains(known)
                                && !FileSystemNames.isHiddenWithinVault(root, idToPathMap.get(known))) {
                            unlist(known);
                            deleted.add(known);
                        }
                    }
                } else if (Files.isRegularFile(path)) {
                    if (isListedKind(path)) {
                        listAgain(root, path, created, changed);
                    }
                } else {
                    // Known by its date and size even if the list already dropped it (see
                    // pruneStaleCacheEntries): a move is still found.
                    String id = FileSystemNames.normalizeId(root.relativize(path).toString());
                    if (idToPathMap.containsKey(id) || knownStamps.containsKey(id)) {
                        unlist(id);
                        deleted.add(id);
                        continue;
                    }
                    String inside = id + "/";
                    Set<String> knownIds = new HashSet<>(idToPathMap.keySet());
                    knownIds.addAll(knownStamps.keySet());
                    for (String known : knownIds) {
                        if (known.startsWith(inside)) {
                            unlist(known);
                            deleted.add(known);
                            folders = true;
                        }
                    }
                    // A missing name that was no note may have been a folder (empty, or the app's own).
                    if (!isListedKind(path)) {
                        folders = true;
                    }
                }
            }
            renamed.putAll(movesIn(created));
            for (String gone : deleted) {
                metadataCache().remove(gone);
            }
            if (!created.isEmpty() || !changed.isEmpty() || !deleted.isEmpty()) {
                notesByFolderIndexDirty = true;
            }
        } finally {
            leftThisBatch.clear();
            FileSystemIoLock.LOCK.unlock();
        }
        if (!created.isEmpty() || !changed.isEmpty() || !deleted.isEmpty()) {
            saveMetadataCacheSoon();
        }
        return new ExternalChanges(created, changed, deleted, folders, renamed);
    }

    /**
     * The notes of this batch that moved: one gone and one {@code created} with the same modified
     * time and size, and no other with them. When it cannot be told which went where, none.
     */
    private Map<String, String> movesIn(Set<String> created) {
        Map<FileStamp, List<String>> arrived = new HashMap<>();
        for (String id : created) {
            FileStamp stamp = knownStamps.get(id);
            if (stamp != null) {
                arrived.computeIfAbsent(stamp, key -> new ArrayList<>()).add(id);
            }
        }
        Map<String, String> moves = new LinkedHashMap<>();
        leftThisBatch.forEach((stamp, from) -> {
            List<String> to = arrived.get(stamp);
            if (!from.isEmpty() && to != null && to.size() == 1) {
                moves.put(from, to.get(0));
            }
        });
        return moves;
    }

    /** Whether the vault lists a file of this kind: a note or an attachment. */
    private static boolean isListedKind(Path file) {
        String name = file.getFileName().toString();
        return name.endsWith(".md") || AttachmentType.isAttachment(name);
    }

    /**
     * Lists {@code file} again if it is new or its modified time or size changed.
     *
     * @return its id
     */
    private String listAgain(Path root, Path file, Set<String> created, Set<String> changed) {
        String id = FileSystemNames.normalizeId(root.relativize(file).toString());
        FileStamp now;
        try {
            now = FileStamp.of(Files.readAttributes(file, BasicFileAttributes.class));
        } catch (IOException e) {
            return id;
        }
        boolean known = idToPathMap.containsKey(id);
        if (known && now.equals(knownStamps.get(id))) {
            return id;
        }
        Note note = createLightweightNote(id, file);
        idToPathMap.put(id, file);
        cachedNotes.put(id, note);
        (known ? changed : created).add(id);
        return id;
    }

    /**
     * Takes the note {@code id} out of the list, keeping its modified time and size for this
     * batch (see {@link #leftThisBatch}); its cache entry goes at the end of the batch.
     */
    private void unlist(String id) {
        idToPathMap.remove(id);
        cachedNotes.remove(id);
        FileStamp stamp = knownStamps.remove(id);
        if (stamp != null) {
            leftThisBatch.merge(stamp, id, (first, second) -> "");
        }
    }

    /** The whole file as UTF-8, a malformed byte read as U+FFFD (as the list always read it). */
    private static String readText(Path path) throws IOException {
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }

    /**
     * A value worked out from the text of note {@code id} (without its properties) under
     * {@code key}: kept in the metadata cache with the note, and worked out again only when the
     * file changes. Empty for a note that is not a Markdown file of the vault, or that is
     * private: its text is encrypted on disk, and what it says is never kept.
     */
    @Override
    public Optional<String> derived(String id, String key, Function<String, String> fromText) {
        Path path = id != null ? idToPathMap.get(FileSystemNames.normalizeId(id)) : null;
        if (path == null || !path.getFileName().toString().endsWith(".md")) {
            return Optional.empty();
        }
        String noteId = FileSystemNames.normalizeId(id);
        try {
            BasicFileAttributes attrs = Files.readAttributes(path, BasicFileAttributes.class);
            long modified = attrs.lastModifiedTime().toMillis();
            long size = attrs.size();
            NoteMetadataCache cache = metadataCache();
            Optional<String> stored = cache.derived(noteId, modified, size, key);
            if (stored.isPresent()) {
                return stored;
            }
            String text = readText(path);
            NoteMetadataCache.Metadata metadata = NoteMetadataCache.Metadata.of(text);
            if (metadata.yaml() != null && FrontmatterHandler.parseLightweight(metadata.lightweight()).isPrivate()) {
                return Optional.empty();
            }
            String value = fromText.apply(FrontmatterHandler.bodyOf(text));
            if (cache.get(noteId, modified, size).isEmpty()) {
                cache.put(noteId, modified, size, metadata);
            }
            if (value != null && cache.putDerived(noteId, modified, size, key, value)) {
                saveMetadataCacheSoon();
            }
            return Optional.ofNullable(value);
        } catch (IOException | RuntimeException e) {
            logger.log(Level.FINE, "Could not work out " + key + " for " + id, e);
            return Optional.empty();
        }
    }

    private static boolean isCanvasFile(String filename) {
        return AttachmentType.fromName(filename) == AttachmentType.CANVAS;
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
            applyFileTimestampsIfMissing(note, Files.readAttributes(path, BasicFileAttributes.class));
        } catch (IOException e) {
            logger.log(Level.FINE, "Could not read file timestamps for: " + path, e);
        }
    }

    private static void applyFileTimestampsIfMissing(Note note, BasicFileAttributes attrs) {
        if (note.getCreatedDate() == null || note.getCreatedDate().isBlank()) {
            note.setCreatedDate(DateTimeFormatter.ISO_INSTANT.format(attrs.creationTime().toInstant()));
        }
        if (note.getModifiedDate() == null || note.getModifiedDate().isBlank()) {
            note.setModifiedDate(DateTimeFormatter.ISO_INSTANT.format(attrs.lastModifiedTime().toInstant()));
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
            String suggestedId = FileSystemNames.normalizeId(note.getId());

            if (!suggestedId.isEmpty()) {
                // Check if the ID implies a folder path (e.g. "Folder/Note.md" or just
                // "Folder/")
                // If the ID comes from MainController as "Folder/New Note", we want to use
                // "Folder" as parent.
                if (suggestedId.contains("/")) {
                    int lastSeparator = suggestedId.lastIndexOf('/');
                    String folderPath = suggestedId.substring(0, lastSeparator);
                    // A suggested parent outside the vault is ignored: the note goes to the root.
                    if (isInsideVault(folderPath)) {
                        Path potentialDir = rootPath.resolve(folderPath.replace("/", File.separator));
                        if (Files.exists(potentialDir) && Files.isDirectory(potentialDir)) {
                            parentDir = potentialDir;
                        }
                    }
                }
            }

            // Attachments (e.g. .canvas) keep their own extension and are written as raw
            // bytes — no ".md" suffix and no frontmatter wrapper.
            boolean attachment = AttachmentType.isAttachment(note.getTitle());
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
            String relativePath = FileSystemNames.normalizeId(rootPath.relativize(filePath).toString());
            note.setId(relativePath);

            try {
                String fileContent;
                if (!attachment) {
                    fileContent = FrontmatterHandler.generate(note);
                } else if (isCanvasFile(filename)) {
                    fileContent = metadataStore.createCanvasDocument(note.getContent());
                } else {
                    fileContent = note.getContent() != null ? note.getContent() : "";
                }
                FileSystemAtomicWriter.writeString(filePath, fileContent, StandardCharsets.UTF_8);
                if (attachment) {
                    metadataStore.persistDocumentMetadata(note);
                } else {
                    // Also clears marks left by a note that was at this path before.
                    metadataStore.persistNoteMarks(note, FrontmatterHandler.claimsMarks(fileContent));
                    // Its tags are the ones its text has, inline #tags included, as after an edit
                    // (see updateNote): a note created with its text already written has them now.
                    note.setTags(FrontmatterHandler.parse(fileContent).getTags());
                }
                idToPathMap.put(relativePath, filePath);
                cachedNotes.put(relativePath, note);
                notesByFolderIndexDirty = true;
                rememberWritten(relativePath, filePath, attachment ? null : fileContent);
                return relativePath;
            } catch (IOException e) {
                logger.log(Level.SEVERE, "Failed to write note file: " + filePath, e);
                throw new DataAccessException("Failed to create note: " + note.getTitle(), e);
            }
        } finally {
            FileSystemIoLock.LOCK.unlock();
        }
    }

    @Override
    public Optional<Path> storageRoot() {
        return Optional.of(rootPath.toAbsolutePath().normalize());
    }

    /**
     * Scans the Markdown files themselves (trash included): during the deferred startup
     * load the note cache holds metadata only, so it cannot tell private notes apart.
     */
    @Override
    public boolean hasPrivateNotes() {
        try (Stream<Path> walk = Files.walk(rootPath)) {
            return walk.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().endsWith(".md"))
                    .anyMatch(this::isPrivateNoteFile);
        } catch (IOException | UncheckedIOException e) {
            logger.log(Level.WARNING, "Could not scan vault for private notes", e);
            return false;
        }
    }

    private boolean isPrivateNoteFile(Path path) {
        try {
            // Every encrypted note carries the private flag in its frontmatter.
            return FrontmatterHandler.parseLightweight(readFileHead(path, LIGHTWEIGHT_READ_BYTES), 0).isPrivate();
        } catch (IOException | RuntimeException e) {
            return false;
        }
    }

    @Override
    public Optional<Path> resolveFilePath(String id) {
        if (id == null || !isInsideVault(id)) {
            return Optional.empty();
        }
        Path path = idToPathMap.get(id);
        if (path == null) {
            path = idToPathMap.get(FileSystemNames.normalizeId(id));
        }
        if (path == null) {
            Path potential = rootPath.resolve(FileSystemNames.normalizeId(id).replace("/", File.separator));
            if (Files.exists(potential)) {
                path = potential.toAbsolutePath().normalize();
            }
        }
        return Optional.ofNullable(path).filter(Files::exists);
    }

    /** Reads and fully parses the Markdown file for {@code id}, resolving the path from the cache or disk. */
    @Override
    public Note getNoteById(String id) {
        if (id == null || !isInsideVault(id))
            return null;

        String normalizedId = FileSystemNames.normalizeId(id);
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
                // Read before a folder above it was renamed or moved: follow it there.
                String moved = redirects.current(normalizedId);
                boolean there = !moved.equals(normalizedId)
                        && Files.exists(rootPath.resolve(moved.replace("/", File.separator)));
                return there ? getNoteById(moved) : null;
            }
        }

        // Attachments (PDF, images) are binary — return their metadata, never decode
        // the bytes as Markdown text.
        if (AttachmentType.isAttachment(path.getFileName().toString())) {
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
            metadataStore.applyNoteMarks(normalizedId, note);
            // The file's dates when its properties do not give them, as when the vault is listed.
            applyFileTimestampsIfMissing(note, path);

            // Sync Title with Filename
            String filename = path.getFileName().toString();
            if (filename.endsWith(".md"))
                filename = filename.substring(0, filename.length() - 3);
            note.setTitle(filename);

            // Full read → full-body link coverage for the backlink index.
            note.setLinkTargets(FrontmatterHandler.linksOf(content));

            return note;
        } catch (IOException e) {
            // A single failed read is recoverable: callers handle the null return. Log at
            // WARNING without a stack trace so a slow/offline drive or an iCloud-offloaded
            // file ("Operation timed out") cannot flood the log with SEVERE entries.
            logger.warning("Could not read note file (skipped): " + path + " — " + e.getMessage());
            return null;
        }
    }

    /** Shares where folders went with the folder DAO of the same vault. */
    void usePathRedirects(FileSystemPathRedirects shared) {
        this.redirects = shared;
    }

    /** Persists changes to an existing note file; renames the file when the title has changed. */
    @Override
    public void updateNote(Note note) {
        updateNote(note, true);
    }

    /**
     * @param resyncInlineTags whether to re-derive tags from the body just written and
     *                         merge them into {@code note}'s tag list (see below). Must be
     *                         {@code false} from {@link #addTag} / {@link #removeTag}: those
     *                         calls are the user explicitly changing the tag list, and an
     *                         inline {@code #tag} occurrence left in the body — removing a
     *                         tag chip does not edit body text — would otherwise resurrect
     *                         the very tag the user just removed on this same call.
     */
    private void updateNote(Note note, boolean resyncInlineTags) {
        FileSystemIoLock.LOCK.lock();
        try {
            if (note == null || note.getId() == null)
                throw new InvalidParameterException("Invalid note");
            requireInsideVault(note.getId());

            String normalizedId = FileSystemNames.normalizeId(note.getId());
            note.setId(normalizedId);
            Path path = idToPathMap.get(note.getId());
            if (path == null) {
                path = idToPathMap.get(normalizedId);
            }
            if (path == null) {
                // Check if file exists at ID location
                path = rootPath.resolve(normalizedId.replace("/", File.separator));
            }
            if (!Files.exists(path)) {
                // Held since before a folder above it was renamed or moved: save it where it is
                // now, not as a copy under the old folder.
                String moved = redirects.current(normalizedId);
                Path movedPath = rootPath.resolve(moved.replace("/", File.separator));
                if (!moved.equals(normalizedId) && Files.exists(movedPath)) {
                    path = movedPath.toAbsolutePath().normalize();
                    note.setId(moved);
                }
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
            if (missingOnDisk && !attachment && !note.isContentComplete()) {
                logger.warning("Attempted to recreate missing Markdown note from incomplete state: " + note.getId());
                removeCacheAliasesForIds(normalizedId, normalizedId);
                notesByFolderIndexDirty = true;
                throw new DataAccessException("Cannot recreate missing Markdown note from incomplete state: "
                        + note.getId(), null);
            }
            if (!attachment) {
                try {
                    ensureMarkdownContentComplete(note, path);
                    FrontmatterHandler.importLeadingFrontmatter(note);
                } catch (IOException e) {
                    throw new DataAccessException("Failed to load Markdown note before updating: " + note.getId(), e);
                } catch (IllegalArgumentException e) {
                    throw new DataAccessException("Invalid YAML frontmatter in note: " + note.getId(), e);
                }
            }
            String currentFilename = path.getFileName().toString();
            String expectedFilename = attachment
                    ? expectedAttachmentFilename(currentFilename, note.getTitle())
                    : sanitizeFilename(note.getTitle()) + ".md";

            if (!currentFilename.equals(expectedFilename)) {
                Path newPath = path.resolveSibling(expectedFilename);
                if (!Files.exists(newPath) && missingOnDisk) {
                    String oldId = normalizedId;
                    String newId = FileSystemNames.normalizeId(rootPath.relativize(newPath).toString());
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

                        String newId = FileSystemNames.normalizeId(rootPath.relativize(newPath).toString());
                        if (usesSidecarMetadata(newPath)) {
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

            String markdownOnDisk = null;
            try {
                if (missingOnDisk && path.getParent() != null) {
                    Files.createDirectories(path.getParent());
                }
                if (!attachment) {
                    // The file as it is now: what it had stays as it was unless the note changed it.
                    String previous = !missingOnDisk && Files.exists(path)
                            ? new String(Files.readAllBytes(path), StandardCharsets.UTF_8) : null;
                    String serialized = FrontmatterHandler.generate(note, previous);
                    markdownOnDisk = serialized;
                    if (!serialized.equals(previous)) {
                        // Nothing changed (a mark, say, which lives in .jylos/): the file is left alone.
                        FileSystemAtomicWriter.writeString(path, serialized, StandardCharsets.UTF_8);
                    }
                    note.setAddedFrontmatter(null); // what a template added is in the file now
                    metadataStore.persistNoteMarks(note, FrontmatterHandler.claimsMarks(serialized));
                    if (resyncInlineTags) {
                        // The caller may have just typed a new inline #tag; note.getTags() at
                        // this point still holds whatever was in memory before this edit, since
                        // tag extraction only happens while parsing. Re-deriving from the very
                        // text just written — the same round-trip a fresh read would do — keeps
                        // the cached note (and whatever this call returns to the caller) in sync
                        // immediately, instead of only after the note is reopened.
                        note.setTags(FrontmatterHandler.parse(serialized).getTags());
                    }
                } else if (isCanvasFile(path.getFileName().toString())) {
                    String content = note.getContent();
                    // Canvas list entries are metadata-only. Do not let a favorite/pinned
                    // toggle rewrite a real .canvas file with an empty lightweight body.
                    if (content != null && !content.isBlank()) {
                        try {
                            // A private canvas is stored encrypted, as a private note's body: written
                            // as it is, not as canvas JSON.
                            String stored = EncryptionService.isEncrypted(content)
                                    ? content
                                    : metadataStore.normalizeCanvasDocument(content);
                            FileSystemAtomicWriter.writeString(path, stored, StandardCharsets.UTF_8);
                        } catch (IllegalArgumentException e) {
                            throw new DataAccessException("Invalid canvas document: " + note.getId(), e);
                        }
                    }
                } else {
                    metadataStore.persistDocumentMetadata(note);
                }
                if (attachment && isCanvasFile(path.getFileName().toString())) {
                    metadataStore.persistDocumentMetadata(note);
                }
                String currentId = FileSystemNames.normalizeId(note.getId());
                removeCacheAliasesForIds(normalizedId, currentId);
                if (!normalizedId.equals(currentId)) {
                    cachedNotes.remove(normalizedId);
                    idToPathMap.remove(normalizedId);
                }
                cachedNotes.put(currentId, note);
                idToPathMap.put(currentId, path);
                notesByFolderIndexDirty = true;
                if (!normalizedId.equals(currentId)) {
                    forgetFile(normalizedId);
                }
                rememberWritten(currentId, path, markdownOnDisk);
            } catch (IOException e) {
                throw new DataAccessException("Failed to update note file: " + path, e);
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
        if ((note.getStructuredFrontmatterProperties() == null || note.getStructuredFrontmatterProperties().isEmpty())
                && full.getStructuredFrontmatterProperties() != null) {
            note.setStructuredFrontmatterProperties(full.getStructuredFrontmatterProperties());
        }
        if ((note.getDisplayableFrontmatterPropertyKeys() == null || note.getDisplayableFrontmatterPropertyKeys().isEmpty())
                && full.getDisplayableFrontmatterPropertyKeys() != null) {
            note.setDisplayableFrontmatterPropertyKeys(full.getDisplayableFrontmatterPropertyKeys());
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
        requireInsideVault(id);
        // Listed before a folder above it was renamed or moved: the note where it is now.
        String normalizedId = redirects.locateId(rootPath, id);

        Path sourcePath = idToPathMap.get(id);
        if (sourcePath == null) {
            sourcePath = idToPathMap.get(normalizedId);
        }
        if (sourcePath == null || !Files.exists(sourcePath)) {
            Path candidate = redirects.locate(rootPath, normalizedId);
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
                Path trashDir = rootPath.resolve(FileSystemNames.TRASH_DIR);
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
                    String trashedId = FileSystemNames.normalizeId(rootPath.relativize(targetPath).toString());
                    metadataStore.moveDocumentMetadata(normalizedId, trashedId);
                }

                // Remove from cache (by exact and normalized key/path)
                idToPathMap.remove(id);
                idToPathMap.remove(normalizedId);
                knownStamps.remove(normalizedId);
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
                throw new DataAccessException("Could not move note to the trash: " + id, e);
            }
        } else {
            // Idempotent delete: if already in trash or already gone, do not treat as warning.
            Path trashedPath = rootPath.resolve(FileSystemNames.TRASH_DIR).resolve(normalizedId.replace("/", File.separator));
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
            Path trashPath = rootPath.resolve(FileSystemNames.TRASH_DIR);
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
        requireInsideVault(id);
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
                source = rootPath.resolve(FileSystemNames.TRASH_DIR).resolve(filename);
            }

            if (Files.exists(source)) {
                // Calculate original relative path
                String originalRelPath = FileSystemNames.originalPathOfTrashed(normalizedId);

                Path target = rootPath.resolve(originalRelPath.replace("/", File.separator));

                // If parent folder was deleted (not restored), restore to root instead of
                // recreating the folder
                if (target.getParent() != null && !Files.exists(target.getParent())) {
                    String filenameOrig = target.getFileName().toString();
                    target = rootPath.resolve(filenameOrig);
                }

                // Handle conflicts
                if (Files.exists(target)) {
                    target = target.resolveSibling(FileSystemNames.withSuffix(target.getFileName().toString(),
                            "_restored_" + System.currentTimeMillis()));
                }

                // Ensure parent exists (always true if it's rootPath, but safe to keep)
                if (target.getParent() != null && !Files.exists(target.getParent())) {
                    Files.createDirectories(target.getParent());
                }

                Files.move(source, target);
                if (usesSidecarMetadata(target)) {
                    String restoredId = FileSystemNames.normalizeId(rootPath.relativize(target).toString());
                    metadataStore.moveDocumentMetadata(normalizedId, restoredId);
                }

                // Update cache
                refreshCache();
            }
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Failed to restore note: " + id, e);
            throw new DataAccessException("Could not restore note: " + id, e);
        }
        } finally {
            FileSystemIoLock.LOCK.unlock();
        }
    }

    /** Irrecoverably deletes the file (typically under {@code .trash}) and removes it from all caches. */
    @Override
    public void permanentlyDeleteNote(String id) {
        if (id == null || id.isBlank()) {
            return;
        }
        requireInsideVault(id);
        FileSystemIoLock.LOCK.lock();
        try {
        try {
            String normalizedId = FileSystemNames.normalizeId(id);
            Path path = rootPath.resolve(normalizedId.replace("/", File.separator)); // id starts with .trash/ usually

            if (Files.exists(path)) {
                if (usesSidecarMetadata(path)) {
                    metadataStore.deleteDocumentMetadata(normalizedId);
                }
                Files.delete(path);
                idToPathMap.remove(id);
                idToPathMap.remove(normalizedId);
                knownStamps.remove(normalizedId);
                cachedNotes.remove(id);
                cachedNotes.remove(normalizedId);
                notesByFolderIndexDirty = true;
            } else {
                // Try fallback to filename in trash root
                String filename = path.getFileName().toString();
                Path fallback = rootPath.resolve(FileSystemNames.TRASH_DIR).resolve(filename);
                if (Files.exists(fallback)) {
                    Files.delete(fallback);
                }
            }
            // Also remove from cache
            cachedNotes.remove(id);
            cachedNotes.remove(FileSystemNames.normalizeId(id));
            notesByFolderIndexDirty = true;
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Failed to permanently delete note: " + id, e);
            throw new DataAccessException("Could not delete note: " + id, e);
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
        // pruneStaleCacheEntriesIfNeeded existed but was never wired into any call site —
        // the cache never self-healed when a file was deleted/moved outside the app (an
        // external sync tool, another process). Rate-limited to once per PRUNE_INTERVAL_MS
        // by design, so it is cheap to call on every read.
        pruneStaleCacheEntriesIfNeeded();
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
            updateNote(note, false);
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
            updateNote(note, false);
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
                // Case-insensitive: an inline #tag keeps whatever case the author typed,
                // so "#Project" and "#project" in different notes must match the same tag.
                if (t.getTitle() != null && t.getTitle().equalsIgnoreCase(tagId)) {
                    all.add(n);
                    break;
                }
            }
        }
        return all;
    }

    /** Replaces characters that are illegal in filenames with underscores while preserving Unicode letters and digits. */
    private String sanitizeFilename(String title) {
        return FileSystemNames.sanitize(title, "note");
    }

    private boolean isAttachmentNote(Path path, Note note) {
        return (path != null && AttachmentType.isAttachment(path.getFileName().toString()))
                || (note != null && AttachmentType.isAttachment(note.getTitle()));
    }

    /**
     * The notes whose properties an older Jylos wrote ({@code id}, {@code favorite},
     * {@code pinned}, {@code deleted}…), by path, the trash's included. Hidden folders such as
     * {@code .obsidian} are not notes. Reads only.
     */
    public List<String> notesWithJylosFields() {
        FileSystemIoLock.LOCK.lock();
        try (Stream<Path> walk = Files.walk(rootPath)) {
            return walk.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".md"))
                    .filter(path -> !inHiddenFolder(path))
                    .filter(path -> {
                        try {
                            return FrontmatterHandler.hasJylosFields(
                                    new String(Files.readAllBytes(path), StandardCharsets.UTF_8));
                        } catch (IOException e) {
                            logger.log(Level.WARNING, "Could not read " + path, e);
                            return false;
                        }
                    })
                    .map(path -> FileSystemNames.normalizeId(rootPath.relativize(path).toString()))
                    .sorted()
                    .toList();
        } catch (IOException e) {
            throw new DataAccessException("Could not read the vault", e);
        } finally {
            FileSystemIoLock.LOCK.unlock();
        }
    }

    /**
     * Takes the fields an older Jylos wrote out of these notes. Each one is first copied to
     * {@code backupDir}, at the same path; its favorite and pinned marks move to Jylos' data
     * unless that already has marks for it. A note whose properties Jylos did not write is left
     * alone.
     *
     * @return how many notes were cleaned
     */
    public int cleanJylosFields(List<String> ids, Path backupDir) {
        FileSystemIoLock.LOCK.lock();
        try {
            int cleaned = 0;
            for (String id : ids) {
                String normalizedId = FileSystemNames.normalizeId(id);
                requireInsideVault(normalizedId);
                Path path = rootPath.resolve(normalizedId.replace("/", File.separator));
                if (!Files.isRegularFile(path)) {
                    continue;
                }
                String file = new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
                if (!FrontmatterHandler.hasJylosFields(file)) {
                    continue;
                }
                Path copy = backupDir.resolve(normalizedId.replace("/", File.separator));
                Files.createDirectories(copy.getParent());
                Files.copy(path, copy, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);

                Note old = FrontmatterHandler.parse(file);
                old.setId(normalizedId);
                if (!metadataStore.hasNoteMarks(normalizedId)) {
                    metadataStore.persistNoteMarks(old, false);
                }
                String clean = FrontmatterHandler.withoutJylosFields(file);
                if (clean == null) {
                    clean = FrontmatterHandler.generate(old, file);
                }
                FileSystemAtomicWriter.writeString(path, clean, StandardCharsets.UTF_8);
                rememberWritten(normalizedId, path, clean);
                cleaned++;
            }
            return cleaned;
        } catch (IOException e) {
            throw new DataAccessException("Could not clean the notes of the vault", e);
        } finally {
            FileSystemIoLock.LOCK.unlock();
        }
    }

    /** Whether {@code path} is under a hidden folder of the vault other than the trash. */
    private boolean inHiddenFolder(Path path) {
        Path relative = rootPath.relativize(path).getParent();
        if (relative == null) {
            return false;
        }
        for (Path part : relative) {
            String name = part.toString();
            if (name.startsWith(".") && !name.equals(FileSystemNames.TRASH_DIR)) {
                return true;
            }
        }
        return false;
    }

    /** Whether Jylos keeps this file's marks in {@code .jylos/}: attachments and notes alike. */
    private boolean usesSidecarMetadata(Path path) {
        if (path == null) {
            return false;
        }
        String name = path.getFileName().toString();
        return AttachmentType.isAttachment(name) || name.toLowerCase(Locale.ROOT).endsWith(".md");
    }

    private String expectedAttachmentFilename(String currentFilename, String requestedTitle) {
        String sanitized = sanitizeFilename(requestedTitle);
        String currentExtension = AttachmentType.extensionOf(currentFilename);
        String extension = currentExtension;
        String base = sanitized;

        if (!currentExtension.isEmpty()) {
            int dot = sanitized.lastIndexOf('.');
            base = dot > 0 ? sanitized.substring(0, dot) : sanitized;
        }

        return extension.isEmpty() ? base : base + "." + extension;
    }


    /** Whether {@code id} resolves to a path inside the vault root (see {@link PathContainment}). */
    private boolean isInsideVault(String id) {
        return PathContainment.resolveInside(rootPath, FileSystemNames.normalizeId(id)).isPresent();
    }

    /** Rejects ids that would resolve outside the vault before any mutation touches the disk. */
    private void requireInsideVault(String id) {
        if (!isInsideVault(id)) {
            throw new InvalidParameterException("Note id resolves outside the vault: " + id);
        }
    }


    /**
     * Removes stale alias entries that can appear after filesystem moves/renames
     * performed outside the NoteDAO cache ownership path.
     */
    private void removeCacheAliasesForIds(String previousId, String currentId) {
        String normalizedPreviousId = FileSystemNames.normalizeId(previousId);
        String normalizedCurrentId = FileSystemNames.normalizeId(currentId);
        if (normalizedCurrentId.equals(normalizedPreviousId) && cachedNotes.containsKey(normalizedCurrentId)) {
            return;
        }

        List<String> aliasKeys = new ArrayList<>();
        for (Map.Entry<String, Note> entry : cachedNotes.entrySet()) {
            String entryKey = FileSystemNames.normalizeId(entry.getKey());
            Note cached = entry.getValue();
            String cachedId = cached != null ? FileSystemNames.normalizeId(cached.getId()) : "";

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
            cachedNotes.remove(FileSystemNames.normalizeId(aliasKey));
            idToPathMap.remove(aliasKey);
            idToPathMap.remove(FileSystemNames.normalizeId(aliasKey));
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

                String noteId = FileSystemNames.normalizeId(note.getId());
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
                String noteId = FileSystemNames.normalizeId(note.getId());
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
        return FileSystemNames.normalizeId(folderId);
    }

    /** Extracts the parent directory portion of a note's relative path to use as the folder-index key. */
    private String extractFolderKeyFromNoteId(String noteId) {
        if (noteId == null || noteId.isBlank()) {
            return ROOT_ID;
        }
        String normalized = FileSystemNames.normalizeId(noteId);
        int idx = normalized.lastIndexOf('/');
        if (idx <= 0) {
            return ROOT_ID;
        }
        return normalized.substring(0, idx);
    }
}
