package com.example.jylos.ui.preferences;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

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
        assertEquals("", UiPreferencesStore.sanitizeAccent("#fff"));
        assertEquals("", UiPreferencesStore.sanitizeAccent("red"));
        assertEquals("", UiPreferencesStore.sanitizeAccent("#7c3aed; -fx-x:1"));
    }
}
