package com.example.jylos.util;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Pure helpers for note templates. Kept out of the UI controllers so the
 * placeholder substitution is unit-testable and reusable (daily notes, "new
 * note from template", plugins).
 *
 * @author Edu Díaz (RGiskard7)
 * @since 2.1.0
 */
public final class NoteTemplates {

    private NoteTemplates() {
    }

    /**
     * Replaces the supported placeholders in a template body:
     * <ul>
     *   <li>{@code {{title}}} — the note title (empty string when {@code null})</li>
     *   <li>{@code {{date}}} — current date, ISO {@code yyyy-MM-dd}</li>
     *   <li>{@code {{time}}} — current time, ISO {@code HH:mm:ss}</li>
     *   <li>{@code {{datetime}}} — current date-time, ISO {@code yyyy-MM-ddTHH:mm:ss}</li>
     * </ul>
     * The substitution uses the supplied {@code now} so callers (and tests) control
     * the clock; the {@link #applyPlaceholders(String, String)} overload uses the
     * system clock.
     *
     * @param content template body (returns {@code ""} when {@code null})
     * @param title   value for {@code {{title}}}
     * @param now     reference instant for the date/time placeholders
     * @return the body with every placeholder substituted
     */
    public static String applyPlaceholders(String content, String title, LocalDateTime now) {
        if (content == null) {
            return "";
        }
        return content
                .replace("{{title}}", title != null ? title : "")
                .replace("{{date}}", now.toLocalDate().toString())
                .replace("{{time}}", now.toLocalTime().withNano(0).toString())
                .replace("{{datetime}}", now.withNano(0).toString());
    }

    /** Convenience overload using the system clock; see {@link #applyPlaceholders(String, String, LocalDateTime)}. */
    public static String applyPlaceholders(String content, String title) {
        return applyPlaceholders(content, title, LocalDateTime.now());
    }

    /** Today's date as an ISO {@code yyyy-MM-dd} string — the canonical daily-note title. */
    public static String dailyNoteTitle() {
        return LocalDate.now().toString();
    }
}
