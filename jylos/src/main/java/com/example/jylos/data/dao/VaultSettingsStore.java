package com.example.jylos.data.dao;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.logging.Logger;

import com.example.jylos.config.LoggerConfig;
import com.example.jylos.data.models.VaultSettings;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;

/**
 * A vault's settings ({@link VaultSettings}), kept in a small JSON file: in a folder vault,
 * {@code .jylos/settings.json} inside it, so they travel with the notes; with the database, a
 * file in the app's data folder. Reading writes nothing; a file that cannot be read is ignored
 * (and replaced on the next change) rather than stopping the app.
 *
 * @author Edu Díaz (RGiskard7)
 * @since 2.6.0
 */
public final class VaultSettingsStore {

    private static final Logger logger = LoggerConfig.getLogger(VaultSettingsStore.class);
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final Path file;
    private VaultSettings settings;

    public VaultSettingsStore(Path file) {
        this.file = file;
        this.settings = load();
    }

    /** The settings as they are now. */
    public synchronized VaultSettings get() {
        return settings;
    }

    /** Keeps {@code value} as the settings. */
    public synchronized void set(VaultSettings value) {
        settings = value != null ? value : VaultSettings.defaults();
        save();
    }

    /** The folder {@code from} is now {@code to}: the settings naming it, or what is inside it, follow. */
    public synchronized void movePath(String from, String to) {
        VaultSettings moved = settings.movePath(from, to);
        if (!moved.equals(settings)) {
            settings = moved;
            save();
        }
    }

    private VaultSettings load() {
        if (file == null || !Files.isRegularFile(file)) {
            return VaultSettings.defaults();
        }
        try {
            VaultSettings stored = GSON.fromJson(Files.readString(file, StandardCharsets.UTF_8), VaultSettings.class);
            return stored != null ? stored : VaultSettings.defaults();
        } catch (IOException | JsonParseException e) {
            logger.warning("Ignoring unreadable vault settings in " + file + ": " + e.getMessage());
            return VaultSettings.defaults();
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
                Files.writeString(temp, GSON.toJson(settings), StandardCharsets.UTF_8);
                try {
                    Files.move(temp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
                } catch (AtomicMoveNotSupportedException e) {
                    Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
                }
            } finally {
                Files.deleteIfExists(temp);
            }
        } catch (IOException e) {
            logger.warning("Could not save vault settings to " + file + ": " + e.getMessage());
        }
    }
}
