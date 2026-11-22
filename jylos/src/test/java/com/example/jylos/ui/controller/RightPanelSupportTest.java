package com.example.jylos.ui.controller;

import static com.example.jylos.tests.FxTestSupport.onFx;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.prefs.Preferences;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import com.example.jylos.tests.FxTestSupport;
import com.example.jylos.ui.components.SideDock;

import javafx.scene.control.Label;

/**
 * The right panel's own views, the backlinks and the outgoing links, as Obsidian's; the view
 * chosen is remembered for the next start (a plugin's too, chosen when the plugin comes), and
 * the links are worked out only while one of their views is in sight.
 */
class RightPanelSupportTest {

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
            new RightPanelSupport().wire(dock, prefs, key -> key, new Label("in"), new Label("out"), () -> true, () -> {
            });
            out.add(dock.viewIds() + " " + dock.selected().orElse(null));
            dock.select(RightPanelSupport.OUTGOING);
            out.add(prefs.get(RightPanelSupport.VIEW_KEY, null));

            // Next start: the outgoing links again.
            SideDock next = new SideDock();
            new RightPanelSupport().wire(next, prefs, key -> key, new Label("in"), new Label("out"), () -> true, () -> {
            });
            out.add(next.selected().orElse(null));

            // A plugin's view chosen last time is chosen when the plugin comes.
            next.addView("plugin:calendar:panel", "Calendar", new Label("c"), new Label("c"));
            next.select("plugin:calendar:panel");
            SideDock third = new SideDock();
            new RightPanelSupport().wire(third, prefs, key -> key, new Label("in"), new Label("out"), () -> true, () -> {
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
            support.wire(dock, prefs, key -> key, new Label("in"), new Label("out"), open::get,
                    () -> refreshed.add(dock.selected().orElse(null)));
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
