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
