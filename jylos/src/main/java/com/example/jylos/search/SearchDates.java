package com.example.jylos.search;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.regex.Pattern;

/**
 * Lightweight date matching for the {@code created:} / {@code modified:} operators.
 *
 * <p>Supported tokens (no complex ranges in this phase):
 * {@code today}, {@code yesterday}, {@code last-week}, {@code last-month},
 * {@code YYYY}, {@code YYYY-MM}, {@code YYYY-MM-DD}.</p>
 *
 * <p>Note timestamps are stored as ISO-8601 instants (both storage modes use
 * {@code DateTimeFormatter.ISO_INSTANT}); {@link #parseNoteDate(String)} also tolerates
 * a bare {@code yyyy-MM-dd} prefix, degrading to {@code null} (never throwing) on
 * anything it cannot read.</p>
 *
 * @author Edu Díaz (RGiskard7)
 * @since 2.1.0
 */
public final class SearchDates {

    private static final Pattern YMD = Pattern.compile("\\d{4}(-\\d{2}(-\\d{2})?)?");

    private SearchDates() {
    }

    /** True if {@code token} is a date token this matcher understands. */
    public static boolean isValidToken(String token) {
        if (token == null) {
            return false;
        }
        String t = token.trim().toLowerCase(java.util.Locale.ROOT);
        return switch (t) {
            case "today", "yesterday", "last-week", "last-month" -> true;
            default -> YMD.matcher(t).matches();
        };
    }

    /**
     * Parses a stored note timestamp into a {@link LocalDate} in the system zone, or
     * {@code null} when it is blank/unparseable.
     */
    public static LocalDate parseNoteDate(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String s = raw.trim();
        try {
            return Instant.parse(s).atZone(ZoneId.systemDefault()).toLocalDate();
        } catch (Exception ignored) {
            // fall through to a lenient yyyy-MM-dd prefix parse
        }
        if (s.length() >= 10) {
            try {
                return LocalDate.parse(s.substring(0, 10));
            } catch (Exception ignored) {
                return null;
            }
        }
        return null;
    }

    /**
     * Tests a stored note timestamp against a date token, relative to today.
     *
     * @return {@code true} when the note date satisfies the token; {@code false} when
     *         the token is invalid or the note date cannot be parsed
     */
    public static boolean matches(String token, String noteDateRaw) {
        if (!isValidToken(token)) {
            return false;
        }
        LocalDate date = parseNoteDate(noteDateRaw);
        if (date == null) {
            return false;
        }
        LocalDate today = LocalDate.now();
        String t = token.trim().toLowerCase(java.util.Locale.ROOT);
        return switch (t) {
            case "today" -> date.isEqual(today);
            case "yesterday" -> date.isEqual(today.minusDays(1));
            case "last-week" -> !date.isBefore(today.minusDays(7)) && !date.isAfter(today);
            case "last-month" -> !date.isBefore(today.minusDays(30)) && !date.isAfter(today);
            default -> date.toString().startsWith(t); // YYYY / YYYY-MM / YYYY-MM-DD prefix
        };
    }
}
