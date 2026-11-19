package com.example.jylos.tests;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.example.jylos.data.dao.filesystem.FolderDAOFileSystem;
import com.example.jylos.data.dao.filesystem.NoteDAOFileSystem;
import com.example.jylos.data.dao.filesystem.TagDAOFileSystem;
import com.example.jylos.data.dao.sqlite.FolderDAOSQLite;
import com.example.jylos.data.dao.sqlite.NoteDAOSQLite;
import com.example.jylos.data.dao.sqlite.TagDAOSQLite;
import com.example.jylos.data.models.Folder;
import com.example.jylos.data.models.Note;
import com.example.jylos.event.EventBus;
import com.example.jylos.graph.GraphBuilder;
import com.example.jylos.graph.GraphData;
import com.example.jylos.graph.GraphEdge;
import com.example.jylos.graph.GraphNode;
import com.example.jylos.service.BacklinkService;
import com.example.jylos.service.NoteService;
import com.example.jylos.service.TagService;

/**
 * Backlinks, graph edges and link clicks follow a link to the note it names by path, as in
 * Obsidian: two notes called "Reunión" in different folders are told apart, a folder path
 * picks one, aliases count, and a link inside code is not a link. Checked on a folder vault
 * and on SQLite (which stores no note properties, so it has no aliases).
 */
class LinkResolutionIntegrationTest {

    @AfterEach
    void clearEventBus() {
        EventBus.getInstance().clear();
    }

    @Test
    void folderVault(@TempDir Path vault) throws Exception {
        write(vault, "Proyectos/Reunión.md", "# R1\n");
        write(vault, "Personal/Reunión.md", "# R2\n");
        write(vault, "Personal/Diario.md", "[[Reunión]] y `[[Inicio]]`\n```\n[[Objetivo]]\n```\n");
        write(vault, "Proyectos/Plan.md", "[[Proyectos/Reunión|la reunión]] y [[Meta]]\n");
        write(vault, "Ideas/Objetivo.md", "---\naliases: [Meta]\n---\n# Objetivo\n");
        write(vault, "Inicio.md", "# Inicio\n");
        // "Personal/Reunión.md" is the shortest path: only the source's folder sends this one to Proyectos/.
        write(vault, "Proyectos/Acta.md", "[[Reunión]]\n");

        NoteDAOFileSystem noteDao = new NoteDAOFileSystem(vault.toString());
        NoteService notes = new NoteService(noteDao, new FolderDAOFileSystem(vault.toString()));
        TagService tags = new TagService(new TagDAOFileSystem(noteDao), noteDao);
        check(notes, tags, Map.of(
                "Proyectos/Reunión.md", "R1", "Personal/Reunión.md", "R2", "Personal/Diario.md", "Diario",
                "Proyectos/Plan.md", "Plan", "Ideas/Objetivo.md", "Objetivo", "Inicio.md", "Inicio", "Proyectos/Acta.md", "Acta"), true);
    }

    @Test
    void sqlite(@TempDir Path dir) throws Exception {
        SQLiteTestSupport.configureFreshDatabase(dir.resolve("links.sqlite"));
        Connection connection = SQLiteTestSupport.openConnection();
        try {
            FolderDAOSQLite folders = new FolderDAOSQLite(connection);
            NoteDAOSQLite noteDao = new NoteDAOSQLite(connection);
            Folder proyectos = folder(folders, "Proyectos");
            Folder personal = folder(folders, "Personal");
            Folder ideas = folder(folders, "Ideas");
            Map<String, String> names = new java.util.HashMap<>();
            names.put(note(noteDao, proyectos, "Reunión", "# R1\n"), "R1");
            names.put(note(noteDao, personal, "Reunión", "# R2\n"), "R2");
            names.put(note(noteDao, personal, "Diario",
                    "[[Reunión]] y `[[Inicio]]`\n```\n[[Objetivo]]\n```\n"), "Diario");
            names.put(note(noteDao, proyectos, "Plan", "[[Proyectos/Reunión|la reunión]] y [[Meta]]\n"), "Plan");
            names.put(note(noteDao, ideas, "Objetivo", "# Objetivo\n"), "Objetivo");
            names.put(note(noteDao, null, "Inicio", "# Inicio\n"), "Inicio");
            names.put(note(noteDao, proyectos, "Acta", "[[Reunión]]\n"), "Acta");

            NoteService notes = new NoteService(noteDao, folders);
            TagService tags = new TagService(new TagDAOSQLite(connection), noteDao);
            check(notes, tags, names, false);
        } finally {
            SQLiteTestSupport.closeAndReset(connection);
        }
    }

