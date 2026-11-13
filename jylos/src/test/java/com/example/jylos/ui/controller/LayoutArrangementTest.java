package com.example.jylos.ui.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
            NavigationCommand command = new NavigationCommand();
            command.arrangeLayout(LayoutMode.CLASSIC_STACKED, false, p.main, p.content, p.nav, p.sidebar, p.notes,
                    p.editor);
            String stacked = p.describe();
            command.arrangeLayout(LayoutMode.EXPLORER, false, p.main, p.content, p.nav, p.sidebar, p.notes, p.editor);
            String explorer = p.describe();
            command.arrangeLayout(LayoutMode.EXPLORER, true, p.main, p.content, p.nav, p.sidebar, p.notes, p.editor);
            String explorerWithResults = p.describe();
            command.arrangeLayout(LayoutMode.CLASSIC_COLUMNS, false, p.main, p.content, p.nav, p.sidebar, p.notes,
                    p.editor);
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
            new NavigationCommand().arrangeLayout(LayoutMode.EXPLORER, true, p.main, p.content, p.nav, p.sidebar,
                    p.notes, p.editor);
            return new double[] { p.sidebar.getMaxWidth(), p.notes.getMaxWidth() };
        });

        assertEquals(Double.MAX_VALUE, widths[0]);
        assertEquals(Double.MAX_VALUE, widths[1]);
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
