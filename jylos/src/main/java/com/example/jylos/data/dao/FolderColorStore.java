package com.example.jylos.data.dao;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.logging.Logger;
import java.util.regex.Pattern;

import com.example.jylos.config.LoggerConfig;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import com.google.gson.reflect.TypeToken;

/**
 * The colour chosen for each folder, by folder id, kept in a small JSON file: in a folder
 * vault, {@code .jylos/folder-colors.json} inside it, so the colours travel with the notes;
 * with the database, a file in the app's data folder.
 *
 * <p>A folder's id is its path in a folder vault, so moving or renaming one changes its id and
 * those of the folders inside it: {@link #move} carries their colours along. A file that cannot
 * be read is ignored (and replaced on the next change) rather than stopping the app.</p>
 *
 * @author Edu Díaz (RGiskard7)
 * @since 2.6.0
 */
public final class FolderColorStore {

    private static final Logger logger = LoggerConfig.getLogger(FolderColorStore.class);
    private static final Pattern HEX = Pattern.compile("#[0-9a-fA-F]{6}");
    private static final Type MAP_TYPE = new TypeToken<Map<String, String>>() { }.getType();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final Path file;
    private final Map<String, String> colors = new TreeMap<>();

    public FolderColorStore(Path file) {
        this.file = file;
        load();
    }

    /** The colour of the folder {@code folderId} ({@code #rrggbb}), or {@code null} if it has none. */
    public synchronized String colorOf(String folderId) {
        return folderId != null ? colors.get(folderId) : null;
    }

    /** Every folder's colour, by folder id. */
    public synchronized Map<String, String> all() {
        return Map.copyOf(colors);
    }

    /**
     * Gives the folder {@code folderId} the colour {@code hex} ({@code #rrggbb}), or takes its
     * colour away when {@code hex} is {@code null}.
     *
     * @throws IllegalArgumentException if {@code hex} is not a six-digit hex colour
     */
    public synchronized void setColor(String folderId, String hex) {
        if (folderId == null) {
            return;
        }
        if (hex == null) {
            if (colors.remove(folderId) != null) {
                save();
            }
            return;
        }
        if (!HEX.matcher(hex).matches()) {
            throw new IllegalArgumentException("Not a #rrggbb colour: " + hex);
        }
        colors.put(folderId, hex.toLowerCase(Locale.ROOT));
        save();
    }

    /** The folder {@code oldId} is now {@code newId}: its colour, and its subfolders', follow it. */
    public synchronized void move(String oldId, String newId) {
        if (oldId == null || newId == null || oldId.equals(newId)) {
            return;
        }
        Map<String, String> moved = new TreeMap<>();
        colors.entrySet().removeIf(entry -> {
            String id = entry.getKey();
            if (id.equals(oldId)) {
                moved.put(newId, entry.getValue());
                return true;
            }
            if (id.startsWith(oldId + "/")) {
                moved.put(newId + id.substring(oldId.length()), entry.getValue());
                return true;
            }
            return false;
        });
        if (!moved.isEmpty()) {
            colors.putAll(moved);
            save();
        }
    }

    /** The folder {@code folderId} is gone: its colour, and its subfolders', are forgotten. */
    public synchronized void remove(String folderId) {
        if (folderId != null && colors.keySet().removeIf(id -> id.equals(folderId) || id.startsWith(folderId + "/"))) {
            save();
        }
    }

    private void load() {
        if (file == null || !Files.isRegularFile(file)) {
            return;
        }
        try {
            Map<String, String> stored = GSON.fromJson(Files.readString(file, StandardCharsets.UTF_8), MAP_TYPE);
            if (stored != null) {
                stored.forEach((id, hex) -> {
                    if (id != null && hex != null && HEX.matcher(hex).matches()) {
                        colors.put(id, hex.toLowerCase(Locale.ROOT));
                    }
                });
            }
        } catch (IOException | JsonParseException e) {
            logger.warning("Ignoring unreadable folder colours in " + file + ": " + e.getMessage());
        }
    }

    private void save() {
        if (file == null) {
            return;
        }
        try {
            Path parent = file.toAbsolutePath().getParent();
            Files.createDirectories(parent);
            Path temp = Files.createTempFile(parent, file.getFileName().toString(), ".tmp");
            try {
                Files.writeString(temp, GSON.toJson(colors, MAP_TYPE), StandardCharsets.UTF_8);
                try {
                    Files.move(temp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
                } catch (AtomicMoveNotSupportedException e) {
                    Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
                }
            } finally {
                Files.deleteIfExists(temp);
            }
        } catch (IOException e) {
            logger.warning("Could not save folder colours to " + file + ": " + e.getMessage());
        }
    }
}
