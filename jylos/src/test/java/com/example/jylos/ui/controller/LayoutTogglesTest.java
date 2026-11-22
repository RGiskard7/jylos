package com.example.jylos.ui.controller;

import static com.example.jylos.tests.FxTestSupport.onFx;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.ResourceBundle;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import com.example.jylos.data.models.Note;
import com.example.jylos.tests.FxTestSupport;
import com.example.jylos.ui.preferences.LayoutMode;

import javafx.fxml.FXMLLoader;
import javafx.scene.control.SplitPane;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

/**
 * The toolbar's panel buttons: the sidebar, the notes panel and the right panel collapse
 * and come back, and the status bar says which. In the stacked layout the sidebar and the
 * notes panel share one column, so both buttons fold that column.
 */
class LayoutTogglesTest {

    @Test
    void theSidebarCollapsesAndComesBack() throws Exception {
        Assumptions.assumeTrue(FxTestSupport.isFxRuntimeAvailable());

        List<String> seen = onFx(() -> {
            Panes p = new Panes();
            List<String> out = new ArrayList<>();
            p.toggleSidebar(false);
            out.add(p.widths() + " " + p.status);
            p.toggleSidebar(false);
            out.add(p.widths() + " " + p.status);
            return out;
        });

        assertEquals(List.of(
                "sidebar 0 notes open nav open buttons [false, true] [status.sidebar_hidden]",
                "sidebar open notes open nav open buttons [true, true] [status.sidebar_hidden, status.sidebar_shown]"),
                seen);
    }

    @Test
    void theNotesPanelCollapsesAndComesBack() throws Exception {
        Assumptions.assumeTrue(FxTestSupport.isFxRuntimeAvailable());

        List<String> seen = onFx(() -> {
            Panes p = new Panes();
            List<String> out = new ArrayList<>();
            p.toggleNotesPanel(false);
            out.add(p.widths() + " " + p.status);
            p.toggleNotesPanel(false);
            out.add(p.widths() + " " + p.status);
            return out;
        });

        assertEquals(List.of(
                "sidebar open notes 0 nav open buttons [true, false] [status.notes_panel_hidden]",
                "sidebar open notes open nav open buttons [true, true] "
                        + "[status.notes_panel_hidden, status.notes_panel_shown]"), seen);
    }

    @Test
    void inTheStackedLayoutBothButtonsFoldTheNavigationColumn() throws Exception {
        Assumptions.assumeTrue(FxTestSupport.isFxRuntimeAvailable());

        List<String> seen = onFx(() -> {
            Panes p = new Panes();
            List<String> out = new ArrayList<>();
            p.toggleSidebar(true);
            out.add(p.widths() + " " + p.status);
            p.toggleNotesPanel(true);
            out.add(p.widths() + " " + p.status);
            return out;
        });

        assertEquals(List.of(
                "sidebar open notes open nav 0 buttons [false, true] [status.nav_hidden]",
                "sidebar open notes open nav open buttons [true, true] [status.nav_hidden, status.nav_shown]"), seen);
    }

    @Test
    void theRightPanelOpensWithTheNoteInfoAndCollapses() throws Exception {
        Assumptions.assumeTrue(FxTestSupport.isFxRuntimeAvailable());

        List<String> seen = onFx(() -> {
            Panes p = new Panes();
            List<String> out = new ArrayList<>();
            p.toggleRightPanel(new Note("Nota", "texto"));
            out.add("right " + p.right.getPrefWidth() + " button " + p.toolbar.getRightPanelToggleBtn().isSelected()
                    + " info " + p.infoRefreshes);
            p.toggleRightPanel(new Note("Nota", "texto"));
            out.add("right " + p.right.getPrefWidth() + " button " + p.toolbar.getRightPanelToggleBtn().isSelected()
                    + " info " + p.infoRefreshes);
            p.toggleRightPanel(null);
            out.add("right " + p.right.getPrefWidth() + " button " + p.toolbar.getRightPanelToggleBtn().isSelected()
                    + " info " + p.infoRefreshes);
            return out;
        });

        // Opening refreshes the note's info only when a note is open.
        assertEquals(List.of("right 300.0 button true info 1", "right 0.0 button false info 1",
                "right 300.0 button true info 1"), seen);
    }

    /** Picking a folder in the sidebar brings back a collapsed notes panel, which then lists it. */
    @Test
    void pickingAFolderBringsBackACollapsedNotesPanel() throws Exception {
        Assumptions.assumeTrue(FxTestSupport.isFxRuntimeAvailable());

        List<String> seen = onFx(() -> {
            Panes p = new Panes();
            List<String> out = new ArrayList<>();
            p.toggleNotesPanel(false);
            out.add(p.widths());
            p.layout.revealNotesPanel();
            out.add(p.widths());
            return out;
        });

        assertEquals(List.of("sidebar open notes 0 nav open buttons [true, false]",
                "sidebar open notes open nav open buttons [true, true]"), seen);
    }

    /** The window's panes, wired as the main window does; the right panel starts collapsed. */
    private static final class Panes {
        final SplitPane main = new SplitPane();
        final SplitPane content = new SplitPane();
        final SplitPane nav = new SplitPane();
        final SplitPane centerRight = new SplitPane();
        final VBox sidebar = new VBox();
        final VBox notes = new VBox();
        final VBox editor = new VBox();
        final VBox right = new VBox();
        final List<String> status = new ArrayList<>();
        final ToolbarController toolbar;
        final UiLayout layout = new UiLayout();
        int infoRefreshes;

        Panes() throws Exception {
            for (Region pane : List.of(sidebar, notes, nav)) {
                pane.setMaxWidth(Double.MAX_VALUE);
            }
            right.setMaxWidth(0);
            right.setPrefWidth(0);
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/example/jylos/ui/view/ToolbarView.fxml"),
                    ResourceBundle.getBundle("com.example.jylos.i18n.messages", Locale.forLanguageTag("es")));
            loader.load();
            toolbar = loader.getController();
            toolbar.getSidebarToggleBtn().setSelected(true);
            toolbar.getNotesPanelToggleBtn().setSelected(true);
            layout.wire(new UiLayout.Panes(main, content, nav, centerRight, sidebar, notes, editor, right),
                    () -> toolbar, key -> key, status::add);
        }

        void toggleSidebar(boolean stacked) {
            useStacked(stacked);
            layout.toggleSidebar();
        }

        void toggleNotesPanel(boolean stacked) {
            useStacked(stacked);
            layout.toggleNotesPanel();
        }

        void toggleRightPanel(Note note) {
            layout.toggleRightPanel(note, shown -> infoRefreshes++);
        }

        private void useStacked(boolean stacked) {
            LayoutMode mode = stacked ? LayoutMode.CLASSIC_STACKED : LayoutMode.CLASSIC_COLUMNS;
            if (layout.mode() != mode) {
                layout.arrange(mode, false);
            }
        }

        String widths() {
            return "sidebar " + width(sidebar) + " notes " + width(notes) + " nav " + width(nav)
                    + (status.isEmpty() ? "" : " buttons " + List.of(toolbar.getSidebarToggleBtn().isSelected(),
                            toolbar.getNotesPanelToggleBtn().isSelected()));
        }

        private static String width(Region pane) {
            return pane.getMaxWidth() < 10 ? "0" : "open";
        }
    }
}
