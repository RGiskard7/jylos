package com.example.jylos.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.junit.jupiter.api.Test;

import com.google.gson.JsonParser;

/**
 * Dates are written as Obsidian writes them: the same symbols, the same names of months and
 * days in the language of the interface, the same weeks and the same quirks. The expected
 * texts are moment.js's own, the version Obsidian ships, run on these dates and formats.
 */
class MomentFormatTest {

    private static final ZoneId MADRID = ZoneId.of("Europe/Madrid");

    @Test
    void everyFormatGivesWhatObsidianGives() throws Exception {
        List<String> wrong = new ArrayList<>();
        int checked = 0;
        try (InputStream in = MomentFormatTest.class.getResourceAsStream("moment-format-expected.tsv");
                BufferedReader lines = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            for (String line = lines.readLine(); line != null; line = lines.readLine()) {
                if (line.startsWith("#") || line.isBlank()) {
                    continue;
                }
                String[] cols = line.split("\t");
                Locale locale = Locale.forLanguageTag(cols[0]);
                ZonedDateTime when = LocalDateTime.parse(cols[1]).atZone(MADRID);
                String format = JsonParser.parseString(cols[2]).getAsString();
                String expected = JsonParser.parseString(cols[3]).getAsString();
                String actual = MomentFormat.format(when, format, locale);
                checked++;
                if (!expected.equals(actual)) {
                    wrong.add(cols[0] + " " + cols[1] + " " + format + ": expected [" + expected + "] but was ["
                            + actual + "]");
                }
            }
        }
        assertTrue(checked > 500, "the whole table was read: " + checked);
        assertEquals(List.of(), wrong, wrong.size() + " of " + checked + " differ");
    }

    /**
     * Jylos carries the names of the languages of its interface; any other is written in
     * English, as moment does with a language it has no data for.
     */
    @Test
    void anotherLanguageIsWrittenInEnglish() {
        ZonedDateTime when = LocalDateTime.parse("2026-11-19T14:05:00").atZone(MADRID);

        assertEquals("Thursday 19th November", MomentFormat.format(when, "dddd Do MMMM", Locale.forLanguageTag("xx")));
        assertEquals("Thursday 19th November", MomentFormat.format(when, "dddd Do MMMM", Locale.GERMAN));
    }

    @Test
    void anEmptyFormatIsMomentsDefault() {
        ZonedDateTime when = LocalDateTime.parse("2026-11-19T14:05:09").atZone(MADRID);

        assertEquals("2026-11-19T14:05:09+01:00", MomentFormat.format(when, "", Locale.ENGLISH));
    }
}
