package com.example.jylos.ui.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Back and forward go through the notes opened, as a browser does: opening a note records the
 * one left, going back makes the current one reachable forward, opening another note forgets
 * what was forward, the back/forward jumps themselves are not recorded, and only the last 50
 * notes are kept.
 */
class NavigationHistorySupportTest {

    @Test
    void backAndForwardWalkTheNotesOpened() {
        NavigationHistorySupport history = new NavigationHistorySupport();
        assertFalse(history.canGoBack());

        history.record("A");
        history.record("B");          // A → B → C (C open)
        assertTrue(history.canGoBack());
        assertFalse(history.canGoForward());

        assertEquals("B", history.back("C"));
        assertEquals("A", history.back("B"));
        assertNull(history.back("A"), "nothing before A");
        assertTrue(history.canGoForward());
        assertEquals("B", history.forward("A"));

        history.record("B");          // B → D: what was forward (C) is forgotten
        assertFalse(history.canGoForward());
        assertEquals("B", history.back("D"));
    }

    @Test
    void theJumpsThemselvesAreNotRecorded() {
        NavigationHistorySupport history = new NavigationHistorySupport();
        history.record("A");
        history.jumping(() -> history.record("B"));
        assertEquals("A", history.back("C"));
        assertFalse(history.canGoBack());
    }

    @Test
    void onlyTheLastFiftyAreKept() {
        NavigationHistorySupport history = new NavigationHistorySupport();
        for (int i = 0; i < 60; i++) {
            history.record("n" + i);
        }
        String oldest = null;
        String current = "end";
        int steps = 0;
        for (String id; (id = history.back(current)) != null; current = id) {
            oldest = id;
            steps++;
        }
        assertEquals(50, steps);
        assertEquals("n10", oldest);
    }
}
