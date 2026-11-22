package com.example.jylos.ui.components;

import static com.example.jylos.tests.FxTestSupport.onFx;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import com.example.jylos.tests.FxTestSupport;

import javafx.event.Event;
import javafx.geometry.Bounds;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.input.ScrollEvent;
import javafx.stage.Stage;

/**
 * The right panel as Obsidian's: a row of icon tabs on top, one per view, and under it the
 * view of the tab chosen, alone. When the tabs do not fit the row scrolls sideways with the
 * mouse wheel, without a scroll bar.
 */
class SideDockTest {

    private static SideDock dockInWindow(double width) {
        SideDock dock = new SideDock();
        Stage stage = new Stage();
        stage.setScene(new Scene(dock, width, 400));
        stage.show();
        return dock;
    }

    private static void add(SideDock dock, String id) {
        dock.addView(id, "Title " + id, new Label("icon"), new Label("content " + id));
    }

    /** What the panel shows: the tabs' tooltips, in order, then the views showing and the tab marked chosen. */
    private static String state(SideDock dock) {
        dock.applyCss();
        dock.layout();
        List<String> tabs = new ArrayList<>();
        String chosen = null;
        for (Node node : dock.lookupAll(".side-dock-tab")) {
            if (!node.isVisible()) {
                continue;
            }
            Button tab = (Button) node;
            tabs.add(tab.getTooltip().getText());
            if (tab.getStyleClass().contains("sidebar-nav-btn-selected")) {
                chosen = tab.getTooltip().getText();
            }
        }
        List<String> showing = new ArrayList<>();
        for (Node node : dock.lookupAll(".label")) {
            if (node instanceof Label label && label.getText().startsWith("content ") && visibleInScene(label)) {
                showing.add(label.getText());
            }
        }
        return tabs + " chosen=" + chosen + " showing=" + showing;
    }

    /** Whether {@code tab} is wholly inside what {@code row} shows. */
    private static boolean inSight(ScrollPane row, Node tab) {
        row.layout();
        Bounds shown = row.localToScene(row.getLayoutBounds());
        Bounds at = tab.localToScene(tab.getLayoutBounds());
        return at.getMinX() >= shown.getMinX() - 0.5 && at.getMaxX() <= shown.getMaxX() + 0.5;
    }

    private static boolean visibleInScene(Node node) {
        for (Node n = node; n != null; n = n.getParent()) {
            if (!n.isVisible()) {
                return false;
            }
        }
        return node.getScene() != null;
    }

    @Test
    void eachViewHasATabAndOnlyTheChosenOneShows() throws Exception {
        Assumptions.assumeTrue(FxTestSupport.isFxRuntimeAvailable());

        List<String> seen = onFx(() -> {
            SideDock dock = dockInWindow(300);
            List<String> chosen = new ArrayList<>();
            dock.setOnSelected(chosen::add);
            List<String> out = new ArrayList<>();
            out.add(state(dock));
            add(dock, "a");
            add(dock, "b");
            add(dock, "c");
            out.add(state(dock));
            click((Button) dock.lookupAll(".side-dock-tab").toArray()[2]);
            out.add(state(dock) + " told=" + chosen);
            return out;
        });

        assertEquals(List.of("[] chosen=null showing=[]",
                "[Title a, Title b, Title c] chosen=Title a showing=[content a]",
                "[Title a, Title b, Title c] chosen=Title c showing=[content c] told=[a, c]"), seen);
    }

    /** The view chosen last time is chosen again when it comes, even if it comes later (a plugin's). */
    @Test
    void aViewAskedForBeforeItExistsIsChosenWhenItComes() throws Exception {
        Assumptions.assumeTrue(FxTestSupport.isFxRuntimeAvailable());

        List<String> seen = onFx(() -> {
            SideDock dock = dockInWindow(300);
            dock.select("plugin");
            add(dock, "a");
            List<String> out = new ArrayList<>();
            out.add(state(dock));
            add(dock, "plugin");
            out.add(state(dock));
            return out;
        });

        assertEquals(List.of("[Title a] chosen=Title a showing=[content a]",
                "[Title a, Title plugin] chosen=Title plugin showing=[content plugin]"), seen);
    }

