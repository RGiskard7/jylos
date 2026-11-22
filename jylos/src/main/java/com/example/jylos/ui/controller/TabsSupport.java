package com.example.jylos.ui.controller;

import java.text.MessageFormat;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.FormatStyle;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import com.example.jylos.data.models.Note;
import com.example.jylos.event.events.SystemActionEvent.ActionType;
import com.example.jylos.service.NoteService;
import com.example.jylos.ui.UiNodes;
import com.example.jylos.ui.components.EditorTabs;
import com.example.jylos.ui.components.EditorTabsModel;
import com.example.jylos.ui.components.EditorTabsModel.OpenTarget;
import com.example.jylos.ui.components.KanbanBoard;
import com.example.jylos.ui.components.ViewTabs;
import com.example.jylos.workspace.TabHistory;

import javafx.scene.layout.VBox;

/**
 * The editor's tabs, as in Obsidian: each one shows a note, the graph or nothing yet (a new
 * tab), and keeps its own back and forward ({@link EditorTabsModel}). Picking a tab, closing
 * one, going back or forward and reopening a workspace all come through here, and end in the
 * one place that shows what a tab holds: the note in the editor, the graph in the editor area,
 * or the editor's empty state. A Kanban board is a note, shown as its board by the editor.
 *
 * <p>The window keeps owning the editor and the notes; it is asked through {@link Host}.</p>
 *
 * @author Edu Díaz (RGiskard7)
 * @since 2.0.0
 */
final class TabsSupport {

    /**
     * What the window does for the tabs.
     *
     * @param confirmLeaveCurrent asks to save the open note's unsaved edits; {@code false} when
     *                            the user cancels
     * @param showNote            shows a note whose tab is already the active one
     * @param openNoteInNewTab    opens a note in a new tab
     * @param openNote            opens a note in the active tab
     * @param showEmptyState      the editor shows its empty state (a new tab, or no tab left)
     * @param noNoteShown         a view (the graph) is shown in place of a note: what follows the
     *                            open note (the outline, the links, the counts) has none
     * @param tabsChanged         what is shown changed: the right panel, the status bar and the
     *                            back/forward buttons follow it
     * @param chooseBoard         asks which of these Kanban boards to open, or to make a new one
     */
    record Host(BooleanSupplier confirmLeaveCurrent, Consumer<String> showNote, Consumer<Note> openNoteInNewTab,
            Consumer<Note> openNote, Runnable showEmptyState, Runnable noNoteShown, Runnable tabsChanged,
            Consumer<List<Note>> chooseBoard) {
    }

    private VBox graphView;
    private GraphController graph;
    private NoteService noteService;
    private Supplier<Boolean> darkTheme;
    private Function<String, String> i18n;
    private Consumer<String> status;
    private Supplier<EditorTabs> tabs = () -> null;
    private Host host;
    /** Whether the graph has been opened since it was last closed: then it resumes. */
    private boolean graphStarted;

    void wire(VBox graphView, GraphController graph, NoteService noteService, Supplier<Boolean> darkTheme,
            Function<String, String> i18n, Consumer<String> status, Supplier<EditorTabs> tabs, Host host) {
        this.graphView = graphView;
        this.graph = graph;
        this.noteService = noteService;
        this.darkTheme = darkTheme;
        this.i18n = i18n;
        this.status = status;
        this.tabs = tabs != null ? tabs : () -> null;
        this.host = host;
        UiNodes.show(graphView, false);
        EditorTabs strip = this.tabs.get();
        if (strip != null) {
            strip.setDescriber(this::describe);
        }
    }

    /** What the tab of {@code id} says when the pointer rests on it; null for a view's tab. */
    private String describe(String id) {
        if (noteService == null || id == null || ViewTabs.isView(id)) {
            return null;
        }
        return describe(noteService.getNoteById(id).orElse(null), i18n, Locale.getDefault(), ZoneId.systemDefault());
    }

