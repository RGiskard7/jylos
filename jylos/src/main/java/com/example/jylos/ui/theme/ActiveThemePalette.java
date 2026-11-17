package com.example.jylos.ui.theme;

import com.example.jylos.util.ThemePalette;

/**
 * The colours of the external theme in use, for what the window's stylesheets do not reach (the
 * editor, the reading view, the graph); {@code null} with the built-in light and dark themes,
 * which keep their own. Set each time the theme is applied, before what depends on it is redrawn.
 *
 * @author Edu Díaz (RGiskard7)
 * @since 2.6.0
 */
public final class ActiveThemePalette {

    private static volatile ThemePalette current;

    private ActiveThemePalette() {
    }

    public static ThemePalette get() {
        return current;
    }

    static void set(ThemePalette palette) {
        current = palette;
    }
}
