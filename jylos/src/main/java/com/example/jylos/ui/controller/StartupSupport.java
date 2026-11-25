package com.example.jylos.ui.controller;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import com.example.jylos.data.models.Note;
import com.example.jylos.data.models.VaultSettings;
import com.example.jylos.data.models.VaultSettings.Startup;
import com.example.jylos.data.models.VaultSettings.StartupTabs;
import com.example.jylos.event.events.SystemActionEvent.ActionType;
import com.example.jylos.workspace.TabHistory;

/**
 * What opens with the vault, and its home page (as Obsidian's Homepage plugin, built in).
 * Which tabs of the last session open again (all of them, or only the pinned ones) and what is
 * in front once they have (the tab that was active, a new tab, the home page, or today's daily
 * note) are the vault's settings ({@link VaultSettings#startup()}). "Go home" opens the home
 * page, a note or a canvas, at any time.
 *
 * @author Edu Díaz (RGiskard7)
 * @since 2.7.0
 */
final class StartupSupport {

    /**
     * What the app offers.
     *
     * @param settings         the vault's settings
     * @param noteAt           the note at a path of the vault
     * @param openNote         opens a note in the active tab (or its own tab, if open)
     * @param openNoteInNewTab opens a note in a new tab
     * @param newTab           opens a new, empty tab
     * @param openToday        opens today's daily note (creating it, as the command does)
     * @param status           shows a message in the status bar
     * @param i18n             the interface's texts
     * @param homeAvailable    shows the house (and its menu item) when there is a home page,
     *                         hides it otherwise, as before there was one
     */
    record Host(Supplier<VaultSettings> settings, Function<String, Optional<Note>> noteAt, Consumer<Note> openNote,
            Consumer<Note> openNoteInNewTab, Runnable newTab, Runnable openToday, Consumer<String> status,
            Function<String, String> i18n, Consumer<Boolean> homeAvailable) {
    }

    private Host host;

    /** Wires the vault in use (at startup, and when another vault opens) and shows its house or not. */
    void wire(Host host) {
        this.host = host;
        refreshHome();
    }

    /** Shows the house when the vault has a home page, hides it otherwise (after Preferences, say). */
    void refreshHome() {
        if (host != null) {
            host.homeAvailable().accept(!startup().home().isBlank());
        }
    }

    void register(Map<ActionType, Runnable> handlers) {
        handlers.put(ActionType.GO_HOME, () -> goHome(false));
        handlers.put(ActionType.GO_HOME_NEW_TAB, () -> goHome(true));
    }

    /**
     * At startup: reopens the last session's tabs the vault's settings keep, then brings what
     * they say to the front.
     */
    void start(WorkspaceController workspaces) {
        StartupTabs which = startup().tabs();
        workspaces.restoreSession(tabs -> keep(tabs, which));
        front();
    }

    /** The tabs of the last session that open again: all of them, or the pinned ones only. */
    static WorkspaceController.SessionTabs keep(WorkspaceController.SessionTabs saved, StartupTabs which) {
        if (which != StartupTabs.PINNED) {
            return saved;
        }
        List<String> ids = new ArrayList<>();
        List<TabHistory> histories = new ArrayList<>();
        for (int i = 0; i < saved.ids().size(); i++) {
            if (i < saved.histories().size() && saved.histories().get(i).pinned()) {
                ids.add(saved.ids().get(i));
                histories.add(saved.histories().get(i));
            }
        }
        String active = ids.contains(saved.activeId()) ? saved.activeId() : null;
        return new WorkspaceController.SessionTabs(List.copyOf(ids), active, List.copyOf(histories));
    }

    /** Brings to the front what the vault's settings say (on startup, and when another vault opens). */
    void front() {
        if (host == null) {
            return;
        }
        switch (startup().front()) {
            case NEW_TAB -> host.newTab().run();
            case HOME -> goHome(false);
            case TODAY -> host.openToday().run();
            case LAST_ACTIVE -> {
                // The tab that was active is already in front.
            }
        }
    }

    /** Opens the home page, here or in a new tab; tells when there is none or it is gone. */
    void goHome(boolean newTab) {
        if (host == null) {
            return;
        }
        String home = startup().home();
        if (home.isBlank()) {
            host.status().accept(host.i18n().apply("status.home_not_set"));
            return;
        }
        Optional<Note> note = host.noteAt().apply(home);
        if (note.isEmpty()) {
            host.status().accept(host.i18n().apply("status.home_missing"));
            return;
        }
        (newTab ? host.openNoteInNewTab() : host.openNote()).accept(note.get());
    }

    private Startup startup() {
        VaultSettings settings = host != null ? host.settings().get() : null;
        return (settings != null ? settings : VaultSettings.defaults()).startup();
    }
}
