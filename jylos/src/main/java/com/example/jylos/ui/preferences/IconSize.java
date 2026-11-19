package com.example.jylos.ui.preferences;

/**
 * How large icons are drawn, on top of the interface font: an icon's size already follows the
 * font, and this multiplies it, for larger (or smaller) icons with the same text.
 *
 * @author Edu Díaz (RGiskard7)
 * @since 2.6.0
 */
public enum IconSize {
    SMALL("small", 0.9),
    NORMAL("normal", 1.0),
    LARGE("large", 1.25),
    EXTRA_LARGE("extra_large", 1.5);

    private final String key;
    private final double factor;

    IconSize(String key, double factor) {
        this.key = key;
        this.factor = factor;
    }

    /** The value stored in the preferences. */
    public String key() {
        return key;
    }

    /** What icon sizes are multiplied by. */
    public double factor() {
        return factor;
    }

    /** The choice stored as {@code value}; {@link #NORMAL} if there is none or it is unknown. */
    public static IconSize fromKey(String value) {
        if (value != null) {
            for (IconSize size : values()) {
                if (size.key.equals(value.trim())) {
                    return size;
                }
            }
        }
        return NORMAL;
    }
}