    /** {@code names}: note id → a name unique in the test ("R1" and "R2" are both "Reunión"). */
    private static void check(NoteService notes, TagService tags, Map<String, String> names, boolean aliases) {
        Map<String, String> idOf = names.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getValue, Map.Entry::getKey));
        BacklinkService backlinks = new BacklinkService(notes, EventBus.getInstance());
        try {
            assertEquals(List.of("Diario"), backlinkNames(backlinks, notes, idOf.get("R2"), names),
                    "the link from Personal/ goes to Personal/Reunión");
            assertEquals(List.of("Acta", "Plan"), backlinkNames(backlinks, notes, idOf.get("R1"), names),
                    "the folder path, or the source's own folder, picks Proyectos/Reunión");
            assertEquals(aliases ? List.of("Plan") : List.of(), backlinkNames(backlinks, notes, idOf.get("Objetivo"), names),
                    "a link to an alias counts, one inside a code block does not");
            assertEquals(List.of(), backlinkNames(backlinks, notes, idOf.get("Inicio"), names),
                    "a link inside inline code does not count");
        } finally {
            backlinks.shutdown();
        }

        GraphData graph = new GraphBuilder(notes, tags).buildGlobalGraph(new GraphBuilder.Options(false, true, true));
        Map<String, String> label = graph.nodes().stream()
                .collect(Collectors.toMap(GraphNode::id, n -> names.getOrDefault(n.id(), "ghost:" + n.label())));
        Set<String> edges = graph.edges().stream().filter(e -> e.type() == GraphEdge.Type.LINK)
                .map(e -> label.get(e.source()) + "->" + label.get(e.target()))
                .collect(Collectors.toSet());
        assertEquals(Set.of("Diario->R2", "Plan->R1", "Acta->R1", aliases ? "Plan->Objetivo" : "Plan->ghost:Meta"), edges);

        assertEquals("R1", names.get(notes.resolveLink("Reunión", idOf.get("Plan")).orElseThrow().getId()),
                "a click from Proyectos/Plan opens Proyectos/Reunión");
        assertEquals("R2", names.get(notes.resolveLink("Reunión", idOf.get("Diario")).orElseThrow().getId()),
                "a click from Personal/Diario opens Personal/Reunión");
        assertEquals("R2", names.get(notes.resolveLink("Personal/Reunión#Notas", idOf.get("Plan")).orElseThrow().getId()));
        if (aliases) {
            assertEquals("Objetivo", names.get(notes.resolveLink("Meta", idOf.get("Plan")).orElseThrow().getId()));
        }
        assertEquals(java.util.Optional.empty(), notes.resolveLink("Otra/Reunión", idOf.get("Plan")),
                "a path that names no note does not fall back to another note with the name");
    }

    private static List<String> backlinkNames(BacklinkService backlinks, NoteService notes, String id,
            Map<String, String> names) {
        Note target = notes.getNoteById(id).orElseThrow();
        return backlinks.backlinksFor(target).stream().map(n -> names.get(n.getId())).sorted().toList();
    }

    private static Folder folder(FolderDAOSQLite folders, String title) {
        Folder folder = new Folder(title);
        folder.setId(folders.createFolder(folder));
        return folder;
    }

    private static String note(NoteDAOSQLite dao, Folder folder, String title, String content) {
        Note note = new Note(title, content);
        if (folder != null) {
            note.setParent(folder);
        }
        dao.createNote(note);
        return note.getId();
    }

    private static void write(Path vault, String path, String content) throws Exception {
        Path file = vault.resolve(path);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content, StandardCharsets.UTF_8);
    }
}
