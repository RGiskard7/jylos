package com.example.jylos.ui.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/**
 * The custom accent color preference must only ever hold a safe {@code #rrggbb}
 * value (it is injected into an inline JavaFX style string on the scene root).
 */
class UiPreferencesStoreTest {

    @Test
    void sanitizeAcceptsValidHex() {
        assertEquals("#7c3aed", UiPreferencesStore.sanitizeAccent("#7C3AED"));
        assertEquals("#00ff00", UiPreferencesStore.sanitizeAccent("  #00ff00 "));
    }

    @Test
    void sanitizeRejectsAnythingElse() {
        assertEquals("", UiPreferencesStore.sanitizeAccent(null));
        assertEquals("", UiPreferencesStore.sanitizeAccent(""));
        assertEquals("", UiPreferencesStore.sanitizeAccent("#fff"));            // short form
        assertEquals("", UiPreferencesStore.sanitizeAccent("red"));             // named color
        assertEquals("", UiPreferencesStore.sanitizeAccent("#7c3aed; -fx-x:1")); // injection attempt
    }
}