    @Test
    void removingTheChosenViewChoosesTheFirstAndAnEmptyPanelSaysSo() throws Exception {
        Assumptions.assumeTrue(FxTestSupport.isFxRuntimeAvailable());

        List<String> seen = onFx(() -> {
            SideDock dock = dockInWindow(300);
            dock.setEmptyText("Nothing here");
            add(dock, "a");
            add(dock, "b");
            dock.select("b");
            List<String> out = new ArrayList<>();
            dock.removeView("b");
            out.add(state(dock));
            dock.removeView("a");
            out.add(state(dock) + " empty=" + ((Label) dock.lookup(".side-dock-empty")).getText()
                    + " " + visibleInScene(dock.lookup(".side-dock-empty")));
            return out;
        });

        assertEquals(List.of("[Title a] chosen=Title a showing=[content a]",
                "[] chosen=null showing=[] empty=Nothing here true"), seen);
    }

    /** A view can be left out of the row for a while (a plugin hides its panels) and come back. */
    @Test
    void aHiddenViewLeavesTheRowAndTheChoiceMovesOff() throws Exception {
        Assumptions.assumeTrue(FxTestSupport.isFxRuntimeAvailable());

        List<String> seen = onFx(() -> {
            SideDock dock = dockInWindow(300);
            add(dock, "a");
            add(dock, "b");
            dock.select("b");
            List<String> out = new ArrayList<>();
            dock.setShown("b", false);
            out.add(state(dock) + " shown=" + dock.isShown("b"));
            dock.setShown("b", true);
            out.add(state(dock) + " shown=" + dock.isShown("b"));
            return out;
        });

        assertEquals(List.of("[Title a] chosen=Title a showing=[content a] shown=false",
                "[Title a, Title b] chosen=Title a showing=[content a] shown=true"), seen);
    }

    /** Tabs that do not fit: the row scrolls sideways with the wheel, has no bar, and the chosen tab is brought into sight. */
    @Test
    void tabsThatDoNotFitScrollSidewaysWithTheWheel() throws Exception {
        Assumptions.assumeTrue(FxTestSupport.isFxRuntimeAvailable());

        List<String> seen = onFx(() -> {
            SideDock dock = dockInWindow(120);
            for (int i = 0; i < 12; i++) {
                add(dock, "v" + i);
            }
            dock.applyCss();
            dock.layout();
            ScrollPane row = (ScrollPane) dock.lookup(".side-dock-tabs");
            List<String> out = new ArrayList<>();
            out.add("bar " + row.getHbarPolicy() + ", at " + row.getHvalue());
            Event.fireEvent(row, new ScrollEvent(ScrollEvent.SCROLL, 10, 10, 10, 10, false, false, false, false,
                    false, false, 0, -40, 0, 0, ScrollEvent.HorizontalTextScrollUnits.NONE, 0,
                    ScrollEvent.VerticalTextScrollUnits.NONE, 0, 0, null));
            out.add("wheel moved it right: " + (row.getHvalue() > 0));
            row.setHvalue(0);
            Node last = (Node) dock.lookupAll(".side-dock-tab").toArray()[11];
            out.add("the last is out of sight: " + !inSight(row, last));
            dock.select("v11");
            dock.layout();
            out.add("choosing it brings it in: " + inSight(row, last));
            return out;
        });

        assertEquals(List.of("bar NEVER, at 0.0", "wheel moved it right: true",
                "the last is out of sight: true", "choosing it brings it in: true"), seen);
    }

    @Test
    void theOrderOfTheTabsIsTheOrderTheViewsCameIn() throws Exception {
        Assumptions.assumeTrue(FxTestSupport.isFxRuntimeAvailable());

        List<String> ids = onFx(() -> {
            SideDock dock = dockInWindow(300);
            add(dock, "links");
            add(dock, "outgoing");
            add(dock, "plugin");
            dock.removeView("outgoing");
            add(dock, "outgoing");
            return dock.viewIds();
        });

        assertEquals(List.of("links", "plugin", "outgoing"), ids);
    }

    private static void click(Button button) {
        button.fire();
    }
}
