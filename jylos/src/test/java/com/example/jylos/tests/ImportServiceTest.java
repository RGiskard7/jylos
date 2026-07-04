package com.example.jylos.tests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.example.jylos.data.dao.filesystem.FolderDAOFileSystem;
import com.example.jylos.data.dao.filesystem.NoteDAOFileSystem;
import com.example.jylos.data.dao.filesystem.TagDAOFileSystem;
import com.example.jylos.data.models.Note;
import com.example.jylos.service.FolderService;
import com.example.jylos.service.ImportService;
import com.example.jylos.service.NoteService;

/**
 * Importers must bring external notes into the current storage: Obsidian vaults keep
 * their folder hierarchy, frontmatter and tags; hidden dirs (.obsidian/.trash) are
 * skipped; Evernote .enex notes arrive with Markdown bodies and their tags.
 */
class ImportServiceTest {

    private record Services(NoteService notes, FolderService folders) {
    }

    private Services servicesOn(Path storageDir) {
        NoteDAOFileSystem noteDao = new NoteDAOFileSystem(storageDir.toString());
        FolderDAOFileSystem folderDao = new FolderDAOFileSystem(storageDir.toString());
        return new Services(
                new NoteService(noteDao, folderDao),
                new FolderService(folderDao, noteDao));
    }

    @Test
    void importsObsidianVaultWithHierarchyAndSkipsHiddenDirs(@TempDir Path source, @TempDir Path target)
            throws Exception {
        Files.writeString(source.resolve("Root note.md"), "# Root\nbody", StandardCharsets.UTF_8);
        Files.createDirectories(source.resolve("Projects/Sub"));
        Files.writeString(source.resolve("Projects/Plan.md"),
                "---\ntitle: Plan\ntags: [work]\n---\nplan body", StandardCharsets.UTF_8);
        Files.writeString(source.resolve("Projects/Sub/Deep.md"), "deep body", StandardCharsets.UTF_8);
        // Obsidian internals must be ignored.
        Files.createDirectories(source.resolve(".obsidian"));
        Files.writeString(source.resolve(".obsidian/workspace.md"), "ignore me", StandardCharsets.UTF_8);

        Services svc = servicesOn(target);
        ImportService importer = new ImportService(svc.notes(), svc.folders());
        ImportService.ImportResult result = importer.importObsidianVault(source);

        assertEquals(3, result.notesImported(), "3 real notes; .obsidian skipped");
        assertTrue(result.errors().isEmpty(), "no errors expected: " + result.errors());

        List<Note> all = svc.notes().getAllNotes();
        assertEquals(3, all.size());
        Optional<Note> plan = svc.notes().findNoteByTitle("Plan");
        assertTrue(plan.isPresent(), "frontmatter title wins over file name");
        Note planFull = svc.notes().getNoteById(plan.get().getId()).orElseThrow();
        assertTrue(planFull.getContent().contains("plan body"));
        assertTrue(planFull.getTags().stream().anyMatch(t -> "work".equals(t.getTitle())),
                "frontmatter tags survive the import");
        assertTrue(svc.notes().findNoteByTitle("Deep").isPresent(), "nested note imported");
        Note rootNote = svc.notes().findNoteByTitle("Root note")
                .flatMap(n -> svc.notes().getNoteById(n.getId())).orElseThrow();
        assertTrue(rootNote.getContent().contains("# Root"),
                "without frontmatter the body is kept verbatim and the file name is the title");
    }

    @Test
    void importsEnexNotesWithTags(@TempDir Path target) throws Exception {
        Path enex = target.resolve("export.enex");
        Files.writeString(enex, """
                <?xml version="1.0" encoding="UTF-8"?>
                <en-export export-date="20260101T000000Z" application="Evernote">
                  <note>
                    <title>Shopping</title>
                    <content><![CDATA[<?xml version="1.0"?><en-note><div><b>Milk</b> and bread</div></en-note>]]></content>
                    <tag>errands</tag>
                  </note>
                  <note>
                    <title>Ideas</title>
                    <content><![CDATA[<en-note><ul><li>one</li><li>two</li></ul></en-note>]]></content>
                  </note>
                </en-export>
                """, StandardCharsets.UTF_8);

        Path storage = Files.createDirectories(target.resolve("vault"));
        Services svc = servicesOn(storage);
        ImportService importer = new ImportService(svc.notes(), svc.folders());
        ImportService.ImportResult result = importer.importEnex(enex);

        assertEquals(2, result.notesImported());
        assertTrue(result.errors().isEmpty(), "no errors expected: " + result.errors());

        Note shopping = svc.notes().findNoteByTitle("Shopping")
                .flatMap(n -> svc.notes().getNoteById(n.getId())).orElseThrow();
        assertTrue(shopping.getContent().contains("**Milk**"), "ENML bold becomes Markdown");
        assertTrue(shopping.getTags().stream().anyMatch(t -> "errands".equals(t.getTitle())));

        Note ideas = svc.notes().findNoteByTitle("Ideas")
                .flatMap(n -> svc.notes().getNoteById(n.getId())).orElseThrow();
        assertTrue(ideas.getContent().contains("- one"), "ENML list items become Markdown bullets");
    }
}
