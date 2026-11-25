package com.example.jylos.ui.controller;

import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import com.example.jylos.data.models.Note;
import com.example.jylos.ui.UiNodes;
import com.example.jylos.ui.preferences.LayoutMode;

import javafx.scene.Node;
import javafx.scene.control.SplitPane;
import javafx.scene.control.ToggleButton;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

/**
 * Where the window's panels go: the layout ({@link LayoutMode}), the Explorer's results
 * panel, and the sidebar, notes panel and right panel collapsed or shown from their toolbar
 * buttons, which it keeps pressed or not to match.
 *
 * <p>It owns the layout state; the main window owns the panes (from its FXML) and hands
 * them over once, and still tells the other controllers about a layout change.</p>
 *
 * @author Edu Díaz (RGiskard7)
 * @since 2.3.0
 */
class UiLayout {

    /**
     * The window's panes, from {@code MainView.fxml}. The panes every layout places are
     * required; the right panel and its split pane may be missing in tests.
     */
    record Panes(SplitPane main, SplitPane content, SplitPane nav, SplitPane centerRight, VBox sidebar,
            VBox notes, Node editor, VBox right) {

        Panes {
            Objects.requireNonNull(main, "main split pane");
            Objects.requireNonNull(content, "content split pane");
            Objects.requireNonNull(nav, "navigation split pane");
            Objects.requireNonNull(sidebar, "sidebar");
            Objects.requireNonNull(notes, "notes panel");
            Objects.requireNonNull(editor, "editor");
        }
    }

    private Panes panes;
    private Supplier<ToolbarController> toolbar = () -> null;
    private Function<String, String> i18n = key -> key;
    private Consumer<String> status = message -> {
    };

    private LayoutMode mode = LayoutMode.CLASSIC_COLUMNS;
    /** Explorer layout only: whether the notes panel is open to show tag or search results. */
    private boolean resultsShown;

    void wire(Panes panes, Supplier<ToolbarController> toolbar, Function<String, String> i18n,
            Consumer<String> status) {
        this.panes = panes;
        if (panes != null) {
            // The side panels keep their width in pixels: when the space around them changes
            // (the right panel opens or closes, the window is resized) the editor gives or takes
            // it. A split pane otherwise keeps each divider at a share of its width, so closing
            // the right panel widened the sidebar.
            for (Node pane : new Node[] {panes.sidebar(), panes.notes(), panes.nav(), panes.right()}) {
                if (pane != null) {
                    SplitPane.setResizableWithParent(pane, false);
                }
            }
        }
        this.toolbar = toolbar;
        this.i18n = i18n;
        this.status = status;
    }

    LayoutMode mode() {
        return mode;
    }

    boolean isExplorer() {
        return mode.isExplorer();
    }

    boolean isStacked() {
        return mode.isStacked();
    }

    boolean resultsShown() {
        return resultsShown;
    }

    /**
     * Puts the sidebar, the notes panel and the editor where {@code mode} wants them:
     * <ul>
     * <li>classic columns: sidebar | notes | editor;</li>
     * <li>classic stacked: (sidebar above notes) | editor;</li>
     * <li>Explorer: sidebar | editor, with the notes panel before the editor only while it
     * shows results ({@code showResults}).</li>
     * </ul>
     * Panels collapsed with their toggle are shown again, since the toggle state does not
     * carry over to a different arrangement.
     *
     * @param mode        the layout; {@code null} is the classic columns
     * @param showResults Explorer only: whether the results panel is open
     */
    void arrange(LayoutMode mode, boolean showResults) {
        this.mode = mode != null ? mode : LayoutMode.CLASSIC_COLUMNS;
        this.resultsShown = this.mode.isExplorer() && showResults;
        Panes p = panes();
        SplitPane main = p.main();
        SplitPane content = p.content();
        SplitPane nav = p.nav();
        main.getItems().clear();
        content.getItems().clear();
        nav.getItems().clear();
        expand(p.sidebar(), 200);
        expand(p.notes(), 180);
        switch (this.mode) {
            case CLASSIC_STACKED -> {
                nav.getItems().addAll(p.sidebar(), p.notes());
                nav.setDividerPositions(0.5);
                main.getItems().addAll(nav, p.editor());
                main.setDividerPositions(0.25);
            }
            case EXPLORER -> {
                if (resultsShown) {
                    content.getItems().addAll(p.notes(), p.editor());
                    content.setDividerPositions(0.3);
                } else {
                    content.getItems().add(p.editor());
                }
                main.getItems().addAll(p.sidebar(), content);
                main.setDividerPositions(0.22);
            }
            default -> {
                content.getItems().addAll(p.notes(), p.editor());
                content.setDividerPositions(0.3);
                main.getItems().addAll(p.sidebar(), content);
                main.setDividerPositions(0.22);
            }
        }
        setSidebarToggle(true);
        setNotesToggle(true);
    }

