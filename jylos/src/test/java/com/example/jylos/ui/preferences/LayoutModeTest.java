package com.example.jylos.ui.preferences;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.UUID;
import java.util.prefs.Preferences;

import org.junit.jupiter.api.Test;

/**
 * Which layout Jylos starts in. New installs start in the Explorer (one tree, as in
 * Obsidian); people upgrading keep the classic columns they are used to; after that, the
 * user's choice is kept.
 */
class LayoutModeTest {

    @Test
    void aNewInstallStartsInTheExplorerAndAnUpgradeInClassicColumns() {
        assertEquals(LayoutMode.EXPLORER, LayoutMode.resolveDefault(null, false));
        assertEquals(LayoutMode.CLASSIC_COLUMNS, LayoutMode.resolveDefault(null, true));
    }

    @Test
    void aStoredChoiceWinsAndAnUnknownOneIsIgnored() {
        assertEquals(LayoutMode.CLASSIC_STACKED, LayoutMode.resolveDefault("stacked", false));
        assertEquals(LayoutMode.EXPLORER, LayoutMode.resolveDefault("explorer", true));
        assertEquals(LayoutMode.CLASSIC_COLUMNS, LayoutMode.resolveDefault("garbage", true));
        assertEquals(LayoutMode.EXPLORER, LayoutMode.resolveDefault(" ", false));
    }

    @Test
    void theFirstStartDecidesOnceFromWhetherThePreferencesAlreadyHadAnything() throws Exception {
        UiPreferencesStore store = new UiPreferencesStore();
        Preferences fresh = Preferences.userRoot().node("/com/example/jylos/test/" + UUID.randomUUID());
        Preferences upgraded = Preferences.userRoot().node("/com/example/jylos/test/" + UUID.randomUUID());
        try {
            upgraded.put("ui.split.main", "0.22");

            assertEquals(LayoutMode.EXPLORER, store.initLayoutMode(fresh));
            assertEquals(LayoutMode.CLASSIC_COLUMNS, store.initLayoutMode(upgraded));
            // Decided once: the next start reads the stored value, it does not re-guess.
            assertEquals("explorer", fresh.get(UiPreferencesStore.LAYOUT_MODE_KEY, null));
            assertEquals(LayoutMode.EXPLORER, store.initLayoutMode(fresh));
            assertEquals(LayoutMode.EXPLORER, store.loadLayoutMode(fresh));
        } finally {
            fresh.removeNode();
            upgraded.removeNode();
        }
    }

    @Test
    void aSavedChoiceIsLoadedBackAndNothingStoredMeansClassic() throws Exception {
        UiPreferencesStore store = new UiPreferencesStore();
        Preferences prefs = Preferences.userRoot().node("/com/example/jylos/test/" + UUID.randomUUID());
        try {
            assertEquals(LayoutMode.CLASSIC_COLUMNS, store.loadLayoutMode(prefs));
            store.saveLayoutMode(prefs, LayoutMode.CLASSIC_STACKED);
            assertEquals(LayoutMode.CLASSIC_STACKED, store.loadLayoutMode(prefs));
            store.saveLayoutMode(prefs, LayoutMode.EXPLORER);
            assertEquals(LayoutMode.EXPLORER, store.loadLayoutMode(prefs));
        } finally {
            prefs.removeNode();
        }
    }

    @Test
    void theLayoutButtonCyclesThroughTheThreeModes() {
        assertEquals(LayoutMode.CLASSIC_COLUMNS, LayoutMode.EXPLORER.next());
        assertEquals(LayoutMode.CLASSIC_STACKED, LayoutMode.CLASSIC_COLUMNS.next());
        assertEquals(LayoutMode.EXPLORER, LayoutMode.CLASSIC_STACKED.next());
    }
}
