package com.example.jylos.workspace;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.example.jylos.AppDataDirectory;
import com.example.jylos.config.LoggerConfig;

/**
 * File-backed persistence for {@link Workspace}s, stored in Jylos' local app-data
 * directory (never inside the notes), one serialized workspace per line.
 *
 * <p>Reading is tolerant: blank or malformed lines are skipped (a corrupt entry is
 * ignored, not fatal), so one bad workspace can't break the rest.</p>
 *
 * @author Edu Díaz (RGiskard7)
 * @since 2.1.0
 */
public final class WorkspaceRepository {

    private static final Logger logger = LoggerConfig.getLogger(WorkspaceRepository.class);
    private static final String FILE_NAME = "workspaces.dat";

    private final Path file;

    /** Default location: {@code <appData>/data/workspaces.dat}. */
    public WorkspaceRepository() {
        this(Path.of(AppDataDirectory.getDataDirectory(), FILE_NAME));
    }

    /** Explicit location (used by tests). */
    public WorkspaceRepository(Path file) {
        this.file = file;
    }

    /** Loads all stored workspaces, skipping any corrupt lines. Never throws. */
    public List<Workspace> loadAll() {
        List<Workspace> result = new ArrayList<>();
        if (file == null || !Files.exists(file)) {
            return result;
        }
        try {
            for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                Workspace ws = Workspace.parse(line);
                if (ws != null) {
                    result.add(ws);
                } else if (!line.isBlank()) {
                    logger.warning("Skipping corrupt workspace entry");
                }
            }
        } catch (IOException e) {
            logger.log(Level.WARNING, "Could not read workspaces file", e);
        }
        return result;
    }

    /** Persists the full list, replacing the file contents. Never throws. */
    public void saveAll(List<Workspace> workspaces) {
        if (file == null) {
            return;
        }
        StringBuilder sb = new StringBuilder();
        for (Workspace ws : workspaces) {
            sb.append(ws.serialize()).append('\n');
        }
        try {
            if (file.getParent() != null) {
                Files.createDirectories(file.getParent());
            }
            Files.writeString(file, sb.toString(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            logger.log(Level.WARNING, "Could not write workspaces file", e);
        }
    }
}
