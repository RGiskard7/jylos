package com.example.jylos.tests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;

import com.example.jylos.util.NoteTemplates;

class NoteTemplatesTest {

    private static final LocalDateTime FIXED =
            LocalDateTime.of(2026, 6, 14, 9, 30, 15, 123_000_000);

    @Test
    void substitutesAllPlaceholdersWithFixedClock() {
        String body = "# {{title}}\n\nCreated {{date}} at {{time}} ({{datetime}})";
        String out = NoteTemplates.applyPlaceholders(body, "My Note", FIXED);

        assertEquals(
                "# My Note\n\nCreated 2026-06-14 at 09:30:15 (2026-06-14T09:30:15)",
                out);
    }

    @Test
    void dropsSubSecondPrecisionFromTimeAndDatetime() {
        String out = NoteTemplates.applyPlaceholders("{{time}}|{{datetime}}", "x", FIXED);
        assertFalse(out.contains("."), "nanoseconds must not leak into the output: " + out);
        assertEquals("09:30:15|2026-06-14T09:30:15", out);
    }

    @Test
    void nullTitleBecomesEmptyString() {
        assertEquals("Title: ", NoteTemplates.applyPlaceholders("Title: {{title}}", null, FIXED));
    }

    @Test
    void nullContentYieldsEmptyString() {
        assertEquals("", NoteTemplates.applyPlaceholders(null, "ignored", FIXED));
    }

    @Test
    void contentWithoutPlaceholdersIsUnchanged() {
        String body = "plain body, no tokens";
        assertEquals(body, NoteTemplates.applyPlaceholders(body, "T", FIXED));
    }

    @Test
    void systemClockOverloadResolvesDateToken() {
        // Uses the real clock: we only assert the token was consumed, not its value.
        String out = NoteTemplates.applyPlaceholders("d={{date}}", "T");
        assertFalse(out.contains("{{date}}"));
        assertTrue(out.startsWith("d="));
    }

    @Test
    void dailyNoteTitleIsIsoDate() {
        assertTrue(NoteTemplates.dailyNoteTitle().matches("\\d{4}-\\d{2}-\\d{2}"));
    }
}
