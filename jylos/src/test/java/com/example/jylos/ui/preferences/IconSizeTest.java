package com.example.jylos.ui.preferences;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.UUID;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

import org.junit.jupiter.api.Test;

/** The icon size choice: small, normal, large or extra large, on top of the interface font, and remembered. */
class IconSizeTest {

    @Test
    void eachChoiceMultipliesTheIcons() {
        assertEquals(0.9, IconSize.SMALL.factor());
        assertEquals(1.0, IconSize.NORMAL.factor());
        assertEquals(1.25, IconSize.LARGE.factor());
        assertEquals(1.5, IconSize.EXTRA_LARGE.factor());
    }

    @Test
    void theChoicesGoFromSmallToExtraLarge() {
        assertEquals(List.of(IconSize.SMALL, IconSize.NORMAL, IconSize.LARGE, IconSize.EXTRA_LARGE),
                List.of(IconSize.values()));
        assertEquals(IconSize.EXTRA_LARGE, IconSize.fromKey("extra_large"));
    }

    @Test
    void anUnknownOrMissingValueIsNormal() {
        assertEquals(IconSize.LARGE, IconSize.fromKey("large"));
        assertEquals(IconSize.NORMAL, IconSize.fromKey(null));
        assertEquals(IconSize.NORMAL, IconSize.fromKey("huge"));
    }

    @Test
    void theChoiceIsRememberedAndStartsAsNormal() throws BackingStoreException {
        UiPreferencesStore store = new UiPreferencesStore();
        Preferences prefs = Preferences.userRoot().node("/com/example/jylos/test/" + UUID.randomUUID());
        try {
            assertEquals(IconSize.NORMAL, store.loadIconSize(prefs));
            store.saveIconSize(prefs, IconSize.LARGE);
            assertEquals(IconSize.LARGE, store.loadIconSize(prefs));
            prefs.put(UiPreferencesStore.ICON_SIZE_KEY, "nonsense");
            assertEquals(IconSize.NORMAL, store.loadIconSize(prefs));
        } finally {
            prefs.removeNode();
        }
    }
}
