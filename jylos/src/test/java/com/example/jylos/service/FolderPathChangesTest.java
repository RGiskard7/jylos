package com.example.jylos.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.example.jylos.data.dao.filesystem.FactoryDAOFileSystem;
import com.example.jylos.data.models.Folder;
import com.example.jylos.data.models.Note;
import com.example.jylos.exceptions.DataAccessException;

/**
 * In a folder vault a folder or note is known by its path, and a path changes when a folder
 * above it is renamed or moved. What still holds the old path (a row of the Explorer, the note
 * open in the editor) must still reach it, and a rename that cannot happen must say so.
 */
class FolderPathChangesTest {

    @Test
    void aSubfolderHeldBeforeItsParentWasRenamedCanStillBeRenamed(@TempDir Path vault) throws Exception {
        Files.createDirectories(vault.resolve("Trabajo/Actas"));
        Files.writeString(vault.resolve("Trabajo/Actas/Lunes.md"), "# Lunes\n");
        Services s = new Services(vault);
        Folder work = s.folders.getSubfolders(root()).get(0);
        Folder minutes = s.folders.getSubfolders(work).get(0);

        s.folders.renameFolder(work, "Oficina");
        s.folders.renameFolder(minutes, "Reuniones");

        assertTrue(Files.isRegularFile(vault.resolve("Oficina/Reuniones/Lunes.md")));
        assertEquals("Oficina/Reuniones", minutes.getId());
    }

    @Test
    void savingTheOpenNoteAfterItsFolderWasRenamedWritesItWhereItNowIs(@TempDir Path vault) throws Exception {
        Files.createDirectories(vault.resolve("Trabajo"));
        Files.writeString(vault.resolve("Trabajo/Acta.md"), "# Acta\nuno\n");
        Services s = new Services(vault);
        Note open = s.notes.getNoteById("Trabajo/Acta.md").orElseThrow();
        Folder work = s.folders.getSubfolders(root()).get(0);

        s.folders.renameFolder(work, "Oficina");
        open.setContent("# Acta\ndos\n");
        s.notes.updateNote(open);

        assertEquals(List.of("Oficina/Acta.md"), files(vault), "no copy left under the old folder");
        assertTrue(Files.readString(vault.resolve("Oficina/Acta.md")).contains("dos"));
        assertEquals("Oficina/Acta.md", open.getId(), "the note knows where it now is");
    }

    @Test
    void aNoteListedBeforeItsFolderWasRenamedStillOpens(@TempDir Path vault) throws Exception {
        Files.createDirectories(vault.resolve("Trabajo"));
        Files.writeString(vault.resolve("Trabajo/Acta.md"), "# Acta\nuno\n");
        Services s = new Services(vault);
        Folder work = s.folders.getSubfolders(root()).get(0);

        s.folders.renameFolder(work, "Oficina");
        Note opened = s.notes.getNoteById("Trabajo/Acta.md").orElseThrow();

        assertEquals("Oficina/Acta.md", opened.getId());
        assertTrue(opened.getContent().contains("uno"));
    }

    @Test
    void aNoteCreatedInAFolderListedBeforeItWasRenamedGoesIntoIt(@TempDir Path vault) throws Exception {
        Files.createDirectories(vault.resolve("Trabajo"));
        Services s = new Services(vault);
        Folder work = s.folders.getSubfolders(root()).get(0);
        Folder stale = new Folder(work.getId(), work.getTitle());

        s.folders.renameFolder(work, "Oficina");
        s.notes.createNoteInFolder("Acta", "uno", stale);

        assertEquals(List.of("Oficina/Acta.md"), files(vault));
    }

    @Test
    void aNoteListedBeforeItsFolderWasRenamedCanBeMoved(@TempDir Path vault) throws Exception {
        Files.createDirectories(vault.resolve("Trabajo"));
        Files.createDirectories(vault.resolve("Archivo"));
        Files.writeString(vault.resolve("Trabajo/Acta.md"), "# Acta\n");
        Services s = new Services(vault);
        Note listed = s.notes.getNoteById("Trabajo/Acta.md").orElseThrow();
        Folder work = folder(s, "Trabajo");
        Folder archive = folder(s, "Archivo");

        s.folders.renameFolder(work, "Oficina");
        s.folders.moveNoteToFolder(listed, archive);

        assertEquals(List.of("Archivo/Acta.md"), files(vault));
        assertEquals("Archivo/Acta.md", listed.getId());
    }

