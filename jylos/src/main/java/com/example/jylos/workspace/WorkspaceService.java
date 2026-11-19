package com.example.jylos.workspace;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/**
 * Business logic for workspaces: list, save (create or update), and delete, on top of a
 * {@link WorkspaceRepository}. Names are unique case-insensitively — saving with an
 * existing name overwrites that workspace (keeping its id and creation time).
 *
 * <p>Holds no UI or app state; the caller supplies a "live state" {@link Workspace}
 * (state fields filled, id/name/timestamps blank) and this assigns identity and
 * timestamps.</p>
 *
 * @author Edu Díaz (RGiskard7)
 * @since 2.1.0
 */
public final class WorkspaceService {

    private final WorkspaceRepository repository;

    private final WorkspaceHistoryRepository tabHistory;

    public WorkspaceService() {
        this(new WorkspaceRepository(), new WorkspaceHistoryRepository());
    }

    /** Without a place for the tabs' history (it is then neither kept nor read). */
    public WorkspaceService(WorkspaceRepository repository) {
        this(repository, null);
    }

    public WorkspaceService(WorkspaceRepository repository, WorkspaceHistoryRepository tabHistory) {
        this.repository = repository;
        this.tabHistory = tabHistory;
    }

    /**
     * As {@link #save(String, Workspace)}, keeping also the back and forward of each tab, in
     * the order of the live state's tabs.
     */
    public Workspace save(String name, Workspace liveState, List<TabHistory> history) {
        Workspace saved = save(name, liveState);
        if (tabHistory != null) {
            tabHistory.save(saved.id(), history);
        }
        return saved;
    }

    /** The back and forward of each tab of workspace {@code id}, or an empty list. */
    public List<TabHistory> history(String id) {
        return tabHistory != null ? tabHistory.load(id) : List.of();
    }

    /**
     * Gives workspace {@code id} the name {@code newName}, keeping everything else.
     *
     * @return the renamed workspace, or empty when it does not exist or another one has that name
     */
    public Optional<Workspace> rename(String id, String newName) {
        String clean = sanitize(newName);
        Workspace[] renamed = new Workspace[1];
        repository.update(all -> {
            Workspace current = all.stream().filter(w -> w.id().equals(id)).findFirst().orElse(null);
            boolean taken = all.stream().anyMatch(w -> !w.id().equals(id) && clean.equalsIgnoreCase(w.name()));
            if (current != null && !taken) {
                renamed[0] = new Workspace(current.id(), clean, current.createdAt(), now(), current.openNoteIds(),
                        current.activeNoteId(), current.viewMode(), current.sidebarVisible(), current.focusMode(),
                        current.splitMain(), current.splitContent(), current.storageMode());
                all.replaceAll(w -> w == current ? renamed[0] : w);
            }
            return all;
        });
        return Optional.ofNullable(renamed[0]);
    }

    /** All workspaces, ordered by name (case-insensitive). */
    public List<Workspace> list() {
        List<Workspace> all = repository.loadAll();
        all.sort(Comparator.comparing(w -> w.name() == null ? "" : w.name().toLowerCase(Locale.ROOT)));
        return all;
    }

    public Optional<Workspace> findById(String id) {
        if (id == null) {
            return Optional.empty();
        }
        return repository.loadAll().stream().filter(w -> id.equals(w.id())).findFirst();
    }

    public Optional<Workspace> findByName(String name) {
        if (name == null) {
            return Optional.empty();
        }
        return repository.loadAll().stream()
                .filter(w -> name.equalsIgnoreCase(w.name()))
                .findFirst();
    }

    /**
     * Saves a workspace under {@code name} with the given live state. Overwrites an
     * existing workspace of the same name (keeping its id and {@code createdAt}); creates
     * a new one otherwise.
     *
     * @return the persisted workspace
     */
    public Workspace save(String name, Workspace liveState) {
        String clean = sanitize(name);
        String now = now();
        Workspace[] savedHolder = new Workspace[1];

        repository.update(all -> {
            Workspace existing = all.stream().filter(w -> clean.equalsIgnoreCase(w.name())).findFirst().orElse(null);

            String id = existing != null ? existing.id() : UUID.randomUUID().toString();
            String createdAt = existing != null ? existing.createdAt() : now;
            Workspace saved = new Workspace(id, clean, createdAt, now,
                    liveState.openNoteIds(), liveState.activeNoteId(), liveState.viewMode(),
                    liveState.sidebarVisible(), liveState.focusMode(),
                    liveState.splitMain(), liveState.splitContent(), liveState.storageMode());
            savedHolder[0] = saved;

            all.removeIf(w -> w.id().equals(id));
            all.add(saved);
            return all;
        });

        return savedHolder[0];
    }

    /**
     * Updates an existing workspace (by id) in place with new live state, keeping its
     * name and creation time. Returns empty if the id is unknown.
     */
    public Optional<Workspace> update(String id, Workspace liveState) {
        Optional<Workspace> current = findById(id);
        if (current.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(save(current.get().name(), liveState));
    }

    public void delete(String id) {
        if (id == null) {
            return;
        }
        repository.update(all -> {
            all.removeIf(w -> id.equals(w.id()));
            return all;
        });
        if (tabHistory != null) {
            tabHistory.delete(id);
        }
    }

    /** Builds a "live state" workspace (no identity yet) to hand to {@link #save}/{@link #update}. */
    public static Workspace liveState(List<String> openNoteIds, String activeNoteId, String viewMode,
            boolean sidebarVisible, boolean focusMode, double splitMain, double splitContent,
            String storageMode) {
        return new Workspace(null, null, null, null,
                openNoteIds != null ? openNoteIds : new ArrayList<>(), activeNoteId, viewMode,
                sidebarVisible, focusMode, splitMain, splitContent, storageMode);
    }

    private static String sanitize(String name) {
        String n = name == null ? "" : name.replaceAll("[\\u0000-\\u001f]", " ").trim();
        return n.isEmpty() ? "Untitled" : n;
    }

    private static String now() {
        return DateTimeFormatter.ISO_INSTANT.format(Instant.now());
    }
}
