package com.example.jylos.ui.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static com.example.jylos.tests.FxTestSupport.onFx;

import java.util.List;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import com.example.jylos.tests.FxTestSupport;
import com.example.jylos.ui.preferences.LayoutMode;

import javafx.scene.Node;
import javafx.scene.control.SplitPane;
import javafx.scene.layout.VBox;

/**
 * Where the sidebar, the notes panel and the editor go in each layout. In the Explorer the
 * notes panel is only there while it shows tag or search results.
 */
class LayoutArrangementTest {

    @Test
    void eachLayoutPlacesThePanels() throws Exception {
        Assumptions.assumeTrue(FxTestSupport.isFxRuntimeAvailable());

        List<String> arrangements = onFx(() -> {
            Panes p = new Panes();
            UiLayout layout = p.layout();
            layout.arrange(LayoutMode.CLASSIC_STACKED, false);
            String stacked = p.describe();
            layout.arrange(LayoutMode.EXPLORER, false);
            String explorer = p.describe();
            layout.arrange(LayoutMode.EXPLORER, true);
            String explorerWithResults = p.describe();
            layout.arrange(LayoutMode.CLASSIC_COLUMNS, false);
            String columns = p.describe();
            return List.of(stacked, explorer, explorerWithResults, columns);
        });

        assertEquals(List.of(
                "main=[nav, editor] content=[] nav=[sidebar, notes]",
                "main=[sidebar, content] content=[editor] nav=[]",
                "main=[sidebar, content] content=[notes, editor] nav=[]",
                "main=[sidebar, content] content=[notes, editor] nav=[]"), arrangements);
    }

    @Test
    void aPanelHiddenByItsToggleIsShownAgainWhenTheLayoutChanges() throws Exception {
        Assumptions.assumeTrue(FxTestSupport.isFxRuntimeAvailable());

        double[] widths = onFx(() -> {
            Panes p = new Panes();
            p.notes.setMaxWidth(0);
            p.sidebar.setMaxWidth(0);
            p.layout().arrange(LayoutMode.EXPLORER, true);
            return new double[] { p.sidebar.getMaxWidth(), p.notes.getMaxWidth() };
        });

        assertEquals(Double.MAX_VALUE, widths[0]);
        assertEquals(Double.MAX_VALUE, widths[1]);
    }

    /**
     * Wiring the layout before a pane exists is a mistake in the window's start-up order: it
     * fails at once instead of leaving the panels where the FXML put them in every layout.
     */
    @Test
    void wiringWithoutAPaneFails() throws Exception {
        Assumptions.assumeTrue(FxTestSupport.isFxRuntimeAvailable());

        Throwable failure = onFx(() -> {
            Panes p = new Panes();
            try {
                new UiLayout().wire(new UiLayout.Panes(p.main, p.content, p.nav, null, p.sidebar, null, p.editor,
                        null), () -> null, key -> key, message -> { });
                return null;
            } catch (NullPointerException e) {
                return e;
            }
        });

        assertNotNull(failure, "wiring without the notes panel should fail");
        assertEquals("notes panel", failure.getMessage());
    }

    private static final class Panes {
        final SplitPane main = named(new SplitPane(), "main");
        final SplitPane content = named(new SplitPane(), "content");
        final SplitPane nav = named(new SplitPane(), "nav");
        final VBox sidebar = named(new VBox(), "sidebar");
        final VBox notes = named(new VBox(), "notes");
        final VBox editor = named(new VBox(), "editor");

        Panes() {
            content.getItems().setAll(notes, editor);
            main.getItems().setAll(sidebar, content);
        }

        UiLayout layout() {
            UiLayout layout = new UiLayout();
            layout.wire(new UiLayout.Panes(main, content, nav, null, sidebar, notes, editor, null), () -> null,
                    key -> key, message -> { });
            return layout;
        }

        String describe() {
            return "main=" + ids(main) + " content=" + ids(content) + " nav=" + ids(nav);
        }

        private static String ids(SplitPane pane) {
            return pane.getItems().stream().map(Node::getId).toList().toString();
        }

        private static <T extends Node> T named(T node, String id) {
            node.setId(id);
            return node;
        }
    }
}
