package com.example.jylos.data.dao.filesystem;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.example.jylos.config.LoggerConfig;
import com.example.jylos.util.WikiLinkResolver;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

/**
 * What the vault needs of each note without opening it, kept between sessions as Obsidian
 * keeps its metadata cache: the properties and the start of the text the list shows, the
 * {@code #tags} and the links of the whole file. An entry is valid only for the modified time
 * and size of the file it was read from, so a note is read again only when its file changes,
 * and opening a vault reads no file that did not (with iCloud, reading an offloaded note
 * downloads it).
 *
 * <p>An entry holds only what the file on disk holds: the text of a private note is
 * encrypted there, so nothing it says is kept. Nothing here is the source of truth: the file
 * is a cache that can be deleted at any time, and a missing or unreadable one is an empty
 * cache.</p>
 *
 * @author Edu Díaz (RGiskard7)
 * @since 2.6.1
 */
public final class NoteMetadataCache {

    private static final Logger logger = LoggerConfig.getLogger(NoteMetadataCache.class);
    private static final Gson GSON = new Gson();

    /** Body characters kept for the notes list preview. */
    static final int PREVIEW_CHARS = 900;

    /** Bumped whenever what an entry holds, or how it is found, changes: older entries are read again. */
    private static final int FORMAT = 1;

    /**
     * What the vault knows of a note without opening it.
     *
     * @param yaml       its properties as written, without the {@code ---} lines; null when it has none
     * @param preview    the first {@value #PREVIEW_CHARS} characters of its text
     * @param inlineTags the {@code #tags} of its whole text
     * @param links      the note links of the whole file, properties included
     *                   ({@link FrontmatterHandler#linksOf})
     * @param fileLinks  the links of its text to files that are not notes
     *                   ({@link WikiLinkResolver#extractFileLinks})
     */
    public record Metadata(String yaml, String preview, List<String> inlineTags, List<String> links,
            List<String> fileLinks) {

        /** Everything the vault keeps of a file, from its whole text. */
        public static Metadata of(String fileText) {
            FrontmatterHandler.Lightweight parts = FrontmatterHandler.lightweight(fileText, PREVIEW_CHARS);
            return new Metadata(parts.yaml(), parts.body(), parts.inlineTags(),
                    FrontmatterHandler.linksOf(fileText),
                    List.copyOf(WikiLinkResolver.extractFileLinks(FrontmatterHandler.bodyOf(fileText))));
        }

        /** What {@link FrontmatterHandler#parseLightweight(FrontmatterHandler.Lightweight)} builds the listed note from. */
        public FrontmatterHandler.Lightweight lightweight() {
            return new FrontmatterHandler.Lightweight(yaml, preview, inlineTags);
        }
    }

    /**
     * @param derived values plugins worked out from the note's text, by key (see
     *                {@link #derived}); valid as long as the entry is
     */
    private record Entry(long modified, long size, int format, String yaml, String preview,
            List<String> inlineTags, List<String> links, List<String> fileLinks, Map<String, String> derived) {

        boolean matches(long modified, long size) {
            return format == FORMAT && this.modified == modified && this.size == size
                    && preview != null && inlineTags != null && links != null && fileLinks != null;
        }
    }

    private final Path file;
    private final Map<String, Entry> entries = new ConcurrentHashMap<>();
    private volatile boolean dirty;

    private NoteMetadataCache(Path file) {
        this.file = file;
    }

    /** A cache that is kept in memory only: {@link #save()} writes nothing. */
    public static NoteMetadataCache inMemory() {
        return new NoteMetadataCache(null);
    }

