package com.example.jylos.ui.controller;

import static com.example.jylos.tests.FxTestSupport.onFx;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.prefs.Preferences;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import com.example.jylos.data.models.Note;
import com.example.jylos.event.events.SystemActionEvent.ActionType;
import com.example.jylos.service.BacklinkService.OutgoingLink;
import com.example.jylos.service.LinkOccurrences.Occurrence;
import com.example.jylos.tests.FxTestSupport;
import com.example.jylos.ui.components.LinksPanel;
import com.example.jylos.ui.components.SideDock;

import javafx.event.Event;
import javafx.event.EventType;
import javafx.geometry.Bounds;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.stage.Stage;

/**
 * The right panel's own views, the backlinks and the outgoing links, as Obsidian's; the view
 * chosen is remembered for the next start (a plugin's too, chosen when the plugin comes), and
 * the links are worked out only while one of their views is in sight.
 */
class RightPanelSupportTest {

    /** Links views that do nothing (what is tested here is the panel). */
    private static LinksPanel links() {
        return new LinksPanel(key -> key, new LinksPanel.Actions() {
            @Override
            public void openNote(Note note) {
            }

            @Override
            public void openOutgoing(OutgoingLink link) {
            }

            @Override
            public void linkMention(Note source, Occurrence mention) {
            }

            @Override
            public void unlinkedRequested() {
            }
        });
    }

    private static Preferences freshPrefs() throws Exception {
        Preferences prefs = Preferences.userRoot().node("jylos-test-right-panel-" + System.nanoTime());
        prefs.clear();
        return prefs;
    }

    @Test
    void theLinksViewsComeFirstAndTheChosenViewIsRemembered() throws Exception {
        Assumptions.assumeTrue(FxTestSupport.isFxRuntimeAvailable());
        Preferences prefs = freshPrefs();

        List<String> seen = onFx(() -> {
            List<String> out = new ArrayList<>();
            SideDock dock = new SideDock();
            new RightPanelSupport().wire(dock, prefs, key -> key, links(), () -> true, () -> {
            }, () -> {
            });
            out.add(dock.viewIds() + " " + dock.selected().orElse(null));
            dock.select(RightPanelSupport.OUTGOING);
            out.add(prefs.get(RightPanelSupport.VIEW_KEY, null));

            // Next start: the outgoing links again.
            SideDock next = new SideDock();
            new RightPanelSupport().wire(next, prefs, key -> key, links(), () -> true, () -> {
            }, () -> {
            });
            out.add(next.selected().orElse(null));

            // A plugin's view chosen last time is chosen when the plugin comes.
            next.addView("plugin:calendar:panel", "Calendar", new Label("c"), new Label("c"));
            next.select("plugin:calendar:panel");
            SideDock third = new SideDock();
            new RightPanelSupport().wire(third, prefs, key -> key, links(), () -> true, () -> {
            }, () -> {
            });
            out.add(third.selected().orElse(null));
            third.addView("plugin:calendar:panel", "Calendar", new Label("c"), new Label("c"));
            out.add(third.selected().orElse(null));
            return out;
        });

        assertEquals(List.of("[backlinks, outgoing-links] backlinks", "outgoing-links", "outgoing-links",
                RightPanelSupport.BACKLINKS, "plugin:calendar:panel"), seen);
        prefs.removeNode();
    }

