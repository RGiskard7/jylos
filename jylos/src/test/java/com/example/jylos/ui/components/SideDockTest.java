package com.example.jylos.ui.components;

import static com.example.jylos.tests.FxTestSupport.onFx;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;

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
                "[Close, -, [x] Title a, [x] Title b, [ ] Title plugin] [Title a, Title b] chosen=Title b showing=[content b]",
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
