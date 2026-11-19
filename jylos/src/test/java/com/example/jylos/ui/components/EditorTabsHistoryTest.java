package com.example.jylos.ui.components;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.example.jylos.ui.components.EditorTabsModel.OpenTarget;

/**
 * As in Obsidian, each tab keeps what it showed: back and forward go through what that tab
 * held (notes, the graph, the new tab it started as), not through every note opened anywhere.
 * A new tab reuses the active one while it is still empty; the + always adds one at the end.
 */
class EditorTabsHistoryTest {

    @Test
    void backAndForwardGoThroughWhatTheTabShowed() {
        EditorTabsModel tabs = new EditorTabsModel();
        tabs.open("a", OpenTarget.CURRENT_TAB);
        tabs.open("b", OpenTarget.CURRENT_TAB);
        boolean couldGoBack = tabs.canGoBack();

        String back = tabs.back();
        List<Object> afterBack = List.of(tabs.getIds(), tabs.getActiveId(), tabs.canGoBack(), tabs.canGoForward());
        String forward = tabs.forward();

        assertTrue(couldGoBack);
        assertEquals("a", back);
        assertEquals(List.of(List.of("a"), "a", false, true), afterBack);
        assertEquals("b", forward);
        assertEquals(List.of("b"), tabs.getIds());
        assertFalse(tabs.canGoForward());
    }

    @Test
    void eachTabHasItsOwnHistory() {
        EditorTabsModel tabs = new EditorTabsModel();
        tabs.open("a", OpenTarget.CURRENT_TAB);
        tabs.open("b", OpenTarget.CURRENT_TAB);
        tabs.open("c", OpenTarget.NEW_TAB);

        String backInTheNewTab = tabs.back();
        tabs.open("b", OpenTarget.CURRENT_TAB); // the first tab, brought to the front

        assertNull(backInTheNewTab, "the new tab has nothing behind it");
        assertEquals("a", tabs.back());
        assertEquals(List.of("a", "c"), tabs.getIds());
    }

    @Test
    void backReturnsToTheGraphAndToTheNewTabTheTabStartedAs() {
        EditorTabsModel tabs = new EditorTabsModel();
        String empty = ViewTabs.newEmptyTab();
        tabs.open(empty, OpenTarget.NEW_TAB);
        tabs.open(ViewTabs.GRAPH, OpenTarget.CURRENT_TAB);
        tabs.open("a", OpenTarget.CURRENT_TAB);

        List<String> backs = new ArrayList<>();
        backs.add(tabs.back());
        backs.add(tabs.back());
        backs.add(tabs.back());

        assertEquals(List.of(ViewTabs.GRAPH, empty), backs.subList(0, 2));
        assertNull(backs.get(2));
        assertEquals(List.of(empty), tabs.getIds());
    }

    /** Opening a new note, a canvas or anything "in a new tab" fills the active tab while it is empty. */
    @Test
    void aNewTabReusesTheActiveOneWhileItIsEmpty() {
        EditorTabsModel tabs = new EditorTabsModel();
        tabs.open("a", OpenTarget.CURRENT_TAB);
        String empty = ViewTabs.newEmptyTab();
        tabs.open(empty, OpenTarget.NEW_TAB);

        tabs.open("nueva", OpenTarget.NEW_TAB);

        assertEquals(List.of("a", "nueva"), tabs.getIds());
        assertEquals(empty, tabs.back(), "back goes to the new tab it was");
    }

    /** The + always adds a tab, at the end, as in Obsidian; even with an empty one in front. */
    @Test
    void anEmptyTabIsAddedAtTheEnd() {
        EditorTabsModel tabs = new EditorTabsModel();
        tabs.open("a", OpenTarget.CURRENT_TAB);
        tabs.open("b", OpenTarget.NEW_TAB);
        tabs.open("a", OpenTarget.CURRENT_TAB);
        String first = ViewTabs.newEmptyTab();
        tabs.open(first, OpenTarget.NEW_TAB);
        String second = ViewTabs.newEmptyTab();
        tabs.open(second, OpenTarget.NEW_TAB);

        assertEquals(List.of("a", "b", first, second), tabs.getIds());
        assertEquals(second, tabs.getActiveId());
    }

    @Test
    void theHistoryKeepsTwentyEntries() {
        EditorTabsModel tabs = new EditorTabsModel();
        for (int i = 0; i <= 25; i++) {
            tabs.open("n" + i, OpenTarget.CURRENT_TAB);
        }

        int steps = 0;
        String last = null;
        for (String target = tabs.back(); target != null; target = tabs.back()) {
            last = target;
            steps++;
        }

        assertEquals(20, steps);
        assertEquals("n5", last);
    }

    /** A note is never in two tabs: going back to one open elsewhere takes it from there. */
    @Test
    void goingBackToANoteOpenInAnotherTabTakesItFromThere() {
        EditorTabsModel tabs = new EditorTabsModel();
        tabs.open("a", OpenTarget.CURRENT_TAB);
        tabs.open("b", OpenTarget.CURRENT_TAB);
        tabs.open("a", OpenTarget.NEW_TAB);
        tabs.open("b", OpenTarget.CURRENT_TAB);

        assertEquals("a", tabs.back());
        assertEquals(List.of("a"), tabs.getIds());
    }

    @Test
    void aRenamedNoteIsRenamedInTheHistoriesAndADeletedOneLeavesThem() {
        EditorTabsModel tabs = new EditorTabsModel();
        tabs.open("a", OpenTarget.CURRENT_TAB);
        tabs.open("b", OpenTarget.CURRENT_TAB);
        tabs.open("c", OpenTarget.CURRENT_TAB);

        tabs.rebind("a", "a2");
        tabs.forget("b");

        assertEquals("a2", tabs.back());
        assertNull(tabs.back());
    }
}
