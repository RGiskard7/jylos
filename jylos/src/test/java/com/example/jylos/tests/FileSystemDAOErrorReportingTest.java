package com.example.jylos.tests;

import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.example.jylos.data.dao.filesystem.FolderDAOFileSystem;
import com.example.jylos.data.dao.filesystem.NoteDAOFileSystem;
import com.example.jylos.data.models.Folder;
import com.example.jylos.data.models.Note;
import com.example.jylos.event.EventBus;
import com.example.jylos.exceptions.DataAccessException;

/**
 * A write to the vault that the disk refuses (a read-only folder, a full disk) is reported to
 * the caller as a {@link DataAccessException}, as the SQLite DAOs do. Swallowed, a move or a
 * delete that did not happen looked done.
 */
class FileSystemDAOErrorReportingTest {

    @TempDir
    Path vault;

    private final List<Path> locked = new ArrayList<>();

    @AfterEach
    void unlock() throws Exception {
        for (Path dir : locked) {
            Files.setPosixFilePermissions(dir, PosixFilePermissions.fromString("rwxr-xr-x"));
        }
        EventBus.getInstance().clear();
    }

    @Test
    void folderWritesTheDiskRefusesAreReported() throws Exception {
        assumePosix();
        write("Libre/Nota.md", "n\n");
        write("Sellada/Vieja/Dentro.md", "d\n");
        write("Fuera.md", "f\n");
        FolderDAOFileSystem folders = new FolderDAOFileSystem(vault.toString());
        Folder sellada = folder("Sellada");
        Folder vieja = folder("Sellada/Vieja");
        Folder libre = folder("Libre");
        Note fuera = note("Fuera.md");
        Note nota = note("Libre/Nota.md");
        lock(vault.resolve("Sellada"));

        assertThrows(DataAccessException.class, () -> folders.createFolder(withParent(new Folder("Nueva"), sellada)),
                "create a folder inside a read-only one");
        assertThrows(DataAccessException.class, () -> folders.updateFolder(renamed(vieja, "Otra")), "rename");
        assertThrows(DataAccessException.class, () -> folders.addNote(sellada, fuera), "move a note into it");
        assertThrows(DataAccessException.class, () -> folders.addSubFolder(sellada, libre), "move a folder into it");
        assertThrows(DataAccessException.class, () -> folders.moveFolderToRoot(vieja), "move its folder out");

        lock(vault);
        assertThrows(DataAccessException.class, () -> folders.moveNoteToRoot(nota), "move a note to a read-only root");
        assertThrows(DataAccessException.class, () -> folders.removeNote(libre, nota), "take a note out of its folder");
    }

    @Test
    void trashWritesTheDiskRefusesAreReported() throws Exception {
        assumePosix();
        write("Nota.md", "n\n");
        write(".trash/Borrada.md", "b\n");
        write(".trash/Carpeta/Dentro.md", "d\n");
        write("Destino/x.md", "x\n");
        NoteDAOFileSystem notes = new NoteDAOFileSystem(vault.toString());
        FolderDAOFileSystem folders = new FolderDAOFileSystem(vault.toString());
        lock(vault.resolve(".trash"));

        assertThrows(DataAccessException.class, () -> notes.deleteNote("Nota.md"), "to a read-only trash");
        assertThrows(DataAccessException.class, () -> notes.permanentlyDeleteNote(".trash/Borrada.md"),
                "delete for good from a read-only trash");
        assertThrows(DataAccessException.class, () -> folders.permanentlyDeleteFolder(".trash/Carpeta"),
                "delete a folder for good from a read-only trash");
        assertThrows(DataAccessException.class, () -> notes.restoreNote(".trash/Borrada.md"),
                "restore out of a read-only trash");
    }

    private void lock(Path dir) throws Exception {
        Files.setPosixFilePermissions(dir, PosixFilePermissions.fromString("r-xr-xr-x"));
        locked.add(0, dir);
    }

    private static void assumePosix() {
        Assumptions.assumeTrue(java.nio.file.FileSystems.getDefault().supportedFileAttributeViews().contains("posix"));
    }

    private void write(String path, String content) throws Exception {
        Path file = vault.resolve(path);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content, StandardCharsets.UTF_8);
    }

    private static Folder folder(String id) {
        Folder folder = new Folder(id.substring(id.lastIndexOf('/') + 1));
        folder.setId(id);
        return folder;
    }

    private static Folder withParent(Folder folder, Folder parent) {
        folder.setParent(parent);
        return folder;
    }

    private static Folder renamed(Folder folder, String title) {
        Folder copy = folder(folder.getId());
        copy.setTitle(title);
        return copy;
    }

    private static Note note(String id) {
        Note note = new Note(id.substring(id.lastIndexOf('/') + 1).replace(".md", ""), "");
        note.setId(id);
        return note;
    }
}
