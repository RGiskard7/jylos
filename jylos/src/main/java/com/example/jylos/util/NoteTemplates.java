package com.example.jylos.util;

import java.time.LocalDate;

/**
 * Pure helpers for daily notes. Templates are written by {@code TemplateEngine}.
 *
 * @author Edu Díaz (RGiskard7)
 * @since 2.1.0
 */
public final class NoteTemplates {

    private NoteTemplates() {
    }

    /** Today's date as an ISO {@code yyyy-MM-dd} string — the canonical daily-note title. */
    public static String dailyNoteTitle() {
        return LocalDate.now().toString();
    }
}