    /** Collapses the sidebar, or shows it; in the stacked layout, the whole navigation column. */
    void toggleSidebar() {
        if (isStacked()) {
            SplitPane nav = panes().nav();
            if (nav == null) {
                return;
            }
            boolean collapsed = nav.getMaxWidth() < 10;
            if (collapsed) {
                showStackedNavigation();
            } else {
                collapse(nav);
            }
            status.accept(i18n.apply(collapsed ? "status.nav_shown" : "status.nav_hidden"));
            setSidebarToggle(collapsed);
            return;
        }
        VBox sidebar = panes().sidebar();
        if (sidebar == null) {
            return;
        }
        boolean collapsed = sidebar.getMaxWidth() < 10;
        if (collapsed) {
            showSidebar();
        } else {
            collapse(sidebar);
        }
        status.accept(i18n.apply(collapsed ? "status.sidebar_shown" : "status.sidebar_hidden"));
        setSidebarToggle(collapsed);
    }

    /**
     * Collapses the notes panel, or shows it. In the stacked layout it shares the sidebar's
     * column, so the column folds instead.
     */
    void toggleNotesPanel() {
        if (isStacked()) {
            toggleSidebar();
            return;
        }
        VBox notes = panes().notes();
        if (notes == null) {
            return;
        }
        boolean collapsed = notes.getMaxWidth() < 10;
        if (collapsed) {
            showNotesPanel();
        } else {
            collapse(notes);
        }
        status.accept(i18n.apply(collapsed ? "status.notes_panel_shown" : "status.notes_panel_hidden"));
        setNotesToggle(collapsed);
    }

    /** Shows the sidebar and the notes panel (at startup, once the split panes have dividers). */
    void showNavigationPanels() {
        if (isStacked()) {
            showStackedNavigation();
        } else {
            showSidebar();
            showNotesPanel();
        }
        setSidebarToggle(true);
        setNotesToggle(true);
    }

    /**
     * Brings back a collapsed notes panel, so a folder picked in the sidebar shows its notes.
     * In the stacked layout it also reopens the panel when the divider hides it.
     */
    void revealNotesPanel() {
        VBox notes = panes().notes();
        if (notes == null) {
            return;
        }
        if (isStacked()) {
            SplitPane nav = panes().nav();
            double[] positions = nav != null ? nav.getDividerPositions() : new double[0];
            boolean dividerCollapsed = positions.length > 0 && positions[0] > 0.95;
            if (notes.getMaxWidth() < 10 || dividerCollapsed) {
                expand(notes, 180);
                if (nav != null) {
                    nav.setDividerPositions(0.5);
                }
            }
        } else if (notes.getMaxWidth() < 10) {
            expand(notes, 180);
            if (panes().content() != null) {
                panes().content().setDividerPositions(0.25);
            }
        }
        setNotesToggle(true);
    }

