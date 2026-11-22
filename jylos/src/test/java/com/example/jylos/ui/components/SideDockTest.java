package com.example.jylos.ui.components;

import static com.example.jylos.tests.FxTestSupport.onFx;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import com.example.jylos.tests.FxTestSupport;

import javafx.event.Event;
import javafx.event.EventType;
import javafx.geometry.Bounds;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.CheckMenuItem;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.SplitPane;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
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

    /** Dragging a tab along the row moves it there, as Obsidian's; the new order is told (to keep it). */
    @Test
    void draggingATabMovesItAndTheNewOrderIsTold() throws Exception {
        Assumptions.assumeTrue(FxTestSupport.isFxRuntimeAvailable());

        List<String> seen = onFx(() -> {
            SideDock dock = dockInWindow(400);
            add(dock, "a");
            add(dock, "b");
            add(dock, "c");
            List<List<String>> told = new ArrayList<>();
            dock.setOnOrderChanged(told::add);
            List<String> out = new ArrayList<>();
            drag(dock, "a", "c");
            out.add(dock.viewIds().toString());
            drag(dock, "a", "b");
            out.add(dock.viewIds().toString());
            drag(dock, "b", "b"); // pressed and let go where it was: nothing moves
            out.add(dock.viewIds() + " told " + told);
            return out;
        });

        assertEquals(List.of("[b, c, a]", "[a, b, c]", "[a, b, c] told [[b, c, a], [a, b, c]]"), seen);
    }

    /** The order kept from last time places the views as they come, a plugin's too; an unknown one goes last. */
    @Test
    void theViewsComeInTheOrderKeptFromLastTime() throws Exception {
        Assumptions.assumeTrue(FxTestSupport.isFxRuntimeAvailable());

        List<String> ids = onFx(() -> {
            SideDock dock = dockInWindow(300);
            dock.setPreferredOrder(List.of("plugin", "b", "a"));
            add(dock, "a");
            add(dock, "b");
            add(dock, "z");
            add(dock, "plugin");
            return dock.viewIds();
        });

        assertEquals(List.of("plugin", "b", "a", "z"), ids);
    }

    /**
     * The tabs' menu closes a view (its tab leaves the row) and lists every view, to open a
     * closed one again; what is closed is told (to keep it) and a view closed last time stays
     * closed when it comes.
     */
    @Test
    void aViewIsClosedFromItsTabsMenuAndOpenedAgainFromTheList() throws Exception {
        Assumptions.assumeTrue(FxTestSupport.isFxRuntimeAvailable());

        List<String> seen = onFx(() -> {
            SideDock dock = dockInWindow(300);
            dock.setCloseText("Close");
            dock.setSplitText("Split down");
            dock.setClosed("plugin", true); // closed last time, before it comes
            add(dock, "a");
            add(dock, "b");
            add(dock, "plugin");
            List<String> told = new ArrayList<>();
            dock.setOnClosedChanged(closed -> told.add(closed.toString()));
            dock.select("b");
            List<String> out = new ArrayList<>();
            out.add(menuTexts(dock.menuFor("b")) + " " + state(dock));
            dock.menuFor("b").getItems().get(0).fire(); // Close
            out.add(state(dock) + " closed=" + dock.isClosed("b"));
            ((CheckMenuItem) dock.menuFor(null).getItems().stream()
                    .filter(i -> "Title plugin".equals(i.getText())).findFirst().orElseThrow()).fire();
            out.add(state(dock) + " told " + told);
            return out;
        });

        assertEquals(List.of(
                "[Close, Split down, -, [x] Title a, [x] Title b, [ ] Title plugin] [Title a, Title b] chosen=Title b showing=[content b]",
                "[Title a] chosen=Title a showing=[content a] closed=true",
                "[Title a, Title plugin] chosen=Title plugin showing=[content plugin] told [[b, plugin], [b]]"), seen);
    }

    /** A view's own bar of actions sits above its content and does not scroll with it; it shows with its view. */
    @Test
    void aViewsActionsBarShowsWithItAboveWhatScrolls() throws Exception {
        Assumptions.assumeTrue(FxTestSupport.isFxRuntimeAvailable());

        List<String> seen = onFx(() -> {
            SideDock dock = dockInWindow(300);
            Label actions = new Label("actions a");
            dock.addView("a", "Title a", new Label("icon"), new Label("content a"), actions);
            add(dock, "b");
            List<String> out = new ArrayList<>();
            boolean scrolled = false;
            for (Node n = actions; n != null; n = n.getParent()) {
                scrolled |= n instanceof ScrollPane;
            }
            out.add("in sight " + visibleInScene(actions) + ", scrolls " + scrolled);
            dock.select("b");
            out.add("in sight " + visibleInScene(actions));
            return out;
        });

        assertEquals(List.of("in sight true, scrolls false", "in sight false"), seen);
    }

    // ── Groups: the panel split in rows of tabs, one over the other ──────────

    /** What each group shows, top to bottom: its tabs, the one chosen and the view showing. */
    private static List<String> groups(SideDock dock) {
        dock.applyCss();
        dock.layout();
        List<String> out = new ArrayList<>();
        for (Node group : dock.lookupAll(".side-dock-group")) {
            if (!visibleInScene(group)) {
                continue;
            }
            List<String> tabs = new ArrayList<>();
            String chosen = null;
            for (Node node : group.lookupAll(".side-dock-tab")) {
                Button tab = (Button) node;
                if (tab.isVisible()) {
                    tabs.add(tab.getTooltip().getText());
                    if (tab.getStyleClass().contains("sidebar-nav-btn-selected")) {
                        chosen = tab.getTooltip().getText();
                    }
                }
            }
            List<String> showing = new ArrayList<>();
            for (Node node : group.lookupAll(".label")) {
                if (node instanceof Label label && label.getText().startsWith("content ") && visibleInScene(label)) {
                    showing.add(label.getText());
                }
            }
            out.add(tabs + " chosen=" + chosen + " showing=" + showing);
        }
        return out;
    }

    private static MenuItem splitItem(SideDock dock, String id) {
        return dock.menuFor(id).getItems().stream().filter(i -> "Split down".equals(i.getText())).findFirst()
                .orElseThrow();
    }

    /** "Split down" in a tab's menu puts its view in a group of its own under its group, as Obsidian's. */
    @Test
    void splittingAViewDownPutsItInAGroupOfItsOwnBelow() throws Exception {
        Assumptions.assumeTrue(FxTestSupport.isFxRuntimeAvailable());

        List<Object> seen = onFx(() -> {
            SideDock dock = dockInWindow(300);
            dock.setSplitText("Split down");
            add(dock, "a");
            add(dock, "b");
            add(dock, "c");
            List<Map<String, Integer>> told = new ArrayList<>();
            dock.setOnGroupsChanged(told::add);
            dock.select("b");
            List<Object> out = new ArrayList<>();
            splitItem(dock, "b").fire();
            out.add(groups(dock));
            out.add("shown " + dock.shown() + ", b alone can split: " + !splitItem(dock, "b").isDisable());
            splitItem(dock, "c").fire(); // under its group, over b's
            out.add(groups(dock));
            out.add("told " + told);
            return out;
        });

        assertEquals(List.of(
                List.of("[Title a, Title c] chosen=Title a showing=[content a]",
                        "[Title b] chosen=Title b showing=[content b]"),
                "shown [a, b], b alone can split: false",
                List.of("[Title a] chosen=Title a showing=[content a]", "[Title c] chosen=Title c showing=[content c]",
                        "[Title b] chosen=Title b showing=[content b]"),
                "told [{a=0, b=1, c=0}, {a=0, b=2, c=1}]"), seen);
    }

    /** The line between two groups is a strip a few pixels tall, so the mouse can take it, in both themes. */
    @Test
    void theLineBetweenGroupsCanBeTakenWithTheMouse() throws Exception {
        Assumptions.assumeTrue(FxTestSupport.isFxRuntimeAvailable());

        List<Double> heights = onFx(() -> {
            List<Double> out = new ArrayList<>();
            for (String theme : List.of("dark-theme.css", "modern-theme.css")) {
                SideDock dock = dockInWindow(300);
                dock.getScene().getStylesheets()
                        .add(SideDock.class.getResource("/com/example/jylos/ui/css/" + theme).toExternalForm());
                add(dock, "a");
                add(dock, "b");
                dock.splitDown("b");
                dock.applyCss();
                dock.layout();
                Node line = dock.lookup(".side-dock-split > .split-pane-divider");
                out.add(line.localToScene(line.getLayoutBounds()).getHeight());
            }
            return out;
        });

        for (double height : heights) {
            assertTrue(height >= 5 && height <= 10, "the strip is " + heights);
        }
    }

    /** A group whose views are all closed goes; a view opened again comes back to its group. */
    @Test
    void aGroupLeftWithoutViewsGoesAndAViewOpenedAgainReturnsToIt() throws Exception {
        Assumptions.assumeTrue(FxTestSupport.isFxRuntimeAvailable());

        List<Object> seen = onFx(() -> {
            SideDock dock = dockInWindow(300);
            add(dock, "a");
            add(dock, "b");
            dock.splitDown("b");
            List<Object> out = new ArrayList<>();
            dock.setClosed("b", true);
            out.add(groups(dock));
            dock.setClosed("b", false);
            out.add(groups(dock));
            return out;
        });

        assertEquals(List.of(List.of("[Title a] chosen=Title a showing=[content a]"),
                List.of("[Title a] chosen=Title a showing=[content a]", "[Title b] chosen=Title b showing=[content b]")),
                seen);
    }

    /**
     * A tab's menu moves its view to the group above or below, at its end and chosen there,
     * without dragging; each entry is there only when there is a group that way.
     */
    @Test
    void aViewMovesToTheGroupAboveOrBelowFromItsTabsMenu() throws Exception {
        Assumptions.assumeTrue(FxTestSupport.isFxRuntimeAvailable());

        List<Object> seen = onFx(() -> {
            SideDock dock = dockInWindow(300);
            dock.setCloseText("Close");
            dock.setSplitText("Split down");
            dock.setMoveTexts("Move up", "Move down");
            add(dock, "a");
            add(dock, "b");
            add(dock, "c");
            List<Object> out = new ArrayList<>();
            out.add("one group: " + menuTexts(dock.menuFor("a")));
            dock.splitDown("c");
            out.add("a: " + menuTexts(dock.menuFor("a")));
            out.add("c: " + menuTexts(dock.menuFor("c")));
            List<String> told = new ArrayList<>();
            dock.setOnGroupsChanged(groups -> told.add("groups " + groups));
            dock.setOnOrderChanged(order -> told.add("order " + order));
            moveItem(dock, "c", "Move up").fire(); // its group goes
            out.add(groups(dock));
            dock.splitDown("a");
            moveItem(dock, "b", "Move down").fire();
            out.add(groups(dock));
            out.add(told);
            return out;
        });

        assertEquals(List.of(
                "one group: [Close, Split down, -, [x] Title a, [x] Title b, [x] Title c]",
                "a: [Close, Split down, Move down, -, [x] Title a, [x] Title b, [x] Title c]",
                "c: [Close, Split down, Move up, -, [x] Title a, [x] Title b, [x] Title c]",
                List.of("[Title a, Title b, Title c] chosen=Title c showing=[content c]"),
                List.of("[Title c] chosen=Title c showing=[content c]",
                        "[Title a, Title b] chosen=Title b showing=[content b]"),
                List.of("order [a, b, c]", "groups {a=0, b=0, c=0}", "groups {a=1, b=0, c=0}", "order [a, b, c]",
                        "groups {a=1, b=1, c=0}")),
                seen);
    }

    private static MenuItem moveItem(SideDock dock, String id, String text) {
        return dock.menuFor(id).getItems().stream().filter(i -> text.equals(i.getText())).findFirst().orElseThrow();
    }

    /** A tab dropped on another group's row goes to that group, before the tab it is dropped on. */
    @Test
    void draggingATabOntoAnotherGroupsRowMovesItThere() throws Exception {
        Assumptions.assumeTrue(FxTestSupport.isFxRuntimeAvailable());

        List<Object> seen = onFx(() -> {
            SideDock dock = dockInWindow(300);
            add(dock, "a");
            add(dock, "b");
            add(dock, "c");
            dock.splitDown("c");
            List<String> told = new ArrayList<>();
            dock.setOnGroupsChanged(groups -> told.add("groups " + groups));
            dock.setOnOrderChanged(order -> told.add("order " + order));
            List<Object> out = new ArrayList<>();
            dragOnto(dock, "a", "c");
            out.add(groups(dock));
            dragOnto(dock, "b", "a"); // the last of its group: the group goes
            out.add(groups(dock));
            out.add(told);
            return out;
        });

        assertEquals(List.of(
                List.of("[Title b] chosen=Title b showing=[content b]",
                        "[Title a, Title c] chosen=Title a showing=[content a]"),
                List.of("[Title b, Title a, Title c] chosen=Title b showing=[content b]"),
                List.of("order [b, a, c]", "groups {a=1, b=0, c=1}", "order [b, a, c]", "groups {a=1, b=1, c=1}")),
                seen);
    }

    /** The groups and how tall each is come from last time; a divider let go is told. */
    @Test
    void theGroupsAndTheirHeightsComeFromLastTime() throws Exception {
        Assumptions.assumeTrue(FxTestSupport.isFxRuntimeAvailable());

        List<Object> seen = onFx(() -> {
            SideDock dock = new SideDock();
            dock.setPreferredGroups(Map.of("b", 1));
            dock.setPreferredDividers(List.of(0.3));
            Stage stage = new Stage();
            stage.setScene(new Scene(dock, 300, 400));
            stage.show();
            add(dock, "a");
            add(dock, "b");
            add(dock, "c");
            dock.select("c");
            List<Object> out = new ArrayList<>();
            out.add(groups(dock));
            out.add("shown " + dock.shown());
            SplitPane split = (SplitPane) dock.lookup(".side-dock-split");
            out.add(Math.round(split.getDividerPositions()[0] * 100) / 100.0);
            List<List<Double>> told = new ArrayList<>();
            dock.setOnDividersChanged(told::add);
            split.setDividerPosition(0, 0.6);
            mouse(split, MouseEvent.MOUSE_RELEASED, 10, 10);
            out.add(told.stream().map(l -> l.stream().map(d -> Math.round(d * 100) / 100.0).toList()).toList());
            return out;
        });

        assertEquals(List.of(
                List.of("[Title a, Title c] chosen=Title c showing=[content c]",
                        "[Title b] chosen=Title b showing=[content b]"),
                "shown [c, b]", 0.3, List.of(List.of(0.6))), seen);
    }

    /**
     * A tab dropped on the top or the bottom of a group's view makes a group of its own above or
     * below that group, as Obsidian's; on its middle, it joins that group, at its end.
     */
    @Test
    void aTabDroppedOnAGroupsEdgeMakesAGroupThereAndOnItsMiddleJoinsIt() throws Exception {
        Assumptions.assumeTrue(FxTestSupport.isFxRuntimeAvailable());

        List<Object> seen = onFx(() -> {
            SideDock dock = dockInWindow(300);
            add(dock, "a");
            add(dock, "b");
            add(dock, "c");
            dock.splitDown("c");
            List<String> told = new ArrayList<>();
            dock.setOnGroupsChanged(groups -> told.add("groups " + groups));
            dock.setOnOrderChanged(order -> told.add("order " + order));
            List<Object> out = new ArrayList<>();
            dropOn(dock, "a", 1, 0.1); // on the top of c's group
            out.add(groups(dock));
            dropOn(dock, "b", 2, 0.9); // the last of its group, on the bottom of c's: its group goes
            out.add(groups(dock));
            dropOn(dock, "b", 0, 0.5); // on the middle of a's group
            out.add(groups(dock));
            out.add(told);
            return out;
        });

        assertEquals(List.of(
                List.of("[Title b] chosen=Title b showing=[content b]",
                        "[Title a] chosen=Title a showing=[content a]",
                        "[Title c] chosen=Title c showing=[content c]"),
                List.of("[Title a] chosen=Title a showing=[content a]",
                        "[Title c] chosen=Title c showing=[content c]",
                        "[Title b] chosen=Title b showing=[content b]"),
                List.of("[Title a, Title b] chosen=Title b showing=[content b]",
                        "[Title c] chosen=Title c showing=[content c]"),
                List.of("order [a, b, c]", "groups {a=1, b=0, c=2}", "order [a, b, c]", "groups {a=1, b=3, c=2}",
                        "order [a, b, c]", "groups {a=1, b=1, c=2}")),
                seen);
    }

    /** Dropped on the edge of its own group, a tab splits it; alone in it, nothing happens. */
    @Test
    void aTabDroppedOnItsOwnGroupsEdgeSplitsItUnlessItIsAlone() throws Exception {
        Assumptions.assumeTrue(FxTestSupport.isFxRuntimeAvailable());

        List<Object> seen = onFx(() -> {
            SideDock dock = dockInWindow(300);
            add(dock, "a");
            add(dock, "b");
            add(dock, "c");
            List<Object> out = new ArrayList<>();
            dropOn(dock, "b", 0, 0.9);
            out.add(groups(dock));
            List<String> told = new ArrayList<>();
            dock.setOnGroupsChanged(groups -> told.add("groups " + groups));
            dock.setOnOrderChanged(order -> told.add("order " + order));
            dropOn(dock, "b", 1, 0.1); // alone: its own top edge
            dropOn(dock, "a", 0, 0.5); // the middle of its own group
            out.add(groups(dock));
            out.add("told " + told);
            return out;
        });

        List<String> split = List.of("[Title a, Title c] chosen=Title a showing=[content a]",
                "[Title b] chosen=Title b showing=[content b]");
        assertEquals(List.of(split, split, "told []"), seen);
    }

    /**
     * While a tab is dragged nothing moves: a mark shows where it would go, over the half of the
     * group it would make a group in, or as a line between the tabs of the row it would join. Let
     * go away from the panel, nothing moves and the mark goes; over its own tab there is none.
     */
    @Test
    void whileATabIsDraggedAMarkShowsWhereItWouldGo() throws Exception {
        Assumptions.assumeTrue(FxTestSupport.isFxRuntimeAvailable());

        List<Object> seen = onFx(() -> {
            SideDock dock = dockInWindow(300);
            add(dock, "a");
            add(dock, "b");
            add(dock, "c");
            dock.splitDown("c");
            dock.applyCss();
            dock.layout();
            List<String> before = List.of(dock.viewIds().toString(), groups(dock).toString());
            List<Object> out = new ArrayList<>();
            Node source = tabOf(dock, "a");
            Bounds start = source.localToScene(source.getLayoutBounds());
            mouse(source, MouseEvent.MOUSE_PRESSED, start.getCenterX(), start.getCenterY());
            mouse(source, MouseEvent.MOUSE_DRAGGED, start.getCenterX() + 2, start.getCenterY() + 1);
            out.add("own tab: " + marks(dock));

            Bounds lower = groupViews(dock, 1);
            mouse(source, MouseEvent.MOUSE_DRAGGED, lower.getCenterX(), lower.getMinY() + lower.getHeight() * 0.1);
            Bounds zone = shownBounds(dock, ".side-dock-drop-zone");
            out.add("top of c's group: " + marks(dock) + ", over its top half: "
                    + (near(zone.getMinY(), lower.getMinY()) && near(zone.getHeight(), lower.getHeight() / 2)
                            && near(zone.getMinX(), lower.getMinX()) && near(zone.getWidth(), lower.getWidth())));
            mouse(source, MouseEvent.MOUSE_DRAGGED, lower.getCenterX(), lower.getMinY() + lower.getHeight() * 0.5);
            zone = shownBounds(dock, ".side-dock-drop-zone");
            out.add("middle: " + marks(dock) + ", over it all: "
                    + (near(zone.getMinY(), lower.getMinY()) && near(zone.getHeight(), lower.getHeight())));
            Bounds c = tabOf(dock, "c").localToScene(tabOf(dock, "c").getLayoutBounds());
            mouse(source, MouseEvent.MOUSE_DRAGGED, c.getMinX() + 2, c.getCenterY());
            Bounds line = shownBounds(dock, ".side-dock-drop-line");
            out.add("c's row: " + marks(dock) + ", before c: "
                    + (line.getMaxX() <= c.getMinX() + 2 && line.getMinX() >= c.getMinX() - 8 && line.getWidth() > 0
                            && line.getHeight() > 0));
            out.add("nothing moved: " + List.of(dock.viewIds().toString(), groups(dock).toString()).equals(before));
            mouse(source, MouseEvent.MOUSE_DRAGGED, -20, -20);
            out.add("away: " + marks(dock));
            mouse(source, MouseEvent.MOUSE_RELEASED, -20, -20);
            dock.layout();
            out.add("let go away, nothing moved: "
                    + List.of(dock.viewIds().toString(), groups(dock).toString()).equals(before) + " " + marks(dock));
            return out;
        });

        assertEquals(List.of("own tab: []", "top of c's group: [zone], over its top half: true",
                "middle: [zone], over it all: true", "c's row: [line], before c: true", "nothing moved: true",
                "away: []", "let go away, nothing moved: true []"), seen);
    }

    /**
     * A view the owner marks as disposable is not listed to open again: closing it takes it away,
     * and the owner is told. The owner can put a view in a new group under another's, and add its
     * own entries to a tab's menu, after the panel's.
     */
    @Test
    void aDisposableViewGoesWhenClosedAndTheOwnerAddsViewsBelowAndMenuEntries() throws Exception {
        Assumptions.assumeTrue(FxTestSupport.isFxRuntimeAvailable());

        List<Object> seen = onFx(() -> {
            SideDock dock = dockInWindow(300);
            dock.setSplitText("Split down");
            dock.setMoveTexts("Move to the group above", "Move to the group below");
            add(dock, "a");
            add(dock, "b");
            dock.setMenuExtras(id -> "a".equals(id) ? List.of(new MenuItem("Pin a copy")) : List.of());
            dock.addViewInNewGroupBelow("a", "a@1", "Title a@1", new Label("icon"), new Label("content a@1"), null);
            dock.setDisposable("a@1");
            List<String> discarded = new ArrayList<>();
            dock.setOnDiscarded(discarded::add);
            List<Object> out = new ArrayList<>();
            out.add(groups(dock));
            out.add("shown " + dock.shown());
            out.add(menuTexts(dock.menuFor("a")));
            out.add(menuTexts(dock.menuFor("a@1")));
            dock.menuFor("a@1").getItems().get(0).fire(); // Close
            out.add(groups(dock));
            out.add("ids " + dock.viewIds() + " discarded " + discarded + " closed " + dock.isClosed("a@1"));
            return out;
        });

        assertEquals(List.of(
                List.of("[Title a, Title b] chosen=Title a showing=[content a]",
                        "[Title a@1] chosen=Title a@1 showing=[content a@1]"),
                "shown [a, a@1]",
                "[Close, Split down, Move to the group below, Pin a copy, -, [x] Title a, [x] Title b]",
                "[Close, Split down, Move to the group above, -, [x] Title a, [x] Title b]",
                List.of("[Title a, Title b] chosen=Title a showing=[content a]"),
                "ids [a, b] discarded [a@1] closed false"), seen);
    }

    /** The tab of a disposable view (a copy, whose icon carries a mark) is wider than the others, in both themes. */
    @Test
    void aDisposableViewsTabIsWider() throws Exception {
        Assumptions.assumeTrue(FxTestSupport.isFxRuntimeAvailable());

        List<Double> widths = onFx(() -> {
            List<Double> out = new ArrayList<>();
            for (String theme : List.of("dark-theme.css", "modern-theme.css")) {
                SideDock dock = dockInWindow(300);
                dock.getScene().getStylesheets()
                        .add(SideDock.class.getResource("/com/example/jylos/ui/css/" + theme).toExternalForm());
                add(dock, "a");
                add(dock, "b");
                dock.setDisposable("b");
                dock.applyCss();
                dock.layout();
                out.add(((Button) tabOf(dock, "a")).getWidth());
                out.add(((Button) tabOf(dock, "b")).getWidth());
            }
            return out;
        });

        for (int i = 0; i < widths.size(); i += 2) {
            assertTrue(widths.get(i) >= 24 && widths.get(i) <= 32 && widths.get(i + 1) >= 36 && widths.get(i + 1) <= 48,
                    "a tab, a disposable one: " + widths);
        }
    }

    /** Drags the tab of {@code from} onto group {@code group} (shown, top to bottom) at {@code fraction} of its view's height, and lets go. */
    private static void dropOn(SideDock dock, String from, int group, double fraction) {
        dock.applyCss();
        dock.layout();
        Node source = tabOf(dock, from);
        Bounds start = source.localToScene(source.getLayoutBounds());
        Bounds views = groupViews(dock, group);
        double x = views.getCenterX();
        double y = views.getMinY() + views.getHeight() * fraction;
        mouse(source, MouseEvent.MOUSE_PRESSED, start.getCenterX(), start.getCenterY());
        mouse(source, MouseEvent.MOUSE_DRAGGED, x, y);
        mouse(source, MouseEvent.MOUSE_RELEASED, x, y);
        dock.layout();
    }

    /** Where the view of the {@code index}th group shown is, in the scene. */
    private static Bounds groupViews(SideDock dock, int index) {
        List<Node> shown = new ArrayList<>();
        for (Node group : dock.lookupAll(".side-dock-group")) {
            if (visibleInScene(group)) {
                shown.add(group.lookup(".side-dock-views"));
            }
        }
        Node views = shown.get(index);
        return views.localToScene(views.getLayoutBounds());
    }

    /** The marks showing: "zone", "line". */
    private static List<String> marks(SideDock dock) {
        List<String> out = new ArrayList<>();
        for (String mark : List.of("zone", "line")) {
            Node node = dock.lookup(".side-dock-drop-" + mark);
            if (node != null && node.isVisible()) {
                out.add(mark);
            }
        }
        return out;
    }

    private static Bounds shownBounds(SideDock dock, String selector) {
        Node node = dock.lookup(selector);
        return node.localToScene(node.getLayoutBounds());
    }

    private static boolean near(double a, double b) {
        return Math.abs(a - b) < 1.5;
    }

    /** Presses on the tab of {@code from}, drags onto the tab of {@code to} (in any group) and lets go. */
    private static void dragOnto(SideDock dock, String from, String to) {
        dock.applyCss();
        dock.layout();
        Node source = tabOf(dock, from);
        Bounds start = source.localToScene(source.getLayoutBounds());
        Bounds end = tabOf(dock, to).localToScene(tabOf(dock, to).getLayoutBounds());
        mouse(source, MouseEvent.MOUSE_PRESSED, start.getCenterX(), start.getCenterY());
        mouse(source, MouseEvent.MOUSE_DRAGGED, end.getCenterX(), end.getCenterY());
        mouse(source, MouseEvent.MOUSE_RELEASED, end.getCenterX(), end.getCenterY());
        dock.layout();
    }

    private static String menuTexts(ContextMenu menu) {
        List<String> out = new ArrayList<>();
        for (MenuItem item : menu.getItems()) {
            if (item instanceof SeparatorMenuItem) {
                out.add("-");
            } else if (item instanceof CheckMenuItem check) {
                out.add((check.isSelected() ? "[x] " : "[ ] ") + check.getText());
            } else {
                out.add(item.getText());
            }
        }
        return out.toString();
    }

    /** Presses on the tab of {@code from}, drags to the middle of the tab of {@code to} and lets go. */
    private static void drag(SideDock dock, String from, String to) {
        dock.applyCss();
        dock.layout();
        Node source = tabOf(dock, from);
        Bounds start = source.localToScene(source.getLayoutBounds());
        Bounds end = tabOf(dock, to).localToScene(tabOf(dock, to).getLayoutBounds());
        double y = start.getCenterY();
        mouse(source, MouseEvent.MOUSE_PRESSED, start.getCenterX(), y);
        mouse(source, MouseEvent.MOUSE_DRAGGED, (start.getCenterX() + end.getCenterX()) / 2, y);
        mouse(source, MouseEvent.MOUSE_DRAGGED, end.getCenterX(), y);
        mouse(source, MouseEvent.MOUSE_RELEASED, end.getCenterX(), y);
        dock.layout();
    }

    private static Node tabOf(SideDock dock, String id) {
        for (Node node : dock.lookupAll(".side-dock-tab")) {
            if (((Button) node).getTooltip().getText().equals("Title " + id)) {
                return node;
            }
        }
        throw new AssertionError("no tab " + id);
    }

    private static void mouse(Node target, EventType<MouseEvent> type, double sceneX, double sceneY) {
        Event.fireEvent(target, new MouseEvent(type, sceneX, sceneY, sceneX, sceneY, MouseButton.PRIMARY, 1,
                false, false, false, false, type != MouseEvent.MOUSE_RELEASED, false, false, false, false,
                false, null));
    }

    private static void click(Button button) {
        button.fire();
    }
}
