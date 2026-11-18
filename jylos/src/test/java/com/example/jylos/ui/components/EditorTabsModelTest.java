package com.example.jylos.ui.components;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;

import com.example.jylos.ui.components.EditorTabsModel.OpenTarget;

/**
 * Tabs behave like VS Code: a plain open replaces the note in the current tab, a note that
 * is already open is activated rather than duplicated, and "open in new tab" inserts the
 * tab right after the active one. Before, every opened note added a tab at the end.
 */
class EditorTabsModelTest {

    /**
     * A tab's back and forward can be read and given back (a workspace or the last session
     * reopening them): going back then walks what it had, and only the last 20 steps are kept.
     */
    @Test
    void aTabsHistoryIsReadAndGivenBack() {
        EditorTabsModel tabs = new EditorTabsModel();
        tabs.open("a", OpenTarget.CURRENT_TAB);
        tabs.open("b", OpenTarget.CURRENT_TAB);
        tabs.open("c", OpenTarget.CURRENT_TAB);
        tabs.back();
        assertEquals(List.of(List.of("a"), List.of("c")), List.of(tabs.backOf("b"), tabs.forwardOf("b")));
        assertEquals(List.of(List.of(), List.of()), List.of(tabs.backOf("unknown"), tabs.forwardOf("unknown")));

        EditorTabsModel reopened = new EditorTabsModel();
        reopened.open("b", OpenTarget.NEW_TAB);
        reopened.open("x", OpenTarget.NEW_TAB);
        reopened.setHistory("b", tabs.backOf("b"), tabs.forwardOf("b"));
        reopened.open("b", OpenTarget.CURRENT_TAB);
        assertEquals(List.of(true, true), List.of(reopened.canGoBack(), reopened.canGoForward()));
        assertEquals("a", reopened.back());
        assertEquals("b", reopened.forward());
        assertEquals("c", reopened.forward());
        assertNull(reopened.forward());

        List<String> long25 = IntStream.range(0, 25).mapToObj(i -> "n" + i).toList();
        reopened.setHistory("x", long25, List.of());
        assertEquals(long25.subList(5, 25), reopened.backOf("x"), "the last 20 are kept");
    }

    @Test
    void theFirstNoteOpensATab() {
        EditorTabsModel tabs = new EditorTabsModel();

        tabs.open("a", OpenTarget.CURRENT_TAB);

        assertEquals(List.of("a"), tabs.getIds());
        assertEquals("a", tabs.getActiveId());
    }

    @Test
    void aPlainOpenReplacesTheActiveTabInPlace() {
        EditorTabsModel tabs = tabsWith("a", "b", "c");
        tabs.open("b", OpenTarget.CURRENT_TAB);

        tabs.open("x", OpenTarget.CURRENT_TAB);

        assertEquals(List.of("a", "x", "c"), tabs.getIds());
        assertEquals("x", tabs.getActiveId());
    }

    @Test
    void openingANoteThatIsAlreadyOpenActivatesItsTabWithoutDuplicating() {
        EditorTabsModel tabs = tabsWith("a", "b", "c");
        tabs.open("a", OpenTarget.CURRENT_TAB);

        tabs.open("c", OpenTarget.CURRENT_TAB);
        assertEquals(List.of("a", "b", "c"), tabs.getIds());
        assertEquals("c", tabs.getActiveId());

        tabs.open("a", OpenTarget.NEW_TAB);
        assertEquals(List.of("a", "b", "c"), tabs.getIds());
        assertEquals("a", tabs.getActiveId());
    }

    @Test
    void openInNewTabInsertsRightAfterTheActiveTab() {
        EditorTabsModel tabs = tabsWith("a", "b", "c");
        tabs.open("a", OpenTarget.CURRENT_TAB);

        tabs.open("x", OpenTarget.NEW_TAB);

        assertEquals(List.of("a", "x", "b", "c"), tabs.getIds());
        assertEquals("x", tabs.getActiveId());
    }

    @Test
    void withTheAlwaysNewTabPreferenceAPlainOpenAddsATab() {
        EditorTabsModel tabs = tabsWith("a", "b");
        tabs.open("a", OpenTarget.CURRENT_TAB);
        tabs.setAlwaysNewTab(true);

        tabs.open("x", OpenTarget.CURRENT_TAB);

        assertEquals(List.of("a", "x", "b"), tabs.getIds());
        assertEquals("x", tabs.getActiveId());
    }

    @Test
    void withoutAnActiveTabANoteOpensAtTheEnd() {
        EditorTabsModel tabs = tabsWith("a", "b");
        tabs.remove("b"); // the active one: nothing is active now

        tabs.open("x", OpenTarget.CURRENT_TAB);

        assertEquals(List.of("a", "x"), tabs.getIds());
        assertEquals("x", tabs.getActiveId());
    }

    @Test
    void removingTheActiveTabLeavesNoActiveTabAndTheNeighbourIsThePreviousOne() {
        EditorTabsModel tabs = tabsWith("a", "b", "c");
        tabs.open("b", OpenTarget.CURRENT_TAB);

        assertEquals("a", tabs.neighborOf("b"));
        assertEquals("b", tabs.neighborOf("a"), "the first tab falls back to the next one");
        tabs.remove("b");

        assertEquals(List.of("a", "c"), tabs.getIds());
        assertNull(tabs.getActiveId());
        assertNull(new EditorTabsModel().neighborOf("missing"));
    }

    @Test
    void removingAnInactiveTabKeepsTheActiveOne() {
        EditorTabsModel tabs = tabsWith("a", "b", "c");

        tabs.remove("a");

        assertEquals(List.of("b", "c"), tabs.getIds());
        assertEquals("c", tabs.getActiveId());
    }

    @Test
    void rebindRenamesATabInPlaceAndMergesIntoAnExistingOne() {
        EditorTabsModel tabs = tabsWith("a", "b", "c");
        tabs.open("b", OpenTarget.CURRENT_TAB);

        tabs.rebind("b", "b2");
        assertEquals(List.of("a", "b2", "c"), tabs.getIds());
        assertEquals("b2", tabs.getActiveId());

        // Renamed onto a note that already has a tab: the duplicate disappears.
        tabs.rebind("b2", "c");
        assertEquals(List.of("a", "c"), tabs.getIds());
        assertEquals("c", tabs.getActiveId());
    }

    @Test
    void nullIdsAreIgnoredAndClearEmptiesEverything() {
        EditorTabsModel tabs = tabsWith("a");

        tabs.open(null, OpenTarget.NEW_TAB);
        assertEquals(List.of("a"), tabs.getIds());

        tabs.clear();
        assertTrue(tabs.getIds().isEmpty());
        assertNull(tabs.getActiveId());
        assertFalse(tabs.isOpen("a"));
    }

    /** Tabs opened one after another in new tabs; the last one is active. */
    private static EditorTabsModel tabsWith(String... ids) {
        EditorTabsModel tabs = new EditorTabsModel();
        for (String id : ids) {
            tabs.open(id, OpenTarget.NEW_TAB);
        }
        return tabs;
    }
}
