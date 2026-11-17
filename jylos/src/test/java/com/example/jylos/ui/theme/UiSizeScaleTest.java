package com.example.jylos.ui.theme;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/** Rules that size icons, controls and their spacing in fixed pixels, rewritten for another interface font. */
class UiSizeScaleTest {

    private static final String CSS = """
            /* a comment { -fx-icon-size: 99; } */
            .feather-icon {
                -fx-icon-color: red;
                -fx-icon-size: 16;
            }
            .nav-btn, .icon-btn {
                -fx-pref-width: 28px;
                -fx-min-height: 28px;
                -fx-padding: 4;
            }
            .sidebar { -fx-pref-width: 250px; }
            .tiny { -fx-min-width: 6px; }
            .relative { -fx-icon-size: 1.2em; -fx-pref-height: 2em; -fx-padding: 0.5em; }
            .toolbar .menu-button .ikonli-font-icon { -fx-icon-size: 17px; }
            .tree-cell { -fx-indent: 10px; }
            .unitless-btn { -fx-pref-width: 28; -fx-max-height: 28; }
            .box { -fx-spacing: 6; -fx-padding: 4 8px 0 1em; }
            .tiles { -fx-pref-tile-width: 150px; -fx-pref-tile-height: 96; -fx-hgap: 12; -fx-vgap: 12px; }
            """;

    @Test
    void atTheDefaultFontTheRulesKeepTheirSizes() {
        assertEquals("""
                .feather-icon { -fx-icon-size: 16px; }
                .nav-btn, .icon-btn { -fx-pref-width: 28px; -fx-min-height: 28px; -fx-padding: 4px; }
                .toolbar .menu-button .ikonli-font-icon { -fx-icon-size: 17px; }
                .tree-cell { -fx-indent: 10px; }
                .unitless-btn { -fx-pref-width: 28px; -fx-max-height: 28px; }
                .box { -fx-spacing: 6px; -fx-padding: 4px 8px 0px 1em; }
                .tiles { -fx-pref-tile-width: 150px; -fx-pref-tile-height: 96px; -fx-hgap: 12px; -fx-vgap: 12px; }
                """, UiSizeScale.scaledRules(CSS, 1.0, 1.0));
    }

    @Test
    void aLargerFontScalesIconsControlsAndTheirSpacing() {
        // 22 / 13: 16 -> 27, 28 -> 47, 17 -> 29, indent 10 -> 17, padding 4 -> 7 and 8 -> 14,
        // spacing 6 -> 10, gaps 12 -> 20, tiles 150 -> 254 and 96 -> 162. Panel widths (250 px),
        // small details (6 px) and em values stay as they are.
        assertEquals("""
                .feather-icon { -fx-icon-size: 27px; }
                .nav-btn, .icon-btn { -fx-pref-width: 47px; -fx-min-height: 47px; -fx-padding: 7px; }
                .toolbar .menu-button .ikonli-font-icon { -fx-icon-size: 29px; }
                .tree-cell { -fx-indent: 17px; }
                .unitless-btn { -fx-pref-width: 47px; -fx-max-height: 47px; }
                .box { -fx-spacing: 10px; -fx-padding: 7px 14px 0px 1em; }
                .tiles { -fx-pref-tile-width: 254px; -fx-pref-tile-height: 162px; -fx-hgap: 20px; -fx-vgap: 20px; }
                """, UiSizeScale.scaledRules(CSS, 22.0 / 13, 1.0));
    }

    @Test
    void theIconFactorEnlargesIconsAndTheControlsThatHoldThemButNotTheSpacing() {
        // Icons ×1.25 on top of the font; control sizes follow the larger of the two so the
        // icon still fits its button. Spacing and padding follow the text, which did not change.
        assertEquals("""
                .feather-icon { -fx-icon-size: 20px; }
                .nav-btn, .icon-btn { -fx-pref-width: 35px; -fx-min-height: 35px; -fx-padding: 4px; }
                .toolbar .menu-button .ikonli-font-icon { -fx-icon-size: 21px; }
                .tree-cell { -fx-indent: 13px; }
                .unitless-btn { -fx-pref-width: 35px; -fx-max-height: 35px; }
                .box { -fx-spacing: 6px; -fx-padding: 4px 8px 0px 1em; }
                .tiles { -fx-pref-tile-width: 150px; -fx-pref-tile-height: 96px; -fx-hgap: 12px; -fx-vgap: 12px; }
                """, UiSizeScale.scaledRules(CSS, 1.0, 1.25));
    }
}
