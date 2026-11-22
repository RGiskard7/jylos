package com.example.jylos.ui.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.ZoneId;
import java.util.Locale;

import org.junit.jupiter.api.Test;

import com.example.jylos.data.models.Note;

/**
 * What a note's tab says when the pointer rests on it: its path, and when it was created and
 * modified, in the interface's language and the computer's time zone. The dates the right
 * panel's note info used to show.
 */
class TabDescriptionTest {

    private static final ZoneId MADRID = ZoneId.of("Europe/Madrid");

    private static Note note(String id, String created, String modified) {
        Note note = new Note(id, "x", "");
        note.setCreatedDate(created);
        note.setModifiedDate(modified);
        return note;
    }

    @Test
    void itSaysThePathAndTheDatesInTheLanguageAndTimeZoneOfTheUser() {
        Note note = note("Proyectos/Plan.md", "2026-11-21T09:05:00Z", "2026-11-22T23:30:00Z");

        String text = TabsSupport.describe(note, key -> key.equals("tab.about_created") ? "Creada: {0}" : "Modificada: {0}",
                Locale.forLanguageTag("es"), MADRID);

        assertEquals("Proyectos/Plan.md\nCreada: 21 nov 2026, 10:05\nModificada: 23 nov 2026, 0:30", text);
    }

    /** A store that keeps dates otherwise (a local date and time, a date alone) still reads; what does not parse is shown as it is. */
    @Test
    void otherWaysOfWritingTheDateAreReadAndAMissingOneIsLeftOut() {
        Locale en = Locale.forLanguageTag("en");
        // The JDK puts a narrow no-break space before AM, as the CLDR says.
        assertEquals("A.md\nC Nov 21, 2026, 9:05\u202fAM\nM Nov 22, 2026",
                TabsSupport.describe(note("A.md", "2026-11-21 09:05:00", "2026-11-22"),
                        key -> key.endsWith("created") ? "C {0}" : "M {0}", en, MADRID));
        assertEquals("A.md\nM ayer", TabsSupport.describe(note("A.md", null, "ayer"),
                key -> key.endsWith("created") ? "C {0}" : "M {0}", en, MADRID));
        assertEquals(null, TabsSupport.describe(null, key -> key, en, MADRID));
    }
}
