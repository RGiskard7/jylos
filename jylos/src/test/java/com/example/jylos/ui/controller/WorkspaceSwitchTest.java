package com.example.jylos.ui.controller;

import static com.example.jylos.tests.FxTestSupport.onFx;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.prefs.Preferences;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.example.jylos.tests.FxTestSupport;
import com.example.jylos.workspace.SessionStore;
import com.example.jylos.workspace.TabHistory;
import com.example.jylos.workspace.Workspace;
import com.example.jylos.workspace.WorkspaceHistoryRepository;
import com.example.jylos.workspace.WorkspaceRepository;
import com.example.jylos.workspace.WorkspaceService;

import javafx.scene.control.CheckMenuItem;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.layout.HBox;

/**
 * Workspaces as working contexts: opening one replaces the open tabs with its own (saving the
 * one in use first, unless that is turned off), the status bar names the one in use and
 * switches between them, and the next start reopens the last session's tabs and workspace.
 */
class WorkspaceSwitchTest {

    private static final List<TabHistory> HISTORY = List.of(new TabHistory(List.of("z.md"), List.of()),
            TabHistory.NONE);

    @TempDir
    Path dir;
    private Preferences prefs;
    private WorkspaceService service;
    private SessionStore session;

    @BeforeEach
    void setUp() {
        prefs = Preferences.userRoot().node("/com/example/jylos/test/" + UUID.randomUUID());
        service = new WorkspaceService(new WorkspaceRepository(dir.resolve("workspaces.dat")),
                new WorkspaceHistoryRepository(dir.resolve("workspace-history.dat")));
        session = new SessionStore(dir);
    }

    @AfterEach
    void tearDown() throws Exception {
        prefs.removeNode();
    }

    @Test
    void openingAWorkspaceReplacesTheTabsAndSavesTheOneInUse() throws Exception {
        Assumptions.assumeTrue(FxTestSupport.isFxRuntimeAvailable());
        Workspace writing = service.save("Escritura", live(List.of("a.md", "b.md"), "a.md"), HISTORY);
        Workspace research = service.save("Investigación", live(List.of("x.md"), "x.md"), List.of());

        List<String> seen = onFx(() -> {
            Window w = new Window();
            List<String> out = new ArrayList<>();
            out.add(w.indicator());
            w.controller.switchTo(writing);
            out.add(w.calls + " " + w.indicator());

            w.calls.clear();
            w.open = List.of("a.md", "b.md", "c.md"); // tabs changed while in use
            w.controller.switchTo(research);
            out.add(w.calls + " " + w.indicator() + " saved " + service.findById(writing.id()).orElseThrow().openNoteIds());

            prefs.putBoolean(WorkspaceController.SAVE_ON_SWITCH, false);
            w.calls.clear();
            w.open = List.of("x.md", "y.md");
            w.controller.switchTo(writing);
            out.add(w.indicator() + " saved " + service.findById(research.id()).orElseThrow().openNoteIds());

            w.allowClose = false; // the save prompt is cancelled
            w.calls.clear();
            w.controller.switchTo(research);
            out.add(w.calls + " " + w.indicator());
            return out;
        });

        assertEquals(List.of(
                "workspace.indicator.none",
                "[close all, restore [a.md, b.md] a.md 2, layout Escritura] Escritura",
                "[close all, restore [x.md] x.md 0, layout Investigación] Investigación saved [a.md, b.md, c.md]",
                "Escritura saved [x.md]",
                "[close all] Escritura"), seen);
    }

