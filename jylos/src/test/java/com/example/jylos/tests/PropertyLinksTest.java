package com.example.jylos.tests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.example.jylos.data.dao.filesystem.FolderDAOFileSystem;
import com.example.jylos.data.dao.filesystem.LinkCache;
import com.example.jylos.data.dao.filesystem.NoteDAOFileSystem;
import com.example.jylos.data.dao.filesystem.TagDAOFileSystem;
import com.example.jylos.data.models.Note;
import com.example.jylos.graph.GraphBuilder;
import com.example.jylos.graph.GraphData;
import com.example.jylos.graph.GraphEdge;
import com.example.jylos.service.NoteService;
import com.example.jylos.service.TagService;

/**
 * Links written in a note's properties count as links, as in Obsidian ({@code up: "[[Índice]]"},
 * a list of {@code "[[Note]]"}): they are edges of the graph and the notes they point to get
 * the backlink.
 */
class PropertyLinksTest {

    @TempDir
    Path vault;

    private static final String FRONTMATTER = """
            ---
            up: "[[Índice]]"
            relacionadas:
              - "[[B]]"
              - "[[Carpeta/C|la C]]"
            portada: "![[foto.png]]"
            ---
            """;

    @Test
    void theLinksInPropertiesAreLinksOfTheNoteShortOrLong() throws Exception {
        write("Corta", FRONTMATTER + "Cuerpo con [[D]]\n");
        write("Larga", FRONTMATTER + "Cuerpo con [[D]]\n" + "relleno ".repeat(4000));
        for (String name : List.of("Índice", "B", "D")) {
            write(name, "");
        }
        Files.createDirectories(vault.resolve("Carpeta"));
        write("Carpeta/C", "");
        NoteDAOFileSystem dao = new NoteDAOFileSystem(vault.toString());
        dao.indexLongNotes();

        for (String id : List.of("Corta.md", "Larga.md")) {
            Note listed = dao.fetchAllNotes().stream().filter(n -> n.getId().equals(id)).findFirst().orElseThrow();
            assertEquals(List.of("Índice", "B", "Carpeta/C", "D"), listed.getLinkTargets(), "listed " + id);
            assertEquals(List.of("Índice", "B", "Carpeta/C", "D"), dao.getNoteById(id).getLinkTargets(), "read " + id);
        }
    }

    @Test
    void theGraphHasAnEdgeForEveryPropertyLink() throws Exception {
        write("A", FRONTMATTER + "y [[D]]");
        for (String name : List.of("Índice", "B", "D")) {
            write(name, "");
        }
        Files.createDirectories(vault.resolve("Carpeta"));
        write("Carpeta/C", "");
        NoteDAOFileSystem dao = new NoteDAOFileSystem(vault.toString());
        GraphBuilder graph = new GraphBuilder(new NoteService(dao, new FolderDAOFileSystem(vault.toString())),
                new TagService(new TagDAOFileSystem(dao), dao));

        GraphData data = graph.buildGlobalGraph(new GraphBuilder.Options(false, true, true));

        Set<String> fromA = new TreeSet<>();
        for (GraphEdge edge : data.edges()) {
            if (edge.source().equals("A.md")) {
                fromA.add(edge.target());
            }
        }
        assertEquals(Set.of("Índice.md", "B.md", "Carpeta/C.md", "D.md"), fromA);
    }

    @Test
    void aNoteLinkingOnlyFromItsPropertiesIsALinkedMentionWithThatLine() throws Exception {
        write("A", "---\nrelacionadas:\n  - \"[[B]]\"\n---\nSin enlaces en el cuerpo.\n");
        write("Otra", "Primera línea\nCon [[B]] aquí\n");
        write("B", "");
        NoteService notes = new NoteService(new NoteDAOFileSystem(vault.toString()),
                new FolderDAOFileSystem(vault.toString()));
        com.example.jylos.service.BacklinkService backlinks =
                new com.example.jylos.service.BacklinkService(notes, com.example.jylos.event.EventBus.getInstance());
        Note b = notes.getNoteById("B.md").orElseThrow();

        List<String> seen = new java.util.ArrayList<>();
        for (com.example.jylos.service.BacklinkService.LinkGroup group : backlinks.backlinkGroups(b)) {
            for (var occurrence : group.occurrences()) {
                seen.add(group.note().getId() + " | " + occurrence.lineText().strip() + " | " + occurrence.matched());
            }
        }
        assertEquals(List.of("A.md | - \"[[B]]\" | [[B]]", "Otra.md | Con [[B]] aquí | [[B]]"), seen);
    }

    @Test
    void linksCachedBeforePropertiesCountedAreReadAgain() throws Exception {
        Path cacheFile = vault.resolve(".cache/links.json");
        Files.createDirectories(cacheFile.getParent());
        write("Larga", FRONTMATTER + "Cuerpo con [[D]]\n" + "relleno ".repeat(4000));
        Path larga = vault.resolve("Larga.md");
        long modified = Files.getLastModifiedTime(larga).toMillis();
        long size = Files.size(larga);
        // An entry as an earlier version wrote it: only the body's links, no format.
        Files.writeString(cacheFile, "{\"Larga.md\":{\"modified\":" + modified + ",\"size\":" + size
                + ",\"links\":[\"D\"]}}", StandardCharsets.UTF_8);

        assertTrue(LinkCache.load(cacheFile).get("Larga.md", modified, size).isEmpty(),
                "an entry from before property links counted is not used");
    }

    private void write(String name, String body) throws Exception {
        Files.writeString(vault.resolve(name + ".md"), body, StandardCharsets.UTF_8);
    }
}
