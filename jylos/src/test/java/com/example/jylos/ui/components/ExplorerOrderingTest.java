package com.example.jylos.ui.components;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.example.jylos.data.models.Folder;
import com.example.jylos.data.models.Note;
import com.example.jylos.data.models.interfaces.Component;
import com.example.jylos.service.NoteService.SortOption;

/**
 * Order of the children of one folder in the Explorer tree: folders first, by name; then
 * notes in the order the user picked. Names compare the way people read them: "Nota 2"
 * before "Nota 10", and case or accents do not push "Árbol" behind "banana".
 */
class ExplorerOrderingTest {

    @Test
    void foldersComeFirstEvenWhenANoteSortsBeforeThem() {
        List<Component> ordered = ExplorerOrdering.order(
                List.of(folder("Zeta"), folder("Beta")),
                List.of(note("Alfa", null, null)),
                SortOption.TITLE_ASC);

        assertEquals(List.of("Beta", "Zeta", "Alfa"), titles(ordered));
    }

    @Test
    void numbersInsideNamesCompareByValue() {
        List<Component> ordered = ExplorerOrdering.order(
                List.of(folder("Capítulo 10"), folder("Capítulo 9")),
                List.of(note("Nota 10", null, null), note("Nota 2", null, null), note("Nota 1", null, null)),
                SortOption.TITLE_ASC);

        assertEquals(List.of("Capítulo 9", "Capítulo 10", "Nota 1", "Nota 2", "Nota 10"), titles(ordered));
    }

    @Test
    void caseAndAccentsDoNotPushANameToTheEnd() {
        List<Component> ordered = ExplorerOrdering.order(
                List.of(),
                List.of(note("banana", null, null), note("Árbol", null, null), note("apple", null, null)),
                SortOption.TITLE_ASC);

        assertEquals(List.of("apple", "Árbol", "banana"), titles(ordered));
    }

    @Test
    void titleDescendingReversesNotesButNotFolders() {
        List<Component> ordered = ExplorerOrdering.order(
                List.of(folder("B"), folder("A")),
                List.of(note("x1", null, null), note("x3", null, null), note("x2", null, null)),
                SortOption.TITLE_DESC);

        assertEquals(List.of("A", "B", "x3", "x2", "x1"), titles(ordered));
    }

    @Test
    void modifiedNewestFallsBackToTheCreationDateAndBreaksTiesByName() {
        List<Component> ordered = ExplorerOrdering.order(
                List.of(),
                List.of(
                        note("vieja", "2026-01-01 10:00:00", "2026-01-02 10:00:00"),
                        note("sin editar", "2026-03-01 10:00:00", null),
                        note("b empate", "2026-01-01 10:00:00", "2026-02-01 10:00:00"),
                        note("a empate", "2026-01-01 10:00:00", "2026-02-01 10:00:00")),
                SortOption.MODIFIED_NEWEST);

        assertEquals(List.of("sin editar", "a empate", "b empate", "vieja"), titles(ordered));
    }

    @Test
    void createdOldestPutsTheOldestFirst() {
        List<Component> ordered = ExplorerOrdering.order(
                List.of(),
                List.of(
                        note("nueva", "2026-05-01 10:00:00", null),
                        note("antigua", "2025-05-01 10:00:00", null)),
                SortOption.CREATED_OLDEST);

        assertEquals(List.of("antigua", "nueva"), titles(ordered));
    }

    @Test
    void missingTitlesAndDatesDoNotFail() {
        List<Component> ordered = ExplorerOrdering.order(
                List.of(folder(null), folder("A")),
                List.of(note(null, null, null), note("b", null, null)),
                SortOption.MODIFIED_NEWEST);

        assertEquals(4, ordered.size());
        assertEquals("A", ordered.get(1).getTitle());
    }

    private static Folder folder(String title) {
        return new Folder(title == null ? "id-null" : "id-" + title, title);
    }

    private static Note note(String title, String created, String modified) {
        return new Note(title, "", created, modified);
    }

    private static List<String> titles(List<Component> components) {
        return components.stream().map(Component::getTitle).toList();
    }
}
