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
import javafx.scene.control.Label;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;

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