    @Test
    void aNoteListedBeforeItsFolderWasRenamedCanBeTrashed(@TempDir Path vault) throws Exception {
        Files.createDirectories(vault.resolve("Trabajo"));
        Files.writeString(vault.resolve("Trabajo/Acta.md"), "# Acta\n");
        Services s = new Services(vault);
        Folder work = folder(s, "Trabajo");

        s.folders.renameFolder(work, "Oficina");
        s.notes.moveToTrash("Trabajo/Acta.md");

        assertEquals(List.of(), files(vault), "nothing left outside the trash");
        assertFalse(Files.exists(vault.resolve("Oficina/Acta.md")));
    }

    @Test
    void aSubfolderHeldBeforeItsParentWasRenamedCanBeTrashed(@TempDir Path vault) throws Exception {
        Files.createDirectories(vault.resolve("Trabajo/Actas"));
        Files.writeString(vault.resolve("Trabajo/Actas/Lunes.md"), "# Lunes\n");
        Services s = new Services(vault);
        Folder work = folder(s, "Trabajo");
        Folder minutes = s.folders.getSubfolders(work).get(0);

        s.folders.renameFolder(work, "Oficina");
        s.folders.deleteFolder(minutes.getId());

        assertFalse(Files.exists(vault.resolve("Oficina/Actas")), "the subfolder went to the trash");
        assertTrue(Files.isDirectory(vault.resolve("Oficina")));
    }

    @Test
    void renamingToTheNameOfAnotherFolderSaysSoInsteadOfDoingNothing(@TempDir Path vault) throws Exception {
        Files.createDirectories(vault.resolve("Trabajo"));
        Files.createDirectories(vault.resolve("Oficina"));
        Services s = new Services(vault);
        Folder work = s.folders.getSubfolders(root()).stream()
                .filter(f -> "Trabajo".equals(f.getTitle())).findFirst().orElseThrow();

        assertThrows(DataAccessException.class, () -> s.folders.renameFolder(work, "Oficina"));
        assertTrue(Files.isDirectory(vault.resolve("Trabajo")));
    }

    @Test
    void changingOnlyTheCapitalsOfAFolderRenamesIt(@TempDir Path vault) throws Exception {
        Files.createDirectories(vault.resolve("trabajo"));
        Files.writeString(vault.resolve("trabajo/Acta.md"), "# Acta\n");
        Services s = new Services(vault);
        Folder work = s.folders.getSubfolders(root()).get(0);

        s.folders.renameFolder(work, "Trabajo");

        try (Stream<Path> top = Files.list(vault)) {
            assertEquals(List.of("Trabajo"), top.map(p -> p.getFileName().toString())
                    .filter(n -> !n.startsWith(".")).toList());
        }
        assertTrue(Files.isRegularFile(vault.resolve("Trabajo/Acta.md")));
        assertFalse(work.getId().equals("trabajo"));
    }

    private static Folder root() {
        return new Folder("ROOT", "Vault");
    }

    private static Folder folder(Services s, String title) {
        return s.folders.getSubfolders(root()).stream()
                .filter(f -> title.equals(f.getTitle())).findFirst().orElseThrow();
    }

    private static List<String> files(Path vault) throws Exception {
        try (Stream<Path> walk = Files.walk(vault)) {
            return walk.filter(Files::isRegularFile).map(p -> vault.relativize(p).toString().replace('\\', '/'))
                    .filter(p -> !p.startsWith(".")).sorted().toList();
        }
    }

    private static final class Services {
        final FolderService folders;
        final NoteService notes;

        Services(Path vault) {
            FactoryDAOFileSystem factory = new FactoryDAOFileSystem(vault.toString());
            folders = new FolderService(factory.getFolderDAO(), factory.getNoteDAO());
            notes = new NoteService(factory.getNoteDAO(), factory.getFolderDAO());
        }
    }
}
