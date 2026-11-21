package com.example.jylos.ui.components;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.example.jylos.ui.components.EditorTabsModel.OpenTarget;

/**
 * Pinned tabs, as in Obsidian: they go first, what is opened from one goes to another tab
 * (it keeps what it shows), and closing the others leaves them.
 */
class EditorTabsPinnedTest {

    private static EditorTabsModel tabs(String... ids) {
        EditorTabsModel model = new EditorTabsModel();
        for (String id : ids) {
            model.open(id, OpenTarget.NEW_TAB);
        }
        return model;
    }

    @Test
    void aPinnedTabGoesToTheEndOfThePinnedOnesAndUnpinnedToTheStartOfTheRest() {
        EditorTabsModel model = tabs("a.md", "b.md", "c.md", "d.md");

        model.pin("c.md");
        assertEquals(List.of("c.md", "a.md", "b.md", "d.md"), model.getIds());
        model.pin("d.md");
        assertEquals(List.of("c.md", "d.md", "a.md", "b.md"), model.getIds());
        assertEquals(List.of("c.md", "d.md"), model.pinnedIds());

        model.unpin("c.md");
        assertEquals(List.of("d.md", "c.md", "a.md", "b.md"), model.getIds());
        assertTrue(model.isPinned("d.md"));
        assertFalse(model.isPinned("c.md"));
    }

    @Test
    void whatIsOpenedFromAPinnedTabGoesToANewTabAfterThePinnedOnes() {
        EditorTabsModel model = tabs("p.md", "q.md", "x.md");
        model.pin("p.md");
        model.pin("q.md");
        model.open("p.md", OpenTarget.CURRENT_TAB); // activates p.md

        model.open("y.md", OpenTarget.CURRENT_TAB);

        assertEquals(List.of("p.md", "q.md", "y.md", "x.md"), model.getIds(), "p.md keeps what it shows");
        assertEquals("y.md", model.getActiveId());
        assertFalse(model.isPinned("y.md"), "a new tab is not pinned");
    }

    @Test
    void anUnpinnedTabStillOpensInPlace() {
        EditorTabsModel model = tabs("p.md", "x.md");
        model.pin("p.md");

        model.open("y.md", OpenTarget.CURRENT_TAB); // x.md is active

        assertEquals(List.of("p.md", "y.md"), model.getIds());
    }

    /** Going back to a note a pinned tab shows brings that tab forward instead of taking the note from it. */
    @Test
    void goingBackToANotePinnedElsewhereActivatesItsTab() {
        EditorTabsModel model = tabs("p.md", "x.md");
        model.open("y.md", OpenTarget.CURRENT_TAB); // x.md's tab now shows y.md, back: x.md
        model.open("x.md", OpenTarget.NEW_TAB);
        model.pin("x.md");
        model.open("y.md", OpenTarget.CURRENT_TAB); // y.md's tab is active again, back: x.md

        assertEquals("x.md", model.back());

        assertEquals("x.md", model.getActiveId());
        assertTrue(model.isPinned("x.md"));
        assertEquals(List.of("x.md", "p.md", "y.md"), model.getIds(), "no tab was taken away");
        assertEquals(List.of("x.md"), model.backOf("y.md"), "y.md's tab keeps its history");
    }

    @Test
    void closingTheOthersLeavesThePinnedOnes() {
        EditorTabsModel model = tabs("p.md", "a.md", "b.md", "c.md");
        model.pin("p.md");

        assertEquals(List.of("a.md", "c.md"), model.othersToClose("b.md"));
        assertEquals(List.of("a.md", "b.md", "c.md"), model.othersToClose("p.md"));
    }

    @Test
    void aRenamedPinnedTabStaysPinnedAndAClosedOneIsGone() {
        EditorTabsModel model = tabs("p.md", "a.md");
        model.pin("p.md");

        model.rebind("p.md", "Carpeta/p.md");
        assertEquals(List.of("Carpeta/p.md"), model.pinnedIds());

        model.remove("Carpeta/p.md");
        assertEquals(List.of(), model.pinnedIds());
        model.clear();
        assertEquals(List.of(), model.pinnedIds());
    }
}