    /**
     * A note's path, and when it was created and modified, in {@code locale} and {@code zone}
     * (a date that does not parse is shown as stored; a missing one is left out).
     */
    static String describe(Note note, Function<String, String> i18n, Locale locale, ZoneId zone) {
        if (note == null || note.getId() == null) {
            return null;
        }
        StringBuilder text = new StringBuilder(note.getId());
        appendDate(text, i18n.apply("tab.about_created"), note.getCreatedDate(), locale, zone);
        appendDate(text, i18n.apply("tab.about_modified"), note.getModifiedDate(), locale, zone);
        return text.toString();
    }

    private static void appendDate(StringBuilder text, String pattern, String stored, Locale locale, ZoneId zone) {
        if (stored == null || stored.isBlank()) {
            return;
        }
        text.append('\n').append(MessageFormat.format(pattern, readableDate(stored.strip(), locale, zone)));
    }

    /** An instant, a local date and time or a date, as the user reads them; anything else as it is. */
    private static String readableDate(String stored, Locale locale, ZoneId zone) {
        DateTimeFormatter dateTime = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
                .withLocale(locale);
        try {
            return Instant.parse(stored).atZone(zone).format(dateTime);
        } catch (DateTimeParseException notAnInstant) {
            // A local date and time, or a date alone, below.
        }
        try {
            return LocalDateTime.parse(stored.replace(' ', 'T')).format(dateTime);
        } catch (DateTimeParseException notADateTime) {
            // A date alone, below.
        }
        try {
            return LocalDate.parse(stored)
                    .format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale));
        } catch (DateTimeParseException notADate) {
            return stored;
        }
    }

    /** True while a view's tab (the graph, a new tab), not a note's, is the active one. */
    boolean isViewActive() {
        EditorTabs strip = tabs.get();
        return strip != null && ViewTabs.isView(strip.getActiveId());
    }

    /** {@code open}, the note loaded in the editor, unless a view (the graph, a new tab) is in front of it. */
    Note inSight(Note open) {
        return isViewActive() ? null : open;
    }

    /** True while the graph is on screen. */
    boolean isGraphVisible() {
        return graphView != null && graphView.isVisible();
    }

    /**
     * The menu, palette and toolbar actions the tabs answer: back, forward, the graph (in the
     * active tab or a new one), a new tab and the Kanban boards.
     */
    void register(Map<ActionType, Runnable> handlers) {
        handlers.put(ActionType.NAVIGATE_BACK, this::back);
        handlers.put(ActionType.NAVIGATE_FORWARD, this::forward);
        handlers.put(ActionType.GRAPH_VIEW, this::openGraph);
        handlers.put(ActionType.GRAPH_VIEW_NEW_TAB, () -> openGraph(OpenTarget.NEW_TAB));
        handlers.put(ActionType.NEW_TAB, this::newTab);
        handlers.put(ActionType.KANBAN_VIEW, this::openKanban);
    }

    // ── Opening ──────────────────────────────────────────────────────────────

    /** The graph command and button: opens the graph in the active tab, as a note opens. */
    void openGraph() {
        openGraph(OpenTarget.CURRENT_TAB);
    }

    /**
     * Opens the graph where {@code target} says (the active tab, or a new one after it), or
     * brings its tab to the front when it is open. Leaving a note with unsaved edits asks
     * first, as switching notes does.
     */
    void openGraph(OpenTarget target) {
        EditorTabs strip = tabs.get();
        if (strip == null || graphView == null || graph == null || ViewTabs.GRAPH.equals(strip.getActiveId())) {
            return;
        }
        if (!leaveCurrent()) {
            return;
        }
        strip.open(ViewTabs.GRAPH, getString("graph.title"), "fth-share-2", target);
        showActive();
    }

    /**
     * The + after the tabs (and Cmd/Ctrl+T): a new empty tab at the end, showing what can be
     * opened. What is opened next fills it.
     */
    void newTab() {
        EditorTabs strip = tabs.get();
        if (strip == null || !leaveCurrent()) {
            return;
        }
        strip.open(ViewTabs.newEmptyTab(), getString("tab.new_tab"), null, OpenTarget.NEW_TAB);
        showActive();
    }

    /**
     * A note clicked in the graph takes the graph's tab, as a link does; with Cmd/Ctrl (or the
     * middle button) it opens in a new tab and the graph stays.
     */
    void openNoteFromGraph(String noteId, boolean newTab) {
        if (noteId == null || noteService == null || host == null) {
            return;
        }
        noteService.getNoteById(noteId).ifPresent(newTab ? host.openNoteInNewTab() : host.openNote());
    }

    /** A note's tab came to the front from anywhere (the sidebar, a link): the views hide. */
    void noteShown() {
        EditorTabs strip = tabs.get();
        if (strip != null && !strip.isOpen(ViewTabs.GRAPH)) {
            graphStarted = false; // its tab now shows something else
        }
        hideViews();
        tabsChanged();
    }

    // ── Picking, closing, back and forward ──────────────────────────────────

    /** A tab picked in the strip: it comes to the front and shows what it holds. */
    void select(String id) {
        EditorTabs strip = tabs.get();
        if (strip == null || id == null || id.equals(strip.getActiveId()) && !ViewTabs.isView(id)) {
            return;
        }
        if (!leaveCurrent()) {
            return;
        }
        strip.open(id, null, OpenTarget.CURRENT_TAB); // already open: only brought to the front
        showActive();
    }

    /**
     * A tab closed (its ×, the close command). The active note's unsaved edits are asked
     * about first; then the tab next to it shows, or the empty state when none is left.
     */
    void close(String id) {
        EditorTabs strip = tabs.get();
        if (strip == null || id == null || !strip.isOpen(id)) {
            return;
        }
        boolean wasActive = id.equals(strip.getActiveId());
        if (wasActive && !ViewTabs.isView(id) && !leaveCurrent()) {
            return;
        }
        String neighbor = strip.neighborOf(id);
        strip.removeTab(id);
        if (ViewTabs.GRAPH.equals(id)) {
            graphStarted = false;
        }
        if (!wasActive) {
            return;
        }
        if (neighbor == null) {
            hideViews();
            if (host != null) {
                host.showEmptyState().run();
            }
            tabsChanged();
        } else {
            strip.open(neighbor, null, OpenTarget.CURRENT_TAB);
            showActive();
        }
    }

    /** The graph's own close button. */
    void closeGraph() {
        close(ViewTabs.GRAPH);
    }

    /** Back in the active tab: it shows what it showed before (a note, the graph, a new tab). */
    void back() {
        step(true);
    }

    /** Forward in the active tab. */
    void forward() {
        step(false);
    }

    private void step(boolean back) {
        EditorTabs strip = tabs.get();
        if (strip == null || !(back ? strip.canGoBack() : strip.canGoForward()) || !leaveCurrent()) {
            return;
        }
        if ((back ? strip.back() : strip.forward()) != null) {
            showActive();
        }
    }

    /**
     * Reopens a saved workspace's tabs in their order, the graph's among them (the tabs already
     * open stay), then brings its active tab to the front, or the last one reopened when that
     * one is gone. New, empty tabs are not reopened. Each tab gets back its back and forward
     * ({@code histories}, in the order of {@code ids}), without the notes that no longer exist
     * nor new tabs.
     *
     * @return how many of its notes no longer exist
     */
    /** The back and forward of each open tab, and whether it is pinned, in their order (to keep them in a workspace). */
    List<TabHistory> histories() {
        EditorTabs strip = tabs.get();
        if (strip == null) {
            return List.of();
        }
        return strip.getOpenIds().stream()
                .map(id -> new TabHistory(strip.backOf(id), strip.forwardOf(id), strip.isPinned(id)))
                .toList();
    }

    /**
     * Closes every tab, the graph's too, once the open note's unsaved edits are dealt with
     * (opening another workspace).
     *
     * @return {@code false} when the user cancels, and then every tab stays
     */
    boolean closeAll() {
        EditorTabs strip = tabs.get();
        if (strip == null) {
            return true;
        }
        if (!leaveCurrent()) {
            return false;
        }
        strip.clear();
        graphStarted = false;
        hideViews();
        if (host != null) {
            host.showEmptyState().run();
        }
        tabsChanged();
        return true;
    }

    int restoreTabs(List<String> ids, String activeId, List<TabHistory> histories) {
        if (noteService == null || host == null) {
            return 0;
        }
        int missing = 0;
        String lastOpened = null;
        for (int i = 0; i < ids.size(); i++) {
            String id = ids.get(i);
            if (ViewTabs.GRAPH.equals(id)) {
                openGraph(OpenTarget.NEW_TAB);
                lastOpened = id;
            } else if (ViewTabs.isEmptyTab(id)) {
                continue; // nothing to reopen
            } else {
                Optional<Note> note = noteService.getNoteById(id);
                if (note.isEmpty()) {
                    missing++;
                    continue;
                }
                // Each saved tab gets its own tab (a plain open would replace the previous one).
                host.openNoteInNewTab().accept(note.get());
                lastOpened = id;
            }
            if (i < histories.size()) {
                restoreHistory(id, histories.get(i));
            }
        }
        boolean activeExists = activeId != null && (ViewTabs.GRAPH.equals(activeId)
                ? ids.contains(activeId)
                : !ViewTabs.isView(activeId) && noteService.getNoteById(activeId).isPresent());
        String target = activeExists ? activeId : lastOpened;
        if (target != null) {
            select(target);
        }
        tabsChanged(); // back and forward follow the history just given back
        return missing;
    }

    private void restoreHistory(String id, TabHistory history) {
        EditorTabs strip = tabs.get();
        if (strip != null && strip.isOpen(id)) {
            strip.setHistory(id, reopenable(history.back()), reopenable(history.forward()));
            if (history.pinned()) {
                strip.pin(id);
            }
        }
    }

    /** The graph and the notes that still exist; a new tab's id does not last. */
    private List<String> reopenable(List<String> entries) {
        return entries.stream()
                .filter(entry -> ViewTabs.GRAPH.equals(entry)
                        || !ViewTabs.isView(entry) && noteService.getNoteById(entry).isPresent())
                .toList();
    }

    // ── Showing what the active tab holds ───────────────────────────────────

    /** Shows what the active tab holds: the graph, a new tab's empty state, or its note. */
    private void showActive() {
        EditorTabs strip = tabs.get();
        String id = strip != null ? strip.getActiveId() : null;
        if (id == null) {
            return;
        }
        if (ViewTabs.GRAPH.equals(id)) {
            strip.open(id, getString("graph.title"), "fth-share-2", OpenTarget.CURRENT_TAB);
            showGraph();
            if (host != null) {
                host.noNoteShown().run();
            }
        } else if (ViewTabs.isEmptyTab(id)) {
            strip.open(id, getString("tab.new_tab"), null, OpenTarget.CURRENT_TAB);
            hideViews();
            if (host != null) {
                host.showEmptyState().run();
            }
        } else {
            hideViews();
            if (host != null) {
                host.showNote().accept(id);
            }
        }
        tabsChanged();
    }

    private void showGraph() {
        UiNodes.show(graphView, true);
        graphView.toFront();
        if (graphStarted) {
            graph.resume(isDark());
        } else {
            graph.show(isDark());
            graphStarted = true;
            updateStatus(getString("status.graph_opened"));
        }
    }

    private void hideViews() {
        if (!isGraphVisible()) {
            return;
        }
        UiNodes.show(graphView, false);
        if (graph != null) {
            graph.pause();
        }
    }

    private boolean leaveCurrent() {
        return host == null || host.confirmLeaveCurrent().getAsBoolean();
    }

    // ── Graph ────────────────────────────────────────────────────────────────

    /** Re-applies the theme to the graph when it is on screen. */
    void applyGraphThemeIfVisible() {
        if (graph != null && isGraphVisible()) {
            graph.applyTheme(isDark());
        }
    }

    // ── Kanban ───────────────────────────────────────────────────────────────

    /**
     * The Kanban command: asks which board to open (the one changed last first), or to make a
     * new one. A board is a note: the one picked opens in the active tab, as a note does.
     */
    void openKanban() {
        if (noteService != null && host != null) {
            host.chooseBoard().accept(KanbanBoard.boards(noteService));
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private void tabsChanged() {
        if (host != null) {
            host.tabsChanged().run();
        }
    }

    private boolean isDark() {
        return darkTheme != null && Boolean.TRUE.equals(darkTheme.get());
    }

    private String getString(String key) {
        return i18n != null ? i18n.apply(key) : key;
    }

    private void updateStatus(String message) {
        if (status != null) {
            status.accept(message);
        }
    }
}
