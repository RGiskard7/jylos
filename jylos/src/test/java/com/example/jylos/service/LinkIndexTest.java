package com.example.jylos.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.example.jylos.data.models.Note;

/**
 * The vault-wide link index: each note has a path in the vault (whatever the storage), and a
 * link from one note resolves to the id of another, aliases from the note properties included.
 */
class LinkIndexTest {

    @Test
    void resolvesALinkFromOneNoteToTheIdOfAnother() {
        // SQLite-like ids: opaque, the path comes from the folder chain.
        Map<String, String> paths = Map.of(
                "u1", "Proyectos/Reunión.md",
                "u2", "Personal/Reunión.md",
                "u3", "Personal/Diario.md");
        LinkIndex index = LinkIndex.build(List.of(note("u1", "Reunión"), note("u2", "Reunión"), note("u3", "Diario")),
                n -> paths.get(n.getId()));

        assertEquals(Optional.of("u2"), index.resolveId("Reunión", "u3"));
        assertEquals(Optional.of("u1"), index.resolveId("Proyectos/Reunión", "u3"));
        assertEquals(Optional.empty(), index.resolveId("Nada", "u3"));
        assertEquals("Personal/Diario.md", index.pathOf("u3"));
    }

    /**
     * What [[ suggests: every note, also two of the same name, with its folder and the shortest
     * link that names it unambiguously, and its aliases; by name, then folder.
     */
    @Test
    void suggestsEveryNoteWithItsFolderItsShortestLinkAndItsAliases() {
        Note diary = note("Personal/Diario.md", "Diario");
        diary.setCustomProperties(Map.of("aliases", "[Diario personal]"));
        LinkIndex index = LinkIndex.build(List.of(note("Proyectos/Reunión.md", "Reunión"),
                note("Personal/reunión.md", "reunión"), diary, note("Lienzo.canvas", "Lienzo"),
                note("Plan.md", "Plan")), Note::getId);

        assertEquals(List.of(
                "Diario | Personal | Diario | [Diario personal]",
                "Lienzo.canvas |  | Lienzo.canvas | []",
                "Plan |  | Plan | []",
                "reunión | Personal | Personal/reunión | []",
                "Reunión | Proyectos | Proyectos/Reunión | []"),
                index.suggestions().stream().map(s -> s.name() + " | " + s.folder() + " | " + s.link() + " | "
                        + s.aliases()).toList());
        assertEquals(List.of(), LinkIndex.EMPTY.suggestions());
    }

    @Test
    void aliasesComeFromTheAliasesPropertyAsAListOrASingleValue() {
        Note list = note("Ideas/Objetivo.md", "Objetivo");
        list.setCustomProperties(Map.of("aliases", "[Meta, \"Mi fin\"]"));
        Note single = note("Gente/Ana.md", "Ana");
        single.setCustomProperties(Map.of("alias", "Anita"));
        Note source = note("Inicio.md", "Inicio");
        LinkIndex index = LinkIndex.build(List.of(list, single, source), Note::getId);

        assertEquals(Optional.of("Ideas/Objetivo.md"), index.resolveId("meta", "Inicio.md"));
        assertEquals(Optional.of("Ideas/Objetivo.md"), index.resolveId("Mi fin", "Inicio.md"));
        assertEquals(Optional.of("Gente/Ana.md"), index.resolveId("Anita", "Inicio.md"));
    }

    /**
     * Resolving every link of the vault at once (the backlink map) answers exactly as one link
     * at a time: the same name from two folders, relative links, a heading of the note itself,
     * aliases and notes outside the index.
     */
    @Test
    void resolvingManyLinksAtOnceAnswersAsResolvingThemOneByOne() {
        Note alias = note("Ideas/Objetivo.md", "Objetivo");
        alias.setCustomProperties(Map.of("aliases", "[Meta]"));
        List<Note> notes = List.of(note("A/Reunión.md", "Reunión"), note("B/Reunión.md", "Reunión"),
                note("A/Diario.md", "Diario"), note("B/Diario.md", "Diario"), note("B/Sub/Hoja.md", "Hoja"), alias);
        LinkIndex index = LinkIndex.build(notes, Note::getId);
        List<String> sources = List.of("A/Diario.md", "B/Diario.md", "B/Sub/Hoja.md", "Fuera.md");
        List<String> links = List.of("Reunión", "Reunión#Tema", "Reunión|otra", "./Reunión", "../Reunión", "#Tema",
                "B/Reunión", "Meta", "Nada", " ", "|solo", "Hoja", "./Hoja", "Sub/Hoja", "Objetivo", "../Diario");

        java.util.function.BiFunction<String, String, Optional<String>> batch = index.batchResolver();
        for (String source : sources) {
            for (String link : links) {
                assertEquals(index.resolveId(link, source), batch.apply(link, source), link + " from " + source);
            }
        }
        // The folder decides: the same link from A and from B points to different notes.
        assertEquals(Optional.of("A/Reunión.md"), batch.apply("Reunión", "A/Diario.md"));
        assertEquals(Optional.of("B/Reunión.md"), batch.apply("Reunión", "B/Diario.md"));
    }

    @Test
    void aSourceNoteNotInTheIndexResolvesFromTheRoot() {
        LinkIndex index = LinkIndex.build(List.of(note("A/Nota.md", "Nota")), Note::getId);
        assertEquals(Optional.of("A/Nota.md"), index.resolveId("Nota", null));
        assertEquals(Optional.of("A/Nota.md"), index.resolveId("Nota", "desconocida"));
    }

    private static Note note(String id, String title) {
        Note note = new Note(title, "");
        note.setId(id);
        return note;
    }
}
