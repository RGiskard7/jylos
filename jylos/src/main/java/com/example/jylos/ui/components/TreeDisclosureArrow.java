package com.example.jylos.ui.components;

import org.kordamp.ikonli.javafx.FontIcon;

import javafx.scene.layout.StackPane;

/**
 * JavaFX's {@code TreeCellSkin} caches ONE shared disclosure-arrow width per
 * {@code TreeView}, seeded with a hardcoded fallback of 18px and only ever
 * overwritten once a real disclosure node measures WIDER than the current cached
 * value (never narrower — see {@code TreeCellSkin.layoutChildren}, "RT-19656:
 * default width of default disclosure node"). Left to JavaFX's own default arrow
 * (whose real measured width in this theme is larger than 18px), whichever rows
 * render before the first expandable folder gets measured use the stale 18px
 * fallback while later rows use the corrected, larger value — siblings at the same
 * depth end up indented differently, exactly the skew reported after startup or a
 * tree rebuild.
 *
 * <p>The fix has to land on EXACTLY 18px, not merely "smaller than 18": for a row
 * WITH children, {@code TreeCellSkin} ignores the cache entirely and uses this
 * arrow's own real measured width; for a LEAF row it falls back to the cache
 * (18px, since 18 is never strictly greater than 18 the cache can never grow past
 * it once every arrow measures exactly that). Any other fixed value — including a
 * smaller one — makes branch rows and leaf rows use two different, merely
 * consistently-wrong offsets instead of matching.</p>
 *
 * @author Edu Díaz (RGiskard7)
 * @since 2.6.0
 */
public final class TreeDisclosureArrow {

    /** The one width every arrow measures at the default interface font, see the class comment. */
    public static final double SIZE = 18;
    /** Style class of the arrow's box. */
    public static final String STYLE = "tree-disclosure-arrow";

    private TreeDisclosureArrow() {
    }

    /** A chevron pointing right; rotate it 90 degrees to show an expanded row. */
    public static StackPane create() {
        FontIcon chevron = new FontIcon("fth-chevron-right");
        chevron.setIconSize(12);
        chevron.getStyleClass().add("feather-icon");
        StackPane arrow = new StackPane(chevron);
        // The themes size it too (.tree-disclosure-arrow), so it grows with the interface font.
        arrow.getStyleClass().add(STYLE);
        arrow.setMinSize(SIZE, SIZE);
        arrow.setPrefSize(SIZE, SIZE);
        arrow.setMaxSize(SIZE, SIZE);
        return arrow;
    }
}
