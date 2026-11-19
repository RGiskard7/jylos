package com.example.jylos.workspace;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import com.example.jylos.AppDataDirectory;

/**
 * The last session: the tabs open when the app closed, with their history, and the workspace
 * that was in use ({@link Workspace#id()} and {@link Workspace#name()}, blank for none), so the
 * next start reopens them. It also keeps the tabs of "no workspace", the context the app is in
 * before any workspace is opened, to come back to it. Stored in Jylos' local app data, in the
 * same shape as a workspace.
 *
 * @author Edu Díaz (RGiskard7)
 * @since 2.6.0
 */
public final class SessionStore {

    private static final String HISTORY_KEY = "session";
    /** The id of the line that keeps the tabs of "no workspace" (workspace ids are UUIDs). */
    private static final String DEFAULT_ID = "jylos:no-workspace";

    private final WorkspaceRepository state;
    private final WorkspaceHistoryRepository history;

    /** Default location: {@code <appData>/data/}. */
    public SessionStore() {
        this(Path.of(AppDataDirectory.getDataDirectory()));
    }

    public SessionStore(Path dir) {
        this.state = new WorkspaceRepository(dir.resolve("session.dat"));
        this.history = new WorkspaceHistoryRepository(dir.resolve("session-history.dat"));
    }

    /** Keeps {@code session} (the tabs, and the workspace in use as its id and name) and its tabs' history. */
    public void save(Workspace session, List<TabHistory> tabHistory) {
        keep(session, false);
        history.save(HISTORY_KEY, tabHistory);
    }

    /** The last session, or empty when there is none yet. */
    public Optional<Workspace> load() {
        return state.loadAll().stream().filter(line -> !DEFAULT_ID.equals(line.id())).findFirst();
    }

    /**
     * Keeps the tabs of "no workspace" (the open tabs, layout and history when a workspace is
     * opened from it), to bring them back when it is picked again.
     */
    public void saveDefault(Workspace tabs, List<TabHistory> tabHistory) {
        keep(new Workspace(DEFAULT_ID, "", null, null, tabs.openNoteIds(), tabs.activeNoteId(), tabs.viewMode(),
                tabs.sidebarVisible(), tabs.focusMode(), tabs.splitMain(), tabs.splitContent(), tabs.storageMode()),
                true);
        history.save(DEFAULT_ID, tabHistory);
    }

    /** The tabs of "no workspace", or empty when it has never been left. */
    public Optional<Workspace> loadDefault() {
        return state.loadAll().stream().filter(line -> DEFAULT_ID.equals(line.id())).findFirst();
    }

    /** The history of each tab of "no workspace", in the order of its tabs. */
    public List<TabHistory> defaultHistory() {
        return history.load(DEFAULT_ID);
    }

    /** Replaces the session line or the "no workspace" line, keeping the other one. */
    private void keep(Workspace line, boolean isDefault) {
        state.update(all -> {
            all.removeIf(other -> DEFAULT_ID.equals(other.id()) == isDefault);
            all.add(line);
            return all;
        });
    }

    /** The history of each tab of the last session, in the order of its tabs. */
    public List<TabHistory> history() {
        return history.load(HISTORY_KEY);
    }
}
