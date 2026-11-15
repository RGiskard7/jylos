package com.example.jylos.ui.controller;

import java.util.List;
import java.util.Optional;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import com.example.jylos.data.models.Note;
import com.example.jylos.service.NoteService;
import com.example.jylos.ui.UiNodes;
import com.example.jylos.ui.components.EditorTabs;
import com.example.jylos.ui.components.EditorTabsModel;
import com.example.jylos.ui.components.EditorTabsModel.OpenTarget;
import com.example.jylos.ui.components.KanbanBoard;
import com.example.jylos.ui.components.ViewTabs;

import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

/**
 * The editor's tabs, as in Obsidian: each one shows a note, the graph or nothing yet (a new
 * tab), and keeps its own back and forward ({@link EditorTabsModel}). Picking a tab, closing
 * one, going back or forward and reopening a workspace all come through here, and end in the
 * one place that shows what a tab holds: the note in the editor, the graph in the editor area,
 * or the editor's empty state. Also, until boards open in their own tabs, the Kanban board
 * shown over the window.
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
     * @param tabsChanged         what is shown changed: the right panel, the status bar and the
     *                            back/forward buttons follow it
     */
    record Host(BooleanSupplier confirmLeaveCurrent, Consumer<String> showNote, Consumer<Note> openNoteInNewTab,
            Consumer<Note> openNote, Runnable showEmptyState, Runnable tabsChanged) {
    }

    private StackPane centerStack;
    private VBox graphView;
    private GraphController graph;
    private NoteService noteService;
    private Supplier<Boolean> darkTheme;
    private Function<String, String> i18n;
    private Consumer<String> status;
    private Supplier<EditorTabs> tabs = () -> null;
    private Host host;
    private Consumer<Note> noteCreated;
    private Consumer<Note> noteUpdated;
    /** Whether the graph has been opened since it was last closed: then it resumes. */
    private boolean graphStarted;

    /** Lazy-created Kanban board overlay, added to {@link #centerStack} on first use. */
    private KanbanBoard kanbanBoard;

    void wire(StackPane centerStack, VBox graphView, GraphController graph, NoteService noteService,
            Supplier<Boolean> darkTheme, Function<String, String> i18n, Consumer<String> status,
            Supplier<EditorTabs> tabs, Host host, Consumer<Note> noteCreated, Consumer<Note> noteUpdated) {
        this.centerStack = centerStack;
        this.graphView = graphView;
        this.graph = graph;
        this.noteService = noteService;
        this.darkTheme = darkTheme;
        this.i18n = i18n;
        this.status = status;
        this.tabs = tabs != null ? tabs : () -> null;
        this.host = host;
        this.noteCreated = noteCreated;
        this.noteUpdated = noteUpdated;
        UiNodes.show(graphView, false);
    }

    /** True while a view's tab (the graph, a new tab), not a note's, is the active one. */
    boolean isViewActive() {
        EditorTabs strip = tabs.get();
        return strip != null && ViewTabs.isView(strip.getActiveId());
    }

    /** True while the graph is on screen. */
    boolean isGraphVisible() {
        return graphView != null && graphView.isVisible();
    }

    /** True while the Kanban overlay is shown. */
    boolean isKanbanVisible() {
        return kanbanBoard != null && kanbanBoard.isVisible();
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
     * one is gone. New, empty tabs are not reopened.
     *
     * @return how many of its notes no longer exist
     */
    int restoreTabs(List<String> ids, String activeId) {
        if (noteService == null || host == null) {
            return 0;
        }
        int missing = 0;
        String lastOpened = null;
        for (String id : ids) {
            if (ViewTabs.GRAPH.equals(id)) {
                openGraph(OpenTarget.NEW_TAB);
                lastOpened = id;
                continue;
            }
            if (ViewTabs.isEmptyTab(id)) {
                continue; // nothing to reopen
            }
            Optional<Note> note = noteService.getNoteById(id);
            if (note.isPresent()) {
                // Each saved tab gets its own tab (a plain open would replace the previous one).
                host.openNoteInNewTab().accept(note.get());
                lastOpened = id;
            } else {
                missing++;
            }
        }
        boolean activeExists = activeId != null && (ViewTabs.GRAPH.equals(activeId)
                ? ids.contains(activeId)
                : !ViewTabs.isView(activeId) && noteService.getNoteById(activeId).isPresent());
        String target = activeExists ? activeId : lastOpened;
        if (target != null) {
            select(target);
        }
        return missing;
    }

    // ── Showing what the active tab holds ───────────────────────────────────

    /** Shows what the active tab holds: the graph, a new tab's empty state, or its note. */
    private void showActive() {
        EditorTabs strip = tabs.get();
        String id = strip != null ? strip.getActiveId() : null;
        if (id == null) {
            return;
        }
        hideKanban();
        if (ViewTabs.GRAPH.equals(id)) {
            strip.open(id, getString("graph.title"), "fth-share-2", OpenTarget.CURRENT_TAB);
            showGraph();
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

    // ── Kanban overlay ───────────────────────────────────────────────────────

    void toggleKanban() {
        if (centerStack == null) {
            return;
        }
        ensureKanban();
        if (kanbanBoard == null) {
            return;
        }
        if (kanbanBoard.isVisible()) {
            hideKanban();
        } else {
            kanbanBoard.setDarkTheme(isDark());
            kanbanBoard.reload();
            UiNodes.show(kanbanBoard, true);
            kanbanBoard.toFront();
            kanbanBoard.requestFocus(); // so Escape closes the board
            updateStatus(getString("status.kanban_opened"));
            tabsChanged();
        }
    }

    void hideKanban() {
        if (!isKanbanVisible()) {
            return;
        }
        UiNodes.show(kanbanBoard, false);
        tabsChanged();
    }

    private void ensureKanban() {
        if (kanbanBoard != null || centerStack == null || noteService == null) {
            return;
        }
        kanbanBoard = new KanbanBoard(noteService, this::openLinkedNote, this::hideKanban,
                this::publishCreated, this::publishUpdated, i18n);
        UiNodes.show(kanbanBoard, false);
        centerStack.getChildren().add(kanbanBoard);
    }

    /**
     * Opens the note a Kanban card links to ({@code [[Note]]}, {@code [[Folder/Note]]}), resolved
     * by path from the board's note as any link is.
     */
    void openLinkedNote(String link) {
        if (link == null || link.isBlank() || noteService == null) {
            return;
        }
        String boardId = kanbanBoard != null ? kanbanBoard.currentBoardId() : null;
        noteService.resolveLink(link, boardId).ifPresent(note -> {
            hideKanban();
            if (host != null) {
                host.openNote().accept(note);
            }
        });
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private void tabsChanged() {
        if (host != null) {
            host.tabsChanged().run();
        }
    }

    private void publishCreated(Note note) {
        if (noteCreated != null && note != null) {
            noteCreated.accept(note);
        }
    }

    private void publishUpdated(Note note) {
        if (noteUpdated != null && note != null) {
            noteUpdated.accept(note);
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
