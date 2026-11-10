package com.example.jylos.ui.components;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.example.jylos.ui.components.EditorTabsModel.OpenTarget;

/**
 * Tabs behave like VS Code: a plain open replaces the note in the current tab, a note that
 * is already open is activated rather than duplicated, and "open in new tab" inserts the
 * tab right after the active one. Before, every opened note added a tab at the end.
 */
class EditorTabsModelTest {

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
