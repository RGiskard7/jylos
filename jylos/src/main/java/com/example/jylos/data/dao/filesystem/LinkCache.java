package com.example.jylos.data.dao.filesystem;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.example.jylos.config.LoggerConfig;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

/**
 * The links of the notes too long to be read whole when the list is built, kept on disk
 * between sessions so a long note is read in full once, and again only when its file
 * changes. An entry holds the links as written (see {@code WikiLinkResolver.extractRawLinks})
 * and is valid only for the modified time and size it was computed from.
 *
 * <p>Nothing here is the source of truth: the file is a cache that can be deleted at any
 * time, and a missing or unreadable one is simply an empty cache.</p>
 */
public final class LinkCache {

    private static final Logger logger = LoggerConfig.getLogger(LinkCache.class);
    private static final Gson GSON = new Gson();

    /**
     * @param format the rules the links were found with; entries written with others are not
     *               used (2: links in properties count)
     */
    private record Entry(long modified, long size, List<String> links, int format) {
    }

    /** Bumped whenever which links are found changes, so old entries are read again. */
    private static final int FORMAT = 3;

    private final Path file;
    private final Map<String, Entry> entries = new ConcurrentHashMap<>();
    private volatile boolean dirty;

    private LinkCache(Path file) {
        this.file = file;
    }

    /** A cache that is kept in memory only: {@link #save()} writes nothing. */
    public static LinkCache inMemory() {
        return new LinkCache(null);
    }

    /** Loads the cache stored in {@code file}; a missing or corrupt file gives an empty cache. */
    public static LinkCache load(Path file) {
        LinkCache cache = new LinkCache(file);
        if (file == null || !Files.isRegularFile(file)) {
            return cache;
        }
        try {
            Map<String, Entry> stored = GSON.fromJson(Files.readString(file, StandardCharsets.UTF_8),
                    new TypeToken<Map<String, Entry>>() { }.getType());
            if (stored != null) {
                stored.forEach((id, entry) -> {
                    if (id != null && entry != null && entry.links() != null) {
                        cache.entries.put(id, entry);
                    }
                });
            }
        } catch (IOException | RuntimeException e) {
            logger.log(Level.FINE, "Ignoring unreadable link cache " + file, e);
            cache.entries.clear();
        }
        return cache;
    }

    /** The links stored for {@code id}, if they were computed from this exact modified time and size. */
    public Optional<List<String>> get(String id, long modified, long size) {
        Entry entry = entries.get(id);
        if (entry == null || entry.format() != FORMAT || entry.modified() != modified || entry.size() != size) {
            return Optional.empty();
        }
        return Optional.of(List.copyOf(entry.links()));
    }

    public void put(String id, long modified, long size, List<String> links) {
        entries.put(id, new Entry(modified, size, List.copyOf(links), FORMAT));
        dirty = true;
    }

    /** Forgets every entry whose id is not in {@code ids} (notes deleted, renamed or no longer long). */
    public void retainOnly(Set<String> ids) {
        if (entries.keySet().retainAll(ids)) {
            dirty = true;
        }
    }

    /**
     * Writes the cache if it changed since it was loaded or last saved.
     *
     * @return true when the file was written
     */
    public boolean save() {
        if (file == null || !dirty) {
            return false;
        }
        try {
            Path parent = file.toAbsolutePath().getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Path temp = file.resolveSibling(file.getFileName() + ".tmp");
            Files.writeString(temp, GSON.toJson(new java.util.TreeMap<>(entries)), StandardCharsets.UTF_8);
            try {
                Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (java.nio.file.AtomicMoveNotSupportedException e) {
                Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
            }
            dirty = false;
            return true;
        } catch (IOException | RuntimeException e) {
            logger.log(Level.FINE, "Could not save link cache " + file, e);
            return false;
        }
    }

    /** Ids currently stored (diagnostics). */
    List<String> ids() {
        return new ArrayList<>(entries.keySet());
    }
}
