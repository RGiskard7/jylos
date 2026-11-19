package com.example.jylos.ui.components;

import static com.example.jylos.tests.FxTestSupport.onFx;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.kordamp.ikonli.javafx.FontIcon;

import com.example.jylos.tests.FxTestSupport;
import com.example.jylos.ui.components.EditorTabsModel.OpenTarget;

import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.layout.HBox;

/**
 * A tab can hold a view (the graph) instead of a note. It follows the same tab rules, shows
 * its icon, and never shows the unsaved dot.
 */
class ViewTabsTest {

    @Test
    void viewIdsAreToldApartFromNoteIds() {
        assertTrue(ViewTabs.isView(ViewTabs.GRAPH));
        assertFalse(ViewTabs.isView("Notas/Grafo.md"));
        assertFalse(ViewTabs.isView("graph"));
        assertFalse(ViewTabs.isView(null));
    }

    @Test
    void eachNewTabHasItsOwnId() {
        String first = ViewTabs.newEmptyTab();
        String second = ViewTabs.newEmptyTab();

        assertFalse(first.equals(second));
        assertTrue(ViewTabs.isView(first));
        assertTrue(ViewTabs.isEmptyTab(first));
        assertFalse(ViewTabs.isEmptyTab(ViewTabs.GRAPH));
        assertFalse(ViewTabs.isEmptyTab("Notas/Nueva.md"));
    }

    /** The + after the tabs opens a new tab, and is there even with no tab open. */
    @Test
    void thePlusAfterTheTabsOpensANewTab() throws Exception {
        Assumptions.assumeTrue(FxTestSupport.isFxRuntimeAvailable());

        List<String> seen = onFx(() -> {
            HBox bar = new HBox();
            EditorTabs tabs = new EditorTabs(bar, bar, null, key -> key);
            List<String> asked = new ArrayList<>();
            tabs.setNewTabAction(() -> asked.add("new tab"));
            List<String> out = new ArrayList<>();
            out.add("no tabs: visible " + bar.isVisible() + ", last " + last(bar));
            tabs.open("a.md", "Alfa", OpenTarget.CURRENT_TAB);
            out.add("one tab: last " + last(bar));
            Node plus = bar.getChildren().get(bar.getChildren().size() - 1);
            ((Button) plus).fire();
            out.add(String.valueOf(asked));
            return out;
        });

        assertEquals(List.of("no tabs: visible true, last +", "one tab: last +", "[new tab]"), seen);
    }

    private static String last(HBox bar) {
        if (bar.getChildren().isEmpty()) {
            return "nothing";
        }
        Node node = bar.getChildren().get(bar.getChildren().size() - 1);
        return node.getStyleClass().contains("editor-tab-new") ? "+" : node.getStyleClass().toString();
    }

    @Test
    void theGraphTabFollowsTheTabRules() {
        EditorTabsModel model = new EditorTabsModel();
        model.open("a.md", OpenTarget.CURRENT_TAB);
        model.open("b.md", OpenTarget.NEW_TAB);
        model.open("a.md", OpenTarget.CURRENT_TAB);
        model.open(ViewTabs.GRAPH, OpenTarget.NEW_TAB);
        List<String> afterOpening = model.getIds();
        model.open(ViewTabs.GRAPH, OpenTarget.NEW_TAB); // opening it again activates it
        List<String> afterReopening = model.getIds();
        model.open("c.md", OpenTarget.CURRENT_TAB); // a plain open with the graph in front replaces it

        assertEquals(List.of("a.md", ViewTabs.GRAPH, "b.md"), afterOpening);
        assertEquals(afterOpening, afterReopening);
        assertEquals(List.of("a.md", "c.md", "b.md"), model.getIds());
    }

    @Test
    void aViewTabShowsItsIconAndNeverTheUnsavedDot() throws Exception {
        Assumptions.assumeTrue(FxTestSupport.isFxRuntimeAvailable());

        List<String> seen = onFx(() -> {
            HBox bar = new HBox();
            EditorTabs tabs = new EditorTabs(bar, bar, null, key -> key);
            tabs.open("a.md", "Alfa", OpenTarget.CURRENT_TAB);
            tabs.open(ViewTabs.GRAPH, "Grafo", "fth-share-2", OpenTarget.NEW_TAB);
            tabs.setDirty(ViewTabs.GRAPH, true);
            tabs.setDirty("a.md", true);
            List<String> out = new ArrayList<>();
            for (Node tab : bar.getChildren()) {
                List<String> parts = new ArrayList<>();
                for (Node child : ((HBox) tab).getChildren()) {
                    if (child instanceof FontIcon icon) {
                        parts.add("icon " + icon.getIconLiteral());
                    } else if (child.getStyleClass().contains("editor-tab-dot")) {
                        parts.add("dot " + child.isVisible());
                    }
                }
                out.add(String.join(", ", parts));
            }
            return out;
        });

        assertEquals(List.of("dot true", "dot false, icon fth-share-2"), seen);
    }
}
