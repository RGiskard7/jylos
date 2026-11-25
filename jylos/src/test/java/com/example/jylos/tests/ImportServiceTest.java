package com.example.jylos.tests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.example.jylos.data.dao.filesystem.FolderDAOFileSystem;
import com.example.jylos.data.dao.filesystem.NoteDAOFileSystem;
import com.example.jylos.data.dao.filesystem.TagDAOFileSystem;
import com.example.jylos.data.dao.sqlite.FolderDAOSQLite;
import com.example.jylos.data.dao.sqlite.NoteDAOSQLite;
import com.example.jylos.data.models.Folder;
import com.example.jylos.data.models.Note;
import com.example.jylos.service.FolderService;
import com.example.jylos.service.ImportService;
import com.example.jylos.service.NoteService;

/**
 * Importers must bring external notes into the current storage: Obsidian vaults keep
 * their folder hierarchy, frontmatter and tags; hidden dirs (.obsidian/.trash) are
 * skipped; Evernote .enex notes arrive with Markdown bodies and their tags. Importing the
 * same vault again brings only what is not there yet, with the database and with a vault.
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

    // ── Importing the same vault again ───────────────────────────────────────

    /** A small Obsidian vault: three folders (one nested), five notes, and Obsidian's own folder. */
    private static void writeVault(Path source) throws Exception {
        Files.createDirectories(source.resolve("daily-notes"));
        Files.createDirectories(source.resolve("projects/inner"));
        Files.createDirectories(source.resolve(".obsidian"));
        Files.writeString(source.resolve("Index.md"), "[[2026-07-01]] [[Plan]]\n", StandardCharsets.UTF_8);
        Files.writeString(source.resolve("daily-notes/2026-07-01.md"), "Day 1\n", StandardCharsets.UTF_8);
        Files.writeString(source.resolve("daily-notes/2026-07-02.md"), "Day 2\n", StandardCharsets.UTF_8);
        Files.writeString(source.resolve("projects/Plan.md"), "Plan\n", StandardCharsets.UTF_8);
        Files.writeString(source.resolve("projects/inner/Deep.md"), "Deep\n", StandardCharsets.UTF_8);
    }

    /** Runs {@code check} against services on a fresh SQLite database, the storage of the report. */
    private void onSqlite(Path dir, Function<Services, Void> check) throws Exception {
        SQLiteTestSupport.configureFreshDatabase(dir.resolve("import.sqlite"));
        Connection connection = SQLiteTestSupport.openConnection();
        try {
            FolderDAOSQLite folderDao = new FolderDAOSQLite(connection);
            NoteDAOSQLite noteDao = new NoteDAOSQLite(connection);
            check.apply(new Services(new NoteService(noteDao, folderDao), new FolderService(folderDao, noteDao)));
        } finally {
            SQLiteTestSupport.closeAndReset(connection);
        }
    }

    private static List<String> folderTitles(Services svc) {
        return svc.folders().getAllFolders().stream().map(Folder::getTitle).sorted().toList();
    }

    private static Void importingTwiceAddsNothing(Services svc, Path source) {
        ImportService importer = new ImportService(svc.notes(), svc.folders());
        ImportService.ImportResult first = importer.importObsidianVault(source);
        assertEquals(5, first.notesImported());
        assertEquals(0, first.notesSkipped());
        assertEquals(3, first.foldersCreated());

        ImportService.ImportResult second = importer.importObsidianVault(source);

        assertEquals(0, second.notesImported(), "nothing new to import");
        assertEquals(5, second.notesSkipped(), "every note is already there");
        assertEquals(0, second.foldersCreated(), "the folders already there are used");
        assertTrue(second.errors().isEmpty(), "no errors expected: " + second.errors());
        assertEquals(5, svc.notes().getAllNotes().size(), "no note twice");
        assertEquals(List.of("daily-notes", "inner", "projects"), folderTitles(svc), "no folder twice");
        return null;
    }

    @Test
    void importingTheSameVaultTwiceIntoTheDatabaseAddsNothing(@TempDir Path source, @TempDir Path data)
            throws Exception {
        writeVault(source);
        onSqlite(data, svc -> importingTwiceAddsNothing(svc, source));
    }

    @Test
    void importingTheSameVaultTwiceIntoAVaultAddsNothing(@TempDir Path source, @TempDir Path target)
            throws Exception {
        writeVault(source);
        importingTwiceAddsNothing(servicesOn(target), source);
    }

    @Test
    void importingAgainKeepsWhatWasEditedInJylos(@TempDir Path source, @TempDir Path data) throws Exception {
        writeVault(source);
        onSqlite(data, svc -> {
            ImportService importer = new ImportService(svc.notes(), svc.folders());
            importer.importObsidianVault(source);
            Note plan = svc.notes().findNoteByTitle("Plan")
                    .flatMap(n -> svc.notes().getNoteById(n.getId())).orElseThrow();
            plan.setContent("Plan, edited in Jylos\n");
            svc.notes().updateNote(plan);

            importer.importObsidianVault(source);

            Note after = svc.notes().getNoteById(plan.getId()).orElseThrow();
            assertEquals("Plan, edited in Jylos\n", after.getContent(), "the note in Jylos is not overwritten");
            return null;
        });
    }

    @Test
    void importingAgainBringsOnlyTheNewNotesIntoTheFoldersThere(@TempDir Path source, @TempDir Path data)
            throws Exception {
        writeVault(source);
        onSqlite(data, svc -> {
            ImportService importer = new ImportService(svc.notes(), svc.folders());
            importer.importObsidianVault(source);
            try {
                Files.writeString(source.resolve("daily-notes/2026-07-03.md"), "Day 3\n", StandardCharsets.UTF_8);
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }

            ImportService.ImportResult again = importer.importObsidianVault(source);

            assertEquals(1, again.notesImported(), "only the note written since");
            assertEquals(5, again.notesSkipped());
            assertEquals(0, again.foldersCreated());
            Folder daily = svc.folders().getAllFolders().stream()
                    .filter(f -> "daily-notes".equals(f.getTitle())).findFirst().orElseThrow();
            assertEquals(List.of("2026-07-01", "2026-07-02", "2026-07-03"),
                    svc.notes().getNotesByFolder(daily).stream().map(Note::getTitle).sorted().toList(),
                    "the new note goes into the folder already there");
            assertEquals(List.of("daily-notes", "inner", "projects"), folderTitles(svc));
            return null;
        });
    }

    @Test
    void notesWithTheSameTitleInDifferentFoldersAreBothImported(@TempDir Path source, @TempDir Path data)
            throws Exception {
        Files.createDirectories(source.resolve("a"));
        Files.createDirectories(source.resolve("b"));
        Files.writeString(source.resolve("a/Notes.md"), "in a\n", StandardCharsets.UTF_8);
        Files.writeString(source.resolve("b/Notes.md"), "in b\n", StandardCharsets.UTF_8);
        onSqlite(data, svc -> {
            ImportService importer = new ImportService(svc.notes(), svc.folders());
            importer.importObsidianVault(source);
            ImportService.ImportResult again = importer.importObsidianVault(source);

            assertEquals(2, svc.notes().getAllNotes().size(), "one in each folder, once");
            assertEquals(2, again.notesSkipped());
            return null;
        });
    }
}
