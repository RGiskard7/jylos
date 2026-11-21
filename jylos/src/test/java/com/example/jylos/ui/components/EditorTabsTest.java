package com.example.jylos.ui.components;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static com.example.jylos.tests.FxTestSupport.onFx;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import com.example.jylos.tests.FxTestSupport;
import com.example.jylos.ui.components.EditorTabsModel.OpenTarget;

import javafx.event.Event;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ScrollPane;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;

/** The rendered tab strip follows the tab rules and closes a tab on middle click. */
class EditorTabsTest {

    @Test
    void theStripShowsTheTabsInOrderWithTheActiveOneMarked() throws Exception {
        Assumptions.assumeTrue(FxTestSupport.isFxRuntimeAvailable());

        List<String> seen = onFx(() -> {
            HBox bar = new HBox();
            EditorTabs tabs = new EditorTabs(bar, bar, null, key -> key);
            List<String> out = new ArrayList<>();

            tabs.open("a", "Alpha", OpenTarget.CURRENT_TAB);
            tabs.open("b", "Beta", OpenTarget.NEW_TAB);
            out.add(render(bar));
            tabs.open("x", "Xenon", OpenTarget.CURRENT_TAB); // replaces Beta in place
            out.add(render(bar));
            tabs.open("a", "Alpha", OpenTarget.CURRENT_TAB); // already open: just activated
            out.add(render(bar));
            tabs.rebindId("x", "x2", "Xenon 2");
            out.add(render(bar));
            tabs.removeTab("a");
            out.add(render(bar) + " visible=" + bar.isVisible());
            tabs.clear();
            out.add(render(bar) + " visible=" + bar.isVisible());
            return out;
        });

        assertEquals(List.of(
                "Alpha, *Beta",
                "Alpha, *Xenon",
                "*Alpha, Xenon",
                "*Alpha, Xenon 2",
                "Xenon 2 visible=true",
                " visible=false"), seen);
    }

    @Test
    void aMiddleClickOnATabClosesItAndAPrimaryClickSelectsIt() throws Exception {
        Assumptions.assumeTrue(FxTestSupport.isFxRuntimeAvailable());

        List<String> events = new ArrayList<>();
        onFx(() -> {
            HBox bar = new HBox();
            EditorTabs tabs = new EditorTabs(bar, bar, new EditorTabs.Listener() {
                @Override public void onSelect(String noteId) { events.add("select " + noteId); }
                @Override public void onClose(String noteId) { events.add("close " + noteId); }
            }, key -> key);
            tabs.open("a", "Alpha", OpenTarget.CURRENT_TAB);
            tabs.open("b", "Beta", OpenTarget.NEW_TAB);

            click(bar.getChildren().get(0), MouseButton.MIDDLE);
            click(bar.getChildren().get(0), MouseButton.PRIMARY);
            click(bar.getChildren().get(1), MouseButton.PRIMARY); // already active: nothing
            return null;
        });

        assertEquals(List.of("close a", "select a"), events);
    }

    /**
     * With the largest interface font and a title too long for the tab, only the title gives
     * way: the unsaved-changes dot and the close button keep their symbols, not an ellipsis.
     * The strip sits where the app puts it, in the top bar, with the app's theme.
     */
    @Test
    void aLongTitleWithALargeFontEllipsisesTheTitleButKeepsTheSymbols() throws Exception {
        Assumptions.assumeTrue(FxTestSupport.isFxRuntimeAvailable());

        List<String> shown = onFx(() -> {
            HBox bar = new HBox();
            EditorTabs tabs = new EditorTabs(bar, bar, null, key -> key);
            tabs.open("a", "A very long note title that cannot possibly fit in one tab", OpenTarget.CURRENT_TAB);
            tabs.setDirty("a", true);
            ScrollPane strip = new ScrollPane(bar);
            strip.getStyleClass().add("editor-tab-scroll");
            strip.setFitToHeight(true);
            HBox tabsHost = new HBox(strip);
            tabsHost.getStyleClass().add("top-bar-tabs");
            HBox toolbar = new HBox(tabsHost);
            toolbar.getStyleClass().add("toolbar");
            VBox root = new VBox(toolbar);
            root.setStyle("-fx-font-size: 22px;");
            Scene scene = new Scene(root, 800, 100);
            scene.getStylesheets().add(EditorTabs.class
                    .getResource("/com/example/jylos/ui/css/dark-theme.css").toExternalForm());
            root.applyCss();
            root.layout();
            HBox tab = (HBox) bar.getChildren().get(0);
            return List.of(shownText(tab.lookup(".editor-tab-title")),
                    shownText(tab.lookup(".editor-tab-dot")) + wholeness(tab.lookup(".editor-tab-dot")),
                    shownText(tab.lookup(".editor-tab-close")) + wholeness(tab.lookup(".editor-tab-close")));
        });

        assertTrue(shown.get(0).endsWith("…") || shown.get(0).endsWith("..."), "the title is ellipsised: " + shown);
        assertEquals("● whole", shown.get(1), "the unsaved-changes dot keeps its ●, at full width");
        assertEquals("× whole", shown.get(2), "the close button keeps its ×, at full width");
    }