    /**
     * Opens or collapses the right panel without recreating it, preserving plugin panels and
     * backlinks state between toggles. The panel is a sibling of the center content
     * (editor/graph/Kanban) in {@code centerRight}, not nested inside any one of them, so it
     * stays reachable whichever center view is active.
     *
     * @param currentNote     the note in sight, or {@code null}
     * @param refreshNoteInfo given that note (or {@code null}) when the panel opens
     */
    void toggleRightPanel(Note currentNote, Consumer<Note> refreshNoteInfo) {
        VBox right = panes().right();
        if (right == null) {
            return;
        }
        boolean collapsed = !isRightPanelOpen();
        if (collapsed) {
            UiNodes.show(right, true);
            right.setMinWidth(240);
            right.setMaxWidth(Double.MAX_VALUE);
            right.setPrefWidth(300);
            if (panes().centerRight() != null) {
                panes().centerRight().setDividerPositions(0.8);
            }
        } else {
            collapse(right);
        }
        setRightPanelToggle(collapsed);
        // Opened: what it shows works itself out, with no note in sight too (a view pinned to a note).
        if (collapsed && refreshNoteInfo != null) {
            refreshNoteInfo.accept(currentNote);
        }
    }

    /** Whether the right panel is open (not collapsed to zero width). */
    boolean isRightPanelOpen() {
        VBox right = panes != null ? panes.right() : null;
        return right != null && right.isManaged() && right.isVisible() && right.getPrefWidth() >= 10;
    }

    /** Collapses the right panel to zero width; it stays in the scene, so it opens instantly. */
    void collapseRightPanel() {
        VBox right = panes().right();
        if (right != null) {
            UiNodes.show(right, true);
            collapse(right);
        }
        setRightPanelToggle(false);
    }

    private Panes panes() {
        if (panes == null) {
            throw new IllegalStateException("the window layout is used before its panes are wired");
        }
        return panes;
    }

    private void showStackedNavigation() {
        SplitPane nav = panes().nav();
        if (nav == null) {
            return;
        }
        nav.setMinWidth(200);
        nav.setMaxWidth(Double.MAX_VALUE);
        nav.setPrefWidth(300);
        if (panes().main() != null) {
            panes().main().setDividerPositions(0.25);
        }
    }

    private void showSidebar() {
        VBox sidebar = panes().sidebar();
        if (sidebar == null) {
            return;
        }
        sidebar.setMinWidth(200);
        sidebar.setMaxWidth(Double.MAX_VALUE);
        sidebar.setPrefWidth(250);
        if (panes().main() != null) {
            panes().main().setDividerPositions(0.22);
        }
    }

    private void showNotesPanel() {
        VBox notes = panes().notes();
        if (notes == null) {
            return;
        }
        notes.setMinWidth(180);
        notes.setMaxWidth(Double.MAX_VALUE);
        notes.setPrefWidth(260);
        if (panes().content() != null) {
            panes().content().setDividerPositions(0.25);
        }
    }

    private static void collapse(Region pane) {
        pane.setMinWidth(0);
        pane.setMaxWidth(0);
        pane.setPrefWidth(0);
    }

    private static void expand(Node pane, double minWidth) {
        if (pane instanceof Region region) {
            region.setMinWidth(minWidth);
            region.setMaxWidth(Double.MAX_VALUE);
        }
    }

    private void setSidebarToggle(boolean selected) {
        ToolbarController bar = toolbar.get();
        if (bar != null && bar.getSidebarToggleBtn() != null) {
            bar.getSidebarToggleBtn().setSelected(selected);
        }
    }

    private void setRightPanelToggle(boolean selected) {
        ToolbarController bar = toolbar.get();
        ToggleButton button = bar != null ? bar.getRightPanelToggleBtn() : null;
        if (button != null) {
            button.setSelected(selected);
        }
    }

    private void setNotesToggle(boolean selected) {
        ToolbarController bar = toolbar.get();
        if (bar != null && bar.getNotesPanelToggleBtn() != null) {
            bar.getNotesPanelToggleBtn().setSelected(selected);
        }
    }
}
