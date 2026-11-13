package com.example.jylos.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Set;

import org.junit.jupiter.api.Test;

/** A new note or folder takes the first free name, as in Obsidian: "Sin título", then "Sin título 1", ... */
class UntitledNamesTest {

    @Test
    void theBaseNameWhenFreeThenTheFirstFreeNumber() {
        assertEquals("Sin título", UntitledNames.next("Sin título", Set.of("Otra")));
        assertEquals("Sin título 1", UntitledNames.next("Sin título", Set.of("Sin título")));
        assertEquals("Sin título 3", UntitledNames.next("Sin título", Set.of("Sin título", "Sin título 1", "Sin título 2")));
        assertEquals("Sin título 1", UntitledNames.next("Sin título", Set.of("sin título")), "names differ only in case on disk");
    }
}
