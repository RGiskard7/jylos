package com.example.jylos.data.models;

import java.util.regex.Pattern;

/**
 * Represents a single YAML frontmatter property with its inferred type.
 *
 * <p>Used by the editor's properties panel to display and edit Obsidian-compatible
 * YAML frontmatter fields that are not part of the fixed {@link Note} schema
 * (e.g. {@code aliases}, {@code date}, {@code priority}, user-defined fields).</p>
 *
 * <p>Type is inferred heuristically from the raw YAML string value at parse time.
 * Storing the raw string ensures lossless round-trips through save/load cycles.</p>
 *
 * @param key      YAML key, never null or blank
 * @param type     inferred property type
 * @param rawValue raw YAML scalar or inline-array value, as stored on disk
 *
 * @author Edu Díaz (RGiskard7)
 * @since 1.3.0
 */
public record NoteProperty(String key, PropertyType type, String rawValue) {

    private static final Pattern DATE_PATTERN =
            Pattern.compile("\\d{4}-\\d{2}-\\d{2}(T\\d{2}:\\d{2}(:\\d{2})?Z?)?");
    private static final Pattern NUMBER_PATTERN =
            Pattern.compile("-?\\d+(\\.\\d+)?");

    /** Display icon returned by {@link #icon()}. */
    public enum PropertyType {
        TEXT,
        NUMBER,
        BOOLEAN,
        DATE,
        DATETIME,
        LIST
    }

    /**
     * Infers a {@link PropertyType} from the raw string value produced by the YAML parser.
     *
     * @param rawValue the raw YAML string value (may be null)
     * @return the most appropriate type
     */
    public static PropertyType inferType(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            return PropertyType.TEXT;
        }
        String v = rawValue.trim();

        if ("true".equalsIgnoreCase(v) || "false".equalsIgnoreCase(v)) {
            return PropertyType.BOOLEAN;
        }

        if (v.startsWith("[") && v.endsWith("]")) {
            return PropertyType.LIST;
        }

        if (NUMBER_PATTERN.matcher(v).matches()) {
            return PropertyType.NUMBER;
        }

        if (DATE_PATTERN.matcher(v).matches()) {
            return v.contains("T") ? PropertyType.DATETIME : PropertyType.DATE;
        }

        return PropertyType.TEXT;
    }

    /**
     * Creates a {@link NoteProperty} whose type is inferred from the raw value.
     *
     * @param key      YAML key
     * @param rawValue raw YAML value string
     * @return a new NoteProperty
     */
    public static NoteProperty of(String key, String rawValue) {
        return new NoteProperty(key, inferType(rawValue), rawValue != null ? rawValue : "");
    }

    /**
     * Returns a single Unicode icon character for the property type, suitable
     * for use as a compact visual hint in the UI.
     *
     * @return icon character
     */
    public String icon() {
        return switch (type) {
            case BOOLEAN  -> "☑";
            case NUMBER   -> "#";
            case DATE     -> "📅";
            case DATETIME -> "🕐";
            case LIST     -> "≡";
            default       -> "T";
        };
    }
}
