package com.example.jylos.ui.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.example.jylos.data.models.Note;
import com.example.jylos.data.models.VaultSettings;
import com.example.jylos.data.models.VaultSettings.Startup;
import com.example.jylos.data.models.VaultSettings.StartupFront;
import com.example.jylos.data.models.VaultSettings.StartupTabs;
import com.example.jylos.event.events.SystemActionEvent.ActionType;
import com.example.jylos.workspace.TabHistory;

/**
 * What opens with the vault (only the pinned tabs, or all of the last session; and in front,
 * the tab that was active, a new tab, the home page or today's note) and "Go home".
 */
class StartupSupportTest {

    private static final Note HOME = new Note("Inicio/Panel.canvas", "Panel.canvas", "{}");

    private final List<String> calls = new ArrayList<>();
    private VaultSettings settings = VaultSettings.defaults();

    private StartupSupport support() {
        StartupSupport support = new StartupSupport();
        support.wire(new StartupSupport.Host(() -> settings,
                path -> path.equals(HOME.getId()) ? Optional.of(HOME) : Optional.empty(),
                note -> calls.add("open " + note.getId()),
                note -> calls.add("open in new tab " + note.getId()),
                () -> calls.add("new tab"),
                () -> calls.add("today"),
                message -> calls.add("status " + message),
                key -> key,
                available -> calls.add(available ? "house shown" : "house hidden")));
        return support;
    }

    private List<String> withoutHouse() {
        return calls.stream().filter(call -> !call.startsWith("house")).toList();
    }

    private void startWith(String home, StartupFront front) {
        settings = VaultSettings.defaults().withStartup(new Startup(home, StartupTabs.LAST_SESSION, front));
    }

    /** Without a home page the house is not there, as before; it shows once one is chosen. */
    @Test
    void theHouseShowsOnlyWithAHomePage() {
        StartupSupport support = support();
        startWith(HOME.getId(), StartupFront.LAST_ACTIVE);
        support.refreshHome();
        startWith("", StartupFront.LAST_ACTIVE);
        support.refreshHome();

        assertEquals(List.of("house hidden", "house shown", "house hidden"), calls);
    }

    @Test
    void onlyThePinnedTabsAreKeptWhenAsked() {
        WorkspaceController.SessionTabs saved = new WorkspaceController.SessionTabs(List.of("a.md", "b.md", "c.md"),
                "b.md", List.of(new TabHistory(List.of(), List.of(), true), TabHistory.NONE,
                        new TabHistory(List.of("x.md"), List.of(), true)));

        assertEquals(saved, StartupSupport.keep(saved, StartupTabs.LAST_SESSION));
        WorkspaceController.SessionTabs pinned = StartupSupport.keep(saved, StartupTabs.PINNED);
        assertEquals(List.of("a.md", "c.md"), pinned.ids());
        assertEquals(List.of(true, true), pinned.histories().stream().map(TabHistory::pinned).toList());
        assertEquals(null, pinned.activeId(), "the active one was not pinned: whatever comes in front decides");
    }

    @Test
    void whatComesInFront() {
        startWith("", StartupFront.LAST_ACTIVE);
        support().front();
        startWith("", StartupFront.NEW_TAB);
        support().front();
        startWith("", StartupFront.TODAY);
        support().front();
        startWith(HOME.getId(), StartupFront.HOME);
        support().front();

        assertEquals(List.of("new tab", "today", "open Inicio/Panel.canvas"), withoutHouse());
    }

    @Test
    void aHomePageThatIsGoneOrNotChosenIsToldAndNothingElseHappens() {
        startWith("Borrada.md", StartupFront.HOME);
        support().front();
        startWith("", StartupFront.HOME);
        support().goHome(false);

        assertEquals(List.of("status status.home_missing", "status status.home_not_set"), withoutHouse());
    }

    @Test
    void goHomeOpensItHereOrInANewTab() {
        startWith(HOME.getId(), StartupFront.LAST_ACTIVE);
        Map<ActionType, Runnable> handlers = new EnumMap<>(ActionType.class);
        support().register(handlers);

        handlers.get(ActionType.GO_HOME).run();
        handlers.get(ActionType.GO_HOME_NEW_TAB).run();

        assertEquals(List.of("open Inicio/Panel.canvas", "open in new tab Inicio/Panel.canvas"), withoutHouse());
    }
}