    /** Loads the cache stored in {@code file}; a missing or corrupt file gives an empty cache. */
    public static NoteMetadataCache load(Path file) {
        NoteMetadataCache cache = new NoteMetadataCache(file);
        if (file == null || !Files.isRegularFile(file)) {
            return cache;
        }
        try {
            Map<String, Entry> stored = GSON.fromJson(Files.readString(file, StandardCharsets.UTF_8),
                    new TypeToken<Map<String, Entry>>() { }.getType());
            if (stored != null) {
                stored.forEach((id, entry) -> {
                    if (id != null && entry != null) {
                        cache.entries.put(id, entry);
                    }
                });
            }
        } catch (IOException | RuntimeException e) {
            logger.log(Level.FINE, "Ignoring unreadable metadata cache " + file, e);
            cache.entries.clear();
        }
        return cache;
    }

    /** What is stored for {@code id}, if it was read from a file of this exact modified time and size. */
    public Optional<Metadata> get(String id, long modified, long size) {
        Entry entry = entries.get(id);
        if (entry == null || !entry.matches(modified, size)) {
            return Optional.empty();
        }
        return Optional.of(new Metadata(entry.yaml(), entry.preview(), List.copyOf(entry.inlineTags()),
                List.copyOf(entry.links()), List.copyOf(entry.fileLinks())));
    }

    /** Stores what was read from the file of {@code id}, of this modified time and size. */
    public void put(String id, long modified, long size, Metadata metadata) {
        entries.put(id, new Entry(modified, size, FORMAT, metadata.yaml(), metadata.preview(),
                List.copyOf(metadata.inlineTags()), List.copyOf(metadata.links()),
                List.copyOf(metadata.fileLinks()), Map.of()));
        dirty = true;
    }

    /**
     * A value a plugin worked out from the text of {@code id} under {@code key}, if it was
     * worked out from the file of this exact modified time and size.
     */
    public Optional<String> derived(String id, long modified, long size, String key) {
        Entry entry = entries.get(id);
        if (entry == null || !entry.matches(modified, size) || entry.derived() == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(entry.derived().get(key));
    }

    /**
     * Keeps {@code value} under {@code key} for {@code id}, when the entry stored for it is of
     * this modified time and size (otherwise it describes another version of the file).
     *
     * @return true when it was kept
     */
    public boolean putDerived(String id, long modified, long size, String key, String value) {
        boolean[] kept = { false };
        entries.computeIfPresent(id, (k, entry) -> {
            if (!entry.matches(modified, size)) {
                return entry;
            }
            Map<String, String> derived = new LinkedHashMap<>(entry.derived() != null ? entry.derived() : Map.of());
            derived.put(key, value);
            kept[0] = true;
            return new Entry(entry.modified(), entry.size(), entry.format(), entry.yaml(), entry.preview(),
                    entry.inlineTags(), entry.links(), entry.fileLinks(), Map.copyOf(derived));
        });
        if (kept[0]) {
            dirty = true;
        }
        return kept[0];
    }

    /** Forgets every entry whose id is not in {@code ids} (notes deleted or renamed). */
    public void retainOnly(Set<String> ids) {
        if (entries.keySet().retainAll(ids)) {
            dirty = true;
        }
    }

    /** Whether something changed since the cache was loaded or last saved. */
    public boolean isDirty() {
        return dirty;
    }

    /**
     * Writes the cache if it changed since it was loaded or last saved.
     *
     * @return true when the file was written
     */
    public synchronized boolean save() {
        if (file == null || !dirty) {
            return false;
        }
        try {
            Path parent = file.toAbsolutePath().getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            dirty = false;
            Path temp = file.resolveSibling(file.getFileName() + ".tmp");
            Files.writeString(temp, GSON.toJson(new TreeMap<>(entries)), StandardCharsets.UTF_8);
            try {
                Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
            }
            return true;
        } catch (IOException | RuntimeException e) {
            dirty = true;
            logger.log(Level.FINE, "Could not save metadata cache " + file, e);
            return false;
        }
    }

    /** Ids currently stored (diagnostics). */
    List<String> ids() {
        return new ArrayList<>(entries.keySet());
    }
}