    @Test
    void theStatusBarMenuSwitchesSavesAndTurnsSavingOnSwitchOff() throws Exception {
        Assumptions.assumeTrue(FxTestSupport.isFxRuntimeAvailable());
        Workspace writing = service.save("Escritura", live(List.of("a.md"), "a.md"), List.of());
        service.save("Investigación", live(List.of("x.md"), "x.md"), List.of());

        List<String> seen = onFx(() -> {
            Window w = new Window();
            List<String> out = new ArrayList<>();
            out.add(items(w.controller.menu()));
            w.controller.switchTo(writing);
            ContextMenu menu = w.controller.menu();
            out.add(items(menu));

            w.calls.clear();
            menu.getItems().get(1).fire(); // the one in use: nothing happens
            out.add(w.calls.toString());
            menu.getItems().get(2).fire();
            out.add(w.calls + " " + w.indicator());

            menu = w.controller.menu();
            CheckMenuItem saveOnSwitch = (CheckMenuItem) menu.getItems().get(menu.getItems().size() - 1);
            saveOnSwitch.setSelected(false); // as a click does, before its action
            saveOnSwitch.fire();
            out.add("save on switch " + prefs.getBoolean(WorkspaceController.SAVE_ON_SWITCH, true));
            return out;
        });

        assertEquals(List.of(
                "[✓workspace.indicator.none,  Escritura,  Investigación, ---, workspace.menu.save_as,"
                        + " workspace.menu.manage, ---, ✓workspace.menu.save_on_switch]",
                "[ workspace.indicator.none, ✓Escritura,  Investigación, ---, workspace.menu.save,"
                        + " workspace.menu.save_as, workspace.menu.manage, ---, ✓workspace.menu.save_on_switch]",
                "[]",
                "[close all, restore [x.md] x.md 0, layout Investigación] Investigación",
                "save on switch false"), seen);
    }

    /**
     * "No workspace" is a context of its own: leaving it for a workspace keeps its tabs, and
     * picking it again brings them back (after saving the workspace in use).
     */
    @Test
    void noWorkspaceKeepsItsOwnTabsToComeBackTo() throws Exception {
        Assumptions.assumeTrue(FxTestSupport.isFxRuntimeAvailable());
        Workspace writing = service.save("Escritura", live(List.of("a.md"), "a.md"), List.of());

        List<String> seen = onFx(() -> {
            Window w = new Window();
            List<String> out = new ArrayList<>();
            w.controller.restoreSession();
            w.open = List.of("p.md", "q.md");
            w.history = HISTORY;
            w.controller.menu().getItems().get(0).fire(); // already in no workspace: nothing happens
            out.add(w.calls.toString());
            w.controller.switchTo(writing);
            w.calls.clear();
            w.open = List.of("a.md", "b.md");
            w.controller.menu().getItems().get(0).fire();
            out.add(w.calls + " " + w.indicator() + " saved "
                    + service.findById(writing.id()).orElseThrow().openNoteIds());
            return out;
        });

        assertEquals(List.of("[]",
                "[close all, restore [p.md, q.md] q.md 2, layout ] workspace.indicator.none saved [a.md, b.md]"), seen);
    }

    /**
     * With many workspaces the menu stays short: no workspace, then the eight used last, by
     * name; "all workspaces" opens the dialog, which finds them by name, accents or not.
     */
    @Test
    void theMenuListsTheEightUsedLastAndTheDialogFindsTheRest() throws Exception {
        Assumptions.assumeTrue(FxTestSupport.isFxRuntimeAvailable());
        List<Workspace> all = new ArrayList<>();
        for (int i = 1; i <= 12; i++) {
            all.add(service.save(String.format("E%02d", i), live(List.of("a.md"), "a.md"), List.of()));
        }
        Workspace research = service.save("Investigación", live(List.of("x.md"), "x.md"), List.of());

        List<String> seen = onFx(() -> {
            Window w = new Window();
            List<String> out = new ArrayList<>();
            for (int i : new int[] {12, 3, 7, 9, 11, 2, 5, 10, 4}) {
                w.controller.switchTo(all.get(i - 1));
            }
            out.add(items(new Window().controller.menu())); // remembered after a restart
            out.add(WorkspaceController.matching(service.list(), "  investigacion ").toString()
                    .equals(List.of(research).toString()) + " "
                    + WorkspaceController.matching(service.list(), "E1").stream().map(Workspace::name).toList() + " "
                    + WorkspaceController.matching(service.list(), "").size() + " "
                    + WorkspaceController.matching(service.list(), "zz").size());
            return out;
        });

        assertEquals(List.of(
                "[✓workspace.indicator.none,  E02,  E03,  E04,  E05,  E07,  E09,  E10,  E11, ---,"
                        + " workspace.menu.save_as, workspace.menu.all, ---, ✓workspace.menu.save_on_switch]",
                "true [E10, E11, E12] 13 0"), seen);
    }

