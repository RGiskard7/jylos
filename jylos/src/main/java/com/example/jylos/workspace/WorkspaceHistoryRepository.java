package com.example.jylos.workspace;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.example.jylos.AppDataDirectory;
import com.example.jylos.config.LoggerConfig;

/**
 * The back and forward of each tab of each workspace, and whether it is pinned, in Jylos' local
 * app data, one line per workspace: its id, then each tab's in the order of its
 * {@link Workspace#openNoteIds()}. A tab is its back, its forward and, when pinned, a third
 * part: versions that do not know it read the first two and leave it.
 *
 * <p>Kept apart from {@code workspaces.dat} so that file keeps the shape older versions read:
 * a workspace with one more field would be dropped by them as corrupt. A line that does not
 * parse is skipped.</p>
 *
 * @author Edu Díaz (RGiskard7)
 * @since 2.6.0
 */
public final class WorkspaceHistoryRepository {

    private static final Logger logger = LoggerConfig.getLogger(WorkspaceHistoryRepository.class);
    private static final String FILE_NAME = "workspace-history.dat";

    /** Between the workspace id and its tabs. */
    private static final String FIELD = "\u001F";
    /** Between one tab and the next. */
    private static final String TAB = "\u001D";
    /** Between a tab's back and its forward. */
    private static final String SIDE = "\u001C";
    /** Between the entries of a back or a forward. */
    private static final String ENTRY = "\u001E";
    /** The third part of a pinned tab. */
    private static final String PINNED = "pinned";

    private final Path file;
    private final Object fileLock = new Object();

    /** Default location: {@code <appData>/data/workspace-history.dat}. */
    public WorkspaceHistoryRepository() {
        this(Path.of(AppDataDirectory.getDataDirectory(), FILE_NAME));
    }

    public WorkspaceHistoryRepository(Path file) {
        this.file = file;
    }

    /** The history of each tab of workspace {@code id}, or an empty list when it has none. */
    public List<TabHistory> load(String id) {
        List<TabHistory> history = id != null ? readAll().get(id) : null;
        return history != null ? history : List.of();
    }

    /** Keeps {@code history} as workspace {@code id}'s, replacing what it had. */
    public void save(String id, List<TabHistory> history) {
        if (id == null) {
            return;
        }
        synchronized (fileLock) {
            Map<String, List<TabHistory>> all = readAll();
            all.put(id, history != null ? List.copyOf(history) : List.of());
            writeAll(all);
        }
    }

    public void delete(String id) {
        synchronized (fileLock) {
            Map<String, List<TabHistory>> all = readAll();
            if (all.remove(id) != null) {
                writeAll(all);
            }
        }
    }

    private Map<String, List<TabHistory>> readAll() {
        synchronized (fileLock) {
            Map<String, List<TabHistory>> all = new LinkedHashMap<>();
            if (file == null || !Files.exists(file)) {
                return all;
            }
            try {
                for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                    int field = line.indexOf(FIELD);
                    if (field <= 0) {
                        if (!line.isBlank()) {
                            logger.warning("Skipping corrupt workspace history entry");
                        }
                        continue;
                    }
                    all.put(line.substring(0, field), parseTabs(line.substring(field + 1)));
                }
            } catch (IOException e) {
                logger.log(Level.WARNING, "Could not read workspace history file", e);
            }
            return all;
        }
    }

    private void writeAll(Map<String, List<TabHistory>> all) {
        if (file == null) {
            return;
        }
        StringBuilder sb = new StringBuilder();
        all.forEach((id, tabs) -> sb.append(id).append(FIELD).append(serializeTabs(tabs)).append('\n'));
        try {
            if (file.getParent() != null) {
                Files.createDirectories(file.getParent());
            }
            Files.writeString(file, sb.toString(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            logger.log(Level.WARNING, "Could not write workspace history file", e);
        }
    }

    private static String serializeTabs(List<TabHistory> tabs) {
        List<String> parts = new ArrayList<>();
        for (TabHistory tab : tabs) {
            parts.add(String.join(ENTRY, tab.back()) + SIDE + String.join(ENTRY, tab.forward())
                    + (tab.pinned() ? SIDE + PINNED : ""));
        }
        return String.join(TAB, parts);
    }

    private static List<TabHistory> parseTabs(String text) {
        List<TabHistory> tabs = new ArrayList<>();
        if (text.isEmpty()) {
            return tabs; // no tabs: a single tab always has its SIDE separator
        }
        for (String tab : text.split(TAB, -1)) {
            String[] sides = tab.split(SIDE, -1);
            tabs.add(new TabHistory(entries(sides[0]), sides.length > 1 ? entries(sides[1]) : List.of(),
                    sides.length > 2 && PINNED.equals(sides[2])));
        }
        return tabs;
    }

    private static List<String> entries(String text) {
        List<String> entries = new ArrayList<>();
        for (String entry : text.split(ENTRY, -1)) {
            if (!entry.isEmpty()) {
                entries.add(entry);
            }
        }
        return entries;
    }
}
