package com.example.jylos.ui.controller;

import static com.example.jylos.tests.FxTestSupport.onFx;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.prefs.Preferences;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.example.jylos.data.dao.filesystem.FolderDAOFileSystem;
import com.example.jylos.data.dao.filesystem.NoteDAOFileSystem;
import com.example.jylos.data.models.Note;
import com.example.jylos.event.EventBus;
import com.example.jylos.event.events.SystemActionEvent.ActionType;
import com.example.jylos.service.BacklinkService;
import com.example.jylos.service.BacklinkService.OutgoingLink;
import com.example.jylos.service.NoteService;
import com.example.jylos.service.LinkOccurrences.Occurrence;
import com.example.jylos.tests.FxTestSupport;
import com.example.jylos.ui.components.LinksPanel;
import com.example.jylos.ui.components.SideDock;

import javafx.event.Event;
import javafx.event.EventType;
import javafx.geometry.Bounds;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.Labeled;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.SplitPane;
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

    /**
     * The groups the panel is split in, the view chosen in each and how tall they are, are kept
     * for the next start; the links are in sight when one of their views shows in any group.
     */
    @Test
    void theGroupsTheirViewsAndTheirHeightsAreKept() throws Exception {
        Assumptions.assumeTrue(FxTestSupport.isFxRuntimeAvailable());
        Preferences prefs = freshPrefs();

        List<String> seen = onFx(() -> {
            List<String> out = new ArrayList<>();
            SideDock dock = new SideDock();
            RightPanelSupport support = new RightPanelSupport();
            support.wire(dock, prefs, key -> key, links(), () -> true, () -> {
            }, () -> {
            });
            Stage stage = new Stage();
            stage.setScene(new Scene(dock, 300, 400));
            stage.show();
            dock.addView("plugin:outline:panel", "Outline", new Label("o"), new Label("o"));
            dock.splitDown(RightPanelSupport.OUTGOING);
            dock.select("plugin:outline:panel");
            out.add("shown " + dock.shown() + ", links in sight " + support.linksInSight());
            dock.setClosed(RightPanelSupport.OUTGOING, true);
            out.add("shown " + dock.shown() + ", links in sight " + support.linksInSight());
            dock.setClosed(RightPanelSupport.OUTGOING, false);
            SplitPane split = (SplitPane) dock.lookup(".side-dock-split");
            split.setDividerPosition(0, 0.25);
            mouse(split, MouseEvent.MOUSE_RELEASED, 10, 10);
            out.add(prefs.get(RightPanelSupport.GROUPS_KEY, "").replace("\n", ",").replace("\t", ":") + " | "
                    + prefs.get(RightPanelSupport.VIEW_KEY, "").replace("\n", ",") + " | "
                    + prefs.get(RightPanelSupport.DIVIDERS_KEY, ""));
            stage.close();

            SideDock next = new SideDock();
            new RightPanelSupport().wire(next, prefs, key -> key, links(), () -> true, () -> {
            }, () -> {
            });
            Stage again = new Stage();
            again.setScene(new Scene(next, 300, 400));
            again.show();
            next.addView("plugin:outline:panel", "Outline", new Label("o"), new Label("o"));
            next.applyCss();
            next.layout();
            SplitPane nextSplit = (SplitPane) next.lookup(".side-dock-split");
            out.add("next start: shown " + next.shown() + ", divider "
                    + Math.round(nextSplit.getDividerPositions()[0] * 100) / 100.0);
            again.close();
            return out;
        });

        assertEquals(List.of("shown [plugin:outline:panel, outgoing-links], links in sight true",
                "shown [plugin:outline:panel], links in sight false",
                "outgoing-links:1 | plugin:outline:panel,outgoing-links | 0.25",
                "next start: shown [plugin:outline:panel, outgoing-links], divider 0.25"), seen);
        prefs.removeNode();
    }

    /** Each group shows again the view chosen in it last time, not its first one. */
    @Test
    void eachGroupShowsTheViewChosenInItLastTime() throws Exception {
        Assumptions.assumeTrue(FxTestSupport.isFxRuntimeAvailable());
        Preferences prefs = freshPrefs();
        prefs.put(RightPanelSupport.GROUPS_KEY, "outgoing-links\t1\nplugin:calendar:panel\t1");
        prefs.put(RightPanelSupport.VIEW_KEY, "backlinks\nplugin:calendar:panel");

        List<String> seen = onFx(() -> {
            SideDock dock = new SideDock();
            new RightPanelSupport().wire(dock, prefs, key -> key, links(), () -> true, () -> {
            }, () -> {
            });
            dock.addView("plugin:calendar:panel", "Calendar", new Label("c"), new Label("c"));
            return List.of(dock.shown().toString());
        });

        assertEquals(List.of("[backlinks, plugin:calendar:panel]"), seen);
        prefs.removeNode();
    }

    /**
     * "Pin a copy" in the menu of a links view's tab makes a copy of that view, pinned to the
     * note in sight, in a group of its own under the view's; the copies are kept for the next
     * start, in their groups, and closing one takes it away for good. With no note in sight the
     * entry cannot be used; a copy cannot be pinned again.
     */
    @Test
    void aLinksViewIsPinnedToTheNoteInSightAsACopyBelowKeptForTheNextStart() throws Exception {
        Assumptions.assumeTrue(FxTestSupport.isFxRuntimeAvailable());
        Preferences prefs = freshPrefs();
        Note[] inSight = {null};

        List<String> seen = onFx(() -> {
            List<String> out = new ArrayList<>();
            SideDock dock = new SideDock();
            RightPanelSupport support = new RightPanelSupport();
            support.wire(dock, prefs, key -> key, links(), () -> true, () -> {
            }, () -> {
            });
            support.pinWith(new BacklinksSupport(), () -> inSight[0]);
            Stage stage = new Stage();
            stage.setScene(new Scene(dock, 300, 400));
            stage.show();
            out.add("no note: " + pinItem(dock, RightPanelSupport.BACKLINKS).isDisable());
            inSight[0] = new Note("Carpeta/Target.md", "Target", "");
            MenuItem pin = pinItem(dock, RightPanelSupport.BACKLINKS);
            out.add("note: " + pin.isDisable());
            pin.fire();
            String copy = dock.viewIds().get(dock.viewIds().size() - 1);
            out.add(copy + " shown " + dock.shown() + " groups "
                    + prefs.get(RightPanelSupport.GROUPS_KEY, "").replace("	", ":"));
            out.add(prefs.get(RightPanelSupport.PINNED_KEY, "").replace("	", ":"));
            out.add("the copy's menu: " + texts(dock.menuFor(copy)));
            stage.close();

            SideDock next = new SideDock();
            RightPanelSupport nextSupport = new RightPanelSupport();
            nextSupport.wire(next, prefs, key -> key, links(), () -> true, () -> {
            }, () -> {
            });
            nextSupport.pinWith(new BacklinksSupport(), () -> null);
            out.add("next start: " + next.viewIds() + " shown " + next.shown());
            next.menuFor(copy).getItems().get(0).fire(); // Close
            out.add("closed: " + next.viewIds() + " pinned [" + prefs.get(RightPanelSupport.PINNED_KEY, "") + "]");
            return out;
        });

        assertEquals(List.of("no note: true", "note: false",
                "backlinks@1 shown [backlinks, backlinks@1] groups backlinks@1:1",
                "backlinks@1:backlinks:Carpeta/Target.md",
                "the copy's menu: [side.close, side.split_down, side.move_up, -, side.backlinks, links.outgoing]",
                "next start: [backlinks, outgoing-links, backlinks@1] shown [backlinks, backlinks@1]",
                "closed: [backlinks, outgoing-links] pinned []"), seen);
        prefs.removeNode();
    }

    /** A pinned copy whose note is renamed is named after it, at once, and kept so. */
    @Test
    void aPinnedCopyIsNamedAfterItsNoteWhenRenamed() throws Exception {
        Assumptions.assumeTrue(FxTestSupport.isFxRuntimeAvailable());
        Preferences prefs = freshPrefs();
        prefs.put(RightPanelSupport.PINNED_KEY, "outgoing-links@3\toutgoing-links\tA/Viejo.md");

        List<String> seen = onFx(() -> {
            SideDock dock = new SideDock();
            RightPanelSupport support = new RightPanelSupport();
            support.wire(dock, prefs, key -> key, links(), () -> true, () -> {
            }, () -> {
            });
            BacklinksSupport pins = new BacklinksSupport();
            support.pinWith(pins, () -> null);
            Stage stage = new Stage();
            stage.setScene(new Scene(dock, 300, 400));
            stage.show();
            List<String> out = new ArrayList<>();
            out.add(tooltips(dock) + " " + pinnedTo(dock));
            pins.rebind("A/Viejo.md", "B/Nuevo.md");
            out.add(tooltips(dock) + " " + pinnedTo(dock));
            out.add(prefs.get(RightPanelSupport.PINNED_KEY, "").replace("\t", ":"));
            stage.close();
            return out;
        });

        assertEquals(List.of("[side.backlinks, links.outgoing, links.outgoing · Viejo] side.pinned_to",
                "[side.backlinks, links.outgoing, links.outgoing · Nuevo] side.pinned_to",
                "outgoing-links@3:outgoing-links:B/Nuevo.md"), seen);
        prefs.removeNode();
    }

    /** A copy kept from last time works its links out when the panel is opened, not while it is closed. */
    @Test
    void aPinnedCopyIsWorkedOutWhenThePanelOpens(@TempDir Path vault) throws Exception {
        Assumptions.assumeTrue(FxTestSupport.isFxRuntimeAvailable());
        Files.writeString(vault.resolve("Target.md"), "# T\n");
        Files.writeString(vault.resolve("Fuente.md"), "[[Target]]\n");
        NoteService notes = new NoteService(new NoteDAOFileSystem(vault.toString()),
                new FolderDAOFileSystem(vault.toString()));
        BacklinkService service = new BacklinkService(notes, EventBus.getInstance());
        Preferences prefs = freshPrefs();
        prefs.put(RightPanelSupport.PINNED_KEY, "backlinks@1\tbacklinks\tTarget.md");
        prefs.put(RightPanelSupport.GROUPS_KEY, "backlinks@1\t1");
        AtomicBoolean open = new AtomicBoolean(false);
        RightPanelSupport support = new RightPanelSupport();
        SideDock dock = new SideDock();
        try {
            onFx(() -> {
                BacklinksSupport links = new BacklinksSupport();
                links.wire(service, notes, k -> k, n -> { }, support::linksInSight);
                support.wire(dock, prefs, key -> key, links.links(), open::get, () -> {
                }, () -> {
                });
                Stage stage = new Stage();
                stage.setScene(new Scene(dock, 300, 400));
                stage.show();
                support.pinWith(links, () -> null);
                return null;
            });
            Thread.sleep(500);
            List<String> closed = groupNames(dock);
            onFx(() -> {
                open.set(true);
                support.panelOpened();
                return null;
            });
            long deadline = System.currentTimeMillis() + 15_000;
            while (!groupNames(dock).equals(List.of("Fuente")) && System.currentTimeMillis() < deadline) {
                Thread.sleep(50);
            }
            assertEquals(List.of(List.of(), List.of("Fuente")), List.of(closed, groupNames(dock)));
        } finally {
            service.shutdown();
            EventBus.getInstance().clear();
            prefs.removeNode();
        }
    }

    private static List<String> groupNames(SideDock dock) throws Exception {
        return onFx(() -> dock.lookupAll(".links-group-name").stream().map(n -> ((Labeled) n).getText()).toList());
    }

    private static List<String> tooltips(SideDock dock) {
        return dock.lookupAll(".side-dock-tab").stream().map(tab -> ((Button) tab).getTooltip().getText()).toList();
    }

    private static String pinnedTo(SideDock dock) {
        return ((Label) dock.lookup(".side-dock-pinned-to")).getText();
    }

    private static MenuItem pinItem(SideDock dock, String id) {
        return dock.menuFor(id).getItems().stream().filter(i -> i.getText() != null && i.getText().startsWith("side.pin_copy"))
                .findFirst().orElseThrow();
    }

    private static List<String> texts(ContextMenu menu) {
        return menu.getItems().stream().map(i -> i instanceof SeparatorMenuItem ? "-" : i.getText()).toList();
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