    @Test
    void theNextStartReopensTheLastSessionAndItsWorkspace() throws Exception {
        Assumptions.assumeTrue(FxTestSupport.isFxRuntimeAvailable());
        Workspace writing = service.save("Escritura", live(List.of("a.md"), "a.md"), List.of());

        List<String> seen = onFx(() -> {
            List<String> out = new ArrayList<>();
            Window first = new Window();
            first.controller.saveSession(); // before the session is reopened: nothing is kept
            out.add("before restoring " + session.load().isPresent());
            first.controller.restoreSession();
            out.add(first.calls + " " + first.indicator());
            first.controller.switchTo(writing);
            first.open = List.of("a.md", "c.md");
            first.history = HISTORY;
            first.controller.saveSession();

            Window next = new Window();
            next.controller.restoreSession();
            next.controller.restoreSession(); // only once
            out.add(next.calls + " " + next.indicator());

            service.delete(writing.id());
            Window afterDelete = new Window();
            afterDelete.controller.restoreSession();
            out.add(afterDelete.calls + " " + afterDelete.indicator());
            return out;
        });

        assertEquals(List.of("before restoring false", "[] workspace.indicator.none",
                "[restore [a.md, c.md] c.md 2] Escritura",
                "[restore [a.md, c.md] c.md 2] workspace.indicator.none"), seen);
    }

    @Test
    void renamingOrDeletingTheWorkspaceInUseUpdatesTheStatusBar() throws Exception {
        Assumptions.assumeTrue(FxTestSupport.isFxRuntimeAvailable());
        Workspace writing = service.save("Escritura", live(List.of("a.md"), "a.md"), List.of());
        service.save("Investigación", live(List.of("x.md"), "x.md"), List.of());

        List<String> seen = onFx(() -> {
            Window w = new Window();
            List<String> out = new ArrayList<>();
            w.controller.switchTo(writing);
            w.status.clear();
            out.add(w.controller.rename(writing, "investigación") + " " + w.indicator() + " " + w.status);
            out.add(w.controller.rename(writing, "Libro") + " " + w.indicator());
            w.controller.delete(service.findById(writing.id()).orElseThrow());
            out.add(w.indicator() + " " + service.list().stream().map(Workspace::name).toList());
            return out;
        });

        assertEquals(List.of("false Escritura [workspace.status.name_taken]", "true Libro",
                "workspace.indicator.none [Investigación]"), seen);
    }

    private static Workspace live(List<String> open, String active) {
        return WorkspaceService.liveState(open, active, "SPLIT", true, false, 0.22, 0.25, "filesystem");
    }

    private static String items(ContextMenu menu) {
        return menu.getItems().stream().map(item -> item instanceof SeparatorMenuItem ? "---"
                : item instanceof CheckMenuItem check ? (check.isSelected() ? "✓" : " ") + check.getText()
                        : item.getText()).toList().toString();
    }

    /** The main window as the workspaces see it: its tabs, and what it was asked to do. */
    private final class Window {
        final List<String> calls = new ArrayList<>();
        final List<String> status = new ArrayList<>();
        final HBox statusBar = new HBox(new Label("Local"));
        final WorkspaceController controller = new WorkspaceController(service, session);
        List<String> open = List.of();
        List<TabHistory> history = List.of();
        boolean allowClose = true;

        Window() {
            controller.wire(new WorkspaceController.Host(
                    () -> live(open, open.isEmpty() ? null : open.get(open.size() - 1)),
                    () -> history,
                    () -> {
                        calls.add("close all");
                        return allowClose;
                    },
                    (ids, active, histories) -> {
                        calls.add("restore " + ids + " " + active + " " + histories.size());
                        open = ids;
                        return 0;
                    },
                    ws -> calls.add("layout " + ws.name())),
                    key -> key, status::add, () -> null, prefs, statusBar);
        }

        String indicator() {
            return statusBar.getChildren().stream()
                    .filter(node -> node.getStyleClass().contains("status-workspace"))
                    .map(node -> ((Label) node).getText()).findFirst().orElse("no indicator");
        }
    }
}