    /** " whole" when the node is laid out at least as wide as it asks to be, else its widths. */
    private static String wholeness(Node node) {
        double width = node.getLayoutBounds().getWidth();
        double needed = node.prefWidth(-1);
        return width + 0.5 >= needed ? " whole" : " " + width + " of " + needed;
    }

    /** The text a label actually draws, ellipsis included. */
    private static String shownText(Node label) {
        return label.lookup(".text") instanceof Text text ? text.getText() : "<none>";
    }

    /**
     * A tab's menu pins and unpins it, closes it, and closes the others but the pinned ones. A
     * pinned tab goes first and shows a pin, which unpins it, instead of the close cross.
     */
    @Test
    void theTabMenuPinsClosesAndClosesTheOthersButThePinned() throws Exception {
        Assumptions.assumeTrue(FxTestSupport.isFxRuntimeAvailable());

        List<String> seen = onFx(() -> {
            HBox bar = new HBox();
            EditorTabs[] holder = new EditorTabs[1];
            List<String> events = new ArrayList<>();
            holder[0] = new EditorTabs(bar, bar, new EditorTabs.Listener() {
                @Override public void onSelect(String noteId) {
                    events.add("select " + noteId);
                    holder[0].open(noteId, null, OpenTarget.CURRENT_TAB);
                }
                @Override public void onClose(String noteId) {
                    events.add("close " + noteId);
                    holder[0].removeTab(noteId);
                }
            }, key -> key);
            EditorTabs tabs = holder[0];
            tabs.open("a", "Alpha", OpenTarget.NEW_TAB);
            tabs.open("b", "Beta", OpenTarget.NEW_TAB);
            tabs.open("c", "Gamma", OpenTarget.NEW_TAB);
            tabs.open("d", "Delta", OpenTarget.NEW_TAB);
            List<String> out = new ArrayList<>();

            out.add(items(tabs.menuFor("b")));
            fire(tabs.menuFor("b"), "tab.pin");
            out.add(render(bar) + " pinned " + tabs.pinnedIds());
            HBox pinned = (HBox) bar.getChildren().get(0);
            out.add("pin " + pinned.lookup(".editor-tab-pin").isVisible() + " cross "
                    + pinned.lookup(".editor-tab-close").isVisible());
            out.add(items(tabs.menuFor("b")));

            fire(tabs.menuFor("c"), "tab.close_others");
            out.add(render(bar) + " " + events);

            click(pinned.lookup(".editor-tab-pin"), MouseButton.PRIMARY);
            out.add(render(bar) + " pinned " + tabs.pinnedIds());
            return out;
        });

        assertEquals(List.of(
                "tab.pin, tab.close, tab.close_others",
                "Beta, Alpha, Gamma, *Delta pinned [b]",
                "pin true cross false",
                "tab.unpin, tab.close, tab.close_others",
                "Beta, *Gamma [select c, close a, close d]",
                "Beta, *Gamma pinned []"), seen);
    }

    private static String items(ContextMenu menu) {
        return String.join(", ", menu.getItems().stream().map(MenuItem::getText).toList());
    }

    private static void fire(ContextMenu menu, String text) {
        menu.getItems().stream().filter(item -> text.equals(item.getText())).findFirst().orElseThrow().fire();
    }

    private static String render(HBox bar) {
        List<String> titles = new ArrayList<>();
        for (Node tab : bar.getChildren()) {
            String title = ((Label) ((HBox) tab).getChildren().get(1)).getText();
            titles.add((tab.getStyleClass().contains("active") ? "*" : "") + title);
        }
        return String.join(", ", titles);
    }

    private static void click(Node node, MouseButton button) {
        Event.fireEvent(node, new MouseEvent(MouseEvent.MOUSE_CLICKED, 1, 1, 1, 1, button, 1,
                false, false, false, false, button == MouseButton.PRIMARY, button == MouseButton.MIDDLE,
                false, false, false, true, null));
    }
}
