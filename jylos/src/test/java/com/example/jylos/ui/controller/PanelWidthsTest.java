package com.example.jylos.ui.controller;

import static com.example.jylos.tests.FxTestSupport.onFx;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import com.example.jylos.tests.FxTestSupport;
import com.example.jylos.ui.preferences.LayoutMode;

import javafx.scene.Scene;
import javafx.scene.control.SplitPane;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.stage.Window;

/**
 * The side panels keep their width in pixels when the space around them changes: opening or
 * closing the right panel, or resizing the window, gives or takes that space from the editor.
 * Before, the sidebar and the notes panel kept a share of the width instead, so closing the
 * right panel widened them.
 */
class PanelWidthsTest {

    @AfterEach
    void closeWindows() throws Exception {
        if (FxTestSupport.isFxRuntimeAvailable()) {
            onFx(() -> {
                new ArrayList<>(Window.getWindows()).forEach(Window::hide);
                return null;
            });
        }
    }

    @Test
    void openingAndClosingTheRightPanelLeavesTheSidebarAsItWas() throws Exception {
        Assumptions.assumeTrue(FxTestSupport.isFxRuntimeAvailable());

        List<String> seen = onFx(() -> {
            Window_ w = new Window_(LayoutMode.EXPLORER);
            List<String> out = new ArrayList<>();
            out.add(w.widths());
            w.layout.toggleRightPanel(null, null);
            out.add(w.widths());
            w.layout.toggleRightPanel(null, null);
            out.add(w.widths());
            return out;
        });

        String sidebar = seen.get(0).split(" ")[1];
        assertTrue(Integer.parseInt(sidebar) >= 200 && Integer.parseInt(sidebar) < 600, "sidebar " + sidebar);
        String opened = seen.get(1).split(" ")[3];
        assertTrue(Integer.parseInt(opened) >= 240 && Integer.parseInt(opened) < 400, "right " + opened);
        assertEquals(List.of("sidebar " + sidebar + " right 0", "sidebar " + sidebar + " right " + opened,
                "sidebar " + sidebar + " right 0"), seen);
    }

    @Test
    void theSidePanelsKeepTheirWidthWhenTheWindowOrTheRightPanelChanges() throws Exception {
        Assumptions.assumeTrue(FxTestSupport.isFxRuntimeAvailable());

        List<String> seen = onFx(() -> {
            Window_ w = new Window_(LayoutMode.CLASSIC_COLUMNS);
            List<String> out = new ArrayList<>();
            out.add(w.widths());
            w.layout.toggleRightPanel(null, null);
            out.add(w.widths());
            w.resize(1700);
            out.add(w.widths());
            w.layout.toggleRightPanel(null, null);
            out.add(w.widths());
            return out;
        });

        String first = seen.get(0);
        String panels = first.substring(0, first.indexOf(" right"));
        assertTrue(panels.matches("sidebar \\d{3} notes \\d{3}"), panels);
        String opened = seen.get(1).split(" ")[5];
        assertTrue(Integer.parseInt(opened) >= 240 && Integer.parseInt(opened) < 400, "right " + opened);
        // The window grows too: the right panel keeps its width as well.
        assertEquals(List.of(panels + " right 0", panels + " right " + opened, panels + " right " + opened,
                panels + " right 0"), seen);
    }

    /** The window's panes as the main window lays them out, in a 1440 px window. */
    private static final class Window_ {
        final SplitPane main = new SplitPane();
        final SplitPane content = new SplitPane();
        final SplitPane centerRight = new SplitPane();
        final VBox sidebar = new VBox();
        final VBox notes = new VBox();
        final VBox editor = new VBox();
        final VBox right = new VBox();
        final UiLayout layout = new UiLayout();

        Window_(LayoutMode mode) {
            right.setMinWidth(0);
            right.setPrefWidth(0);
            right.setMaxWidth(0);
            centerRight.getItems().addAll(new StackPane(main), right);
            layout.wire(new UiLayout.Panes(main, content, new SplitPane(), centerRight, sidebar, notes, editor, right),
                    () -> null, key -> key, message -> {
                    });
            layout.arrange(mode, false);
            Stage stage = new Stage();
            stage.setScene(new Scene(centerRight, 1440, 800));
            stage.show();
            layout.showNavigationPanels();
            settle();
        }

        void resize(double width) {
            centerRight.resize(width, 800);
            settle();
        }

        String widths() {
            settle();
            String panels = "sidebar " + width(sidebar);
            if (layout.mode() != LayoutMode.EXPLORER) {
                panels += " notes " + width(notes);
            }
            return panels + " right " + width(right);
        }

        private void settle() {
            centerRight.applyCss();
            centerRight.layout();
            main.layout();
            content.layout();
        }

        private static long width(Region region) {
            return Math.round(region.getWidth());
        }
    }
}
