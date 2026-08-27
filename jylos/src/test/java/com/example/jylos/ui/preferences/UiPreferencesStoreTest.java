package com.example.jylos.ui.preferences;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

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

    @Test
    void livePreviewDefaultsToEnabledAndPersistsSelection() throws BackingStoreException {
        UiPreferencesStore store = new UiPreferencesStore();
        Preferences prefs = Preferences.userRoot().node("/com/example/jylos/test/" + UUID.randomUUID());
        try {
            assertTrue(store.loadLivePreviewEnabled(prefs));
            store.saveLivePreviewEnabled(prefs, false);
            assertFalse(store.loadLivePreviewEnabled(prefs));
            assertFalse(store.load(prefs).livePreviewEnabled());
        } finally {
            prefs.removeNode();
        }
    }

    @Test
    void readableLineLengthDefaultsToDisabledAndPersistsSelection() throws BackingStoreException {
        UiPreferencesStore store = new UiPreferencesStore();
        Preferences prefs = Preferences.userRoot().node("/com/example/jylos/test/" + UUID.randomUUID());
        try {
            assertFalse(store.loadReadableLineLength(prefs));
            assertFalse(store.load(prefs).readableLineLength());

            store.save(prefs, new UiPreferencesStore.UiPreferencesData(
                    true, 2000, UiPreferencesStore.THEME_SOURCE_BUILTIN, "", 2, 14, "", true, true, 14));

            assertTrue(store.loadReadableLineLength(prefs));
            assertTrue(store.load(prefs).readableLineLength());
        } finally {
            prefs.removeNode();
        }
    }

    @Test
    void contentFontSizeDefaultsAndPersistsIndependentlyFromInterfaceFontSize() throws BackingStoreException {
        UiPreferencesStore store = new UiPreferencesStore();
        Preferences prefs = Preferences.userRoot().node("/com/example/jylos/test/" + UUID.randomUUID());
        try {
            assertEquals(UiPreferencesStore.DEFAULT_CONTENT_FONT_SIZE, store.loadContentFontSize(prefs));
            assertEquals(UiPreferencesStore.DEFAULT_CONTENT_FONT_SIZE, store.load(prefs).contentFontSize());

            store.saveContentFontSize(prefs, 20);

            assertEquals(20, store.loadContentFontSize(prefs));
            assertEquals(20, store.load(prefs).contentFontSize());
            // The interface font size (a separate preference/target) must not move.
            assertEquals(UiPreferencesStore.DEFAULT_UI_FONT_SIZE, store.load(prefs).uiFontSize());

            store.saveContentFontSize(prefs, 999);
            assertEquals(UiPreferencesStore.MAX_CONTENT_FONT_SIZE, store.loadContentFontSize(prefs),
                    "out-of-range values must be clamped, not stored as-is");
        } finally {
            prefs.removeNode();
        }
    }

    @Test
    void aggregatePreferencesPersistDefaultEditingMode() throws BackingStoreException {
        UiPreferencesStore store = new UiPreferencesStore();
        Preferences prefs = Preferences.userRoot().node("/com/example/jylos/test/" + UUID.randomUUID());
        try {
            store.save(prefs, new UiPreferencesStore.UiPreferencesData(
                    true, 2000, UiPreferencesStore.THEME_SOURCE_BUILTIN, "", 2, 14, "", false, false, 14));

            assertFalse(store.load(prefs).livePreviewEnabled());
        } finally {
            prefs.removeNode();
        }
    }
}
