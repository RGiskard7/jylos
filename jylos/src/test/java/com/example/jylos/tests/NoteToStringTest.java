package com.example.jylos.tests;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.example.jylos.data.models.Note;

/**
 * {@code toString()} ends up in logs and assertion messages. It must identify the note
 * without dumping its body, which may be the plaintext of a private note.
 */
class NoteToStringTest {

    @Test
    void identifiesTheNoteWithoutItsContent() {
        Note note = new Note("Carpeta/diario.md", "diario", "SECRETO del cuerpo");
        String text = note.toString();
        assertTrue(text.contains("Carpeta/diario.md"), text);
        assertTrue(text.contains("diario"), text);
        assertFalse(text.contains("SECRETO"), "the body must never be part of toString(): " + text);
    }
}