    /** The order of the tabs and the views closed are kept for the next start, a plugin's too. */
    @Test
    void theOrderAndTheClosedViewsAreKept() throws Exception {
        Assumptions.assumeTrue(FxTestSupport.isFxRuntimeAvailable());
        Preferences prefs = freshPrefs();
        prefs.put(RightPanelSupport.ORDER_KEY, "plugin:calendar:panel\noutgoing-links\nbacklinks");

        List<String> seen = onFx(() -> {
            List<String> out = new ArrayList<>();
            SideDock dock = new SideDock();
            new RightPanelSupport().wire(dock, prefs, key -> key, links(), () -> true,
                    () -> {
                    }, () -> {
                    });
            dock.addView("plugin:calendar:panel", "Calendar", new Label("c"), new Label("c"));
            out.add(dock.viewIds().toString());
            // Dragging the first tab onto the last one.
            Stage stage = new Stage();
            stage.setScene(new Scene(dock, 300, 300));
            stage.show();
            dock.applyCss();
            dock.layout();
            List<Node> tabs = new ArrayList<>(dock.lookupAll(".side-dock-tab"));
            Bounds from = tabs.get(0).localToScene(tabs.get(0).getLayoutBounds());
            Bounds to = tabs.get(2).localToScene(tabs.get(2).getLayoutBounds());
            mouse(tabs.get(0), MouseEvent.MOUSE_PRESSED, from.getCenterX(), from.getCenterY());
            mouse(tabs.get(0), MouseEvent.MOUSE_DRAGGED, to.getCenterX(), from.getCenterY());
            mouse(tabs.get(0), MouseEvent.MOUSE_RELEASED, to.getCenterX(), from.getCenterY());
            out.add(prefs.get(RightPanelSupport.ORDER_KEY, null).replace("\n", ","));
            stage.close();
            dock.setClosed("plugin:calendar:panel", true);
            out.add(prefs.get(RightPanelSupport.CLOSED_KEY, null));

            SideDock next = new SideDock();
            new RightPanelSupport().wire(next, prefs, key -> key, links(), () -> true,
                    () -> {
                    }, () -> {
                    });
            next.addView("plugin:calendar:panel", "Calendar", new Label("c"), new Label("c"));
            out.add("closed again: " + next.isClosed("plugin:calendar:panel"));
            return out;
        });

        assertEquals(List.of("[plugin:calendar:panel, outgoing-links, backlinks]",
                "outgoing-links,backlinks,plugin:calendar:panel", "plugin:calendar:panel", "closed again: true"), seen);
        prefs.removeNode();
    }

    /** "Show backlinks" (or outgoing links): opens the panel if it is closed, and the view, even a closed one. */
    @Test
    void showingALinksViewOpensThePanelAndTheView() throws Exception {
        Assumptions.assumeTrue(FxTestSupport.isFxRuntimeAvailable());
        Preferences prefs = freshPrefs();
        AtomicBoolean open = new AtomicBoolean(false);

        List<String> seen = onFx(() -> {
            List<String> out = new ArrayList<>();
            SideDock dock = new SideDock();
            RightPanelSupport support = new RightPanelSupport();
            support.wire(dock, prefs, key -> key, links(), open::get, () -> {
            }, () -> open.set(!open.get()));
            dock.setClosed(RightPanelSupport.OUTGOING, true);
            Map<ActionType, Runnable> handlers = new EnumMap<>(ActionType.class);
            support.register(handlers);
            handlers.get(ActionType.SHOW_OUTGOING_LINKS).run();
            out.add("open " + open.get() + ", " + dock.selected().orElse(null) + ", closed "
                    + dock.isClosed(RightPanelSupport.OUTGOING));
            handlers.get(ActionType.SHOW_BACKLINKS).run();
            out.add("open " + open.get() + ", " + dock.selected().orElse(null));
            return out;
        });

        assertEquals(List.of("open true, outgoing-links, closed false", "open true, backlinks"), seen);
        prefs.removeNode();
    }

    private static void mouse(Node target, EventType<MouseEvent> type, double x, double y) {
        Event.fireEvent(target, new MouseEvent(type, x, y, x, y, MouseButton.PRIMARY, 1, false, false, false, false,
                type != MouseEvent.MOUSE_RELEASED, false, false, false, false, false, null));
    }

    @Test
    void theLinksAreWorkedOutOnlyWhileOneOfTheirViewsIsInSight() throws Exception {
        Assumptions.assumeTrue(FxTestSupport.isFxRuntimeAvailable());
        Preferences prefs = freshPrefs();
        AtomicBoolean open = new AtomicBoolean(false);

        List<String> seen = onFx(() -> {
            List<String> out = new ArrayList<>();
            List<String> refreshed = new ArrayList<>();
            SideDock dock = new SideDock();
            RightPanelSupport support = new RightPanelSupport();
            support.wire(dock, prefs, key -> key, links(), open::get,
                    () -> refreshed.add(dock.selected().orElse(null)), () -> {
                    });
            dock.addView("plugin:calendar:panel", "Calendar", new Label("c"), new Label("c"));
            out.add("closed: " + support.linksInSight());
            open.set(true);
            out.add("open on the backlinks: " + support.linksInSight());
            dock.select("plugin:calendar:panel");
            out.add("open on a plugin's view: " + support.linksInSight());
            dock.select(RightPanelSupport.OUTGOING);
            out.add("open on the outgoing links: " + support.linksInSight());
            out.add("worked out when brought into sight: " + refreshed);
            return out;
        });

        assertEquals(List.of("closed: false", "open on the backlinks: true", "open on a plugin's view: false",
                "open on the outgoing links: true", "worked out when brought into sight: [outgoing-links]"), seen);
        prefs.removeNode();
    }
}
