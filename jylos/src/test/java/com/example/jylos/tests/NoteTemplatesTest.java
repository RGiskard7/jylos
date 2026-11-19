package com.example.jylos.tests;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.example.jylos.util.NoteTemplates;

class NoteTemplatesTest {

    @Test
    void dailyNoteTitleIsIsoDate() {
        assertTrue(NoteTemplates.dailyNoteTitle().matches("\\d{4}-\\d{2}-\\d{2}"));
    }
}
