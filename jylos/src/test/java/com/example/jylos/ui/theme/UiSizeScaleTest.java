package com.example.jylos.ui.theme;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/** Rules that size icons and controls in fixed pixels, rewritten for another interface font. */
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
            .relative { -fx-icon-size: 1.2em; -fx-pref-height: 2em; }
            .toolbar .menu-button .ikonli-font-icon { -fx-icon-size: 17px; }
            .tree-cell { -fx-indent: 10px; }
            """;

    @Test
    void atTheDefaultFontTheRulesKeepTheirSizes() {
        assertEquals("""
                .feather-icon { -fx-icon-size: 16px; }
                .nav-btn, .icon-btn { -fx-pref-width: 28px; -fx-min-height: 28px; }
                .toolbar .menu-button .ikonli-font-icon { -fx-icon-size: 17px; }
                .tree-cell { -fx-indent: 10px; }
                """, UiSizeScale.scaledRules(CSS, 1.0, 1.0));
    }

    @Test
    void aLargerFontScalesIconsAndControlSizedDimensionsOnly() {
        // 22 / 13: 16 -> 27, 28 -> 47, 17 -> 29, a tree level's indent 10 -> 17; 250 px panels,
        // 6 px details and em values stay out.
        assertEquals("""
                .feather-icon { -fx-icon-size: 27px; }
                .nav-btn, .icon-btn { -fx-pref-width: 47px; -fx-min-height: 47px; }
                .toolbar .menu-button .ikonli-font-icon { -fx-icon-size: 29px; }
                .tree-cell { -fx-indent: 17px; }
                """, UiSizeScale.scaledRules(CSS, 22.0 / 13, 1.0));
    }

    @Test
    void theIconFactorEnlargesIconsAndTheControlsThatHoldThem() {
        // Icons ×1.25 on top of the font; control sizes follow the larger of the two so the
        // icon still fits its button.
        assertEquals("""
                .feather-icon { -fx-icon-size: 20px; }
                .nav-btn, .icon-btn { -fx-pref-width: 35px; -fx-min-height: 35px; }
                .toolbar .menu-button .ikonli-font-icon { -fx-icon-size: 21px; }
                .tree-cell { -fx-indent: 13px; }
                """, UiSizeScale.scaledRules(CSS, 1.0, 1.25));
    }
}
