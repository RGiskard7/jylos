package com.example.jylos.ui.preferences;

import java.util.Optional;

/**
 * How the main window arranges navigation and notes.
 *
 * <ul>
 * <li>{@link #EXPLORER}: one tree with folders and notes in the sidebar, as in Obsidian.
 * The notes panel only appears to show the results of a tag or a search.</li>
 * <li>{@link #CLASSIC_COLUMNS}: folders, notes list and editor side by side.</li>
 * <li>{@link #CLASSIC_STACKED}: folders above the notes list, beside the editor.</li>
 * </ul>
 */
public enum LayoutMode {
    EXPLORER("explorer"),
    CLASSIC_COLUMNS("columns"),
    CLASSIC_STACKED("stacked");

    private final String key;

    LayoutMode(String key) {
        this.key = key;
    }

    /** The value stored in the preferences. */
    public String key() {
        return key;
    }

    public static Optional<LayoutMode> fromKey(String value) {
        if (value == null) {
            return Optional.empty();
        }
        String trimmed = value.trim();
        for (LayoutMode mode : values()) {
            if (mode.key.equals(trimmed)) {
                return Optional.of(mode);
            }
        }
        return Optional.empty();
    }

    /**
     * The layout to start in: the stored choice if there is a valid one; otherwise the
     * Explorer for a new install and the classic columns for someone upgrading, who should
     * not find the window rearranged without asking.
     */
    public static LayoutMode resolveDefault(String stored, boolean existingInstall) {
        return fromKey(stored).orElse(existingInstall ? CLASSIC_COLUMNS : EXPLORER);
    }

    /** The next mode, for commands that cycle through them. */
    public LayoutMode next() {
        LayoutMode[] all = values();
        return all[(ordinal() + 1) % all.length];
    }

    public boolean isExplorer() {
        return this == EXPLORER;
    }

    public boolean isStacked() {
        return this == CLASSIC_STACKED;
    }
}
