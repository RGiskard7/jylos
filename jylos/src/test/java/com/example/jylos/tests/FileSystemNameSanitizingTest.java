package com.example.jylos.tests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.example.jylos.data.dao.filesystem.FolderDAOFileSystem;
import com.example.jylos.data.dao.filesystem.NoteDAOFileSystem;
import com.example.jylos.data.models.Folder;
import com.example.jylos.data.models.Note;

/**
 * Folder and note names become real file names in a vault. Letters in any script — and the
 * combining accents of NFD-composed names, which macOS and some paste sources produce —
 * must survive; only characters that are unsafe in a file name are replaced.
 */
class FileSystemNameSanitizingTest {

    /** "Diseño" with the tilde as a separate COMBINING TILDE (U+0303), i.e. NFD. */
    private static final String NFD_DISENO = "Diseño";

    @TempDir
    Path vault;

    @Test
    void folderNamesKeepAccentsAndNonLatinScripts() {
        FolderDAOFileSystem dao = new FolderDAOFileSystem(vault.toString());
        String id = dao.createFolder(new Folder("Reuniones de Diseño 日本"));
        assertEquals("Reuniones de Diseño 日本", id);
        assertTrue(Files.isDirectory(vault.resolve("Reuniones de Diseño 日本")));
    }

    @Test
    void folderNamesKeepNfdCombiningAccents() {
        FolderDAOFileSystem dao = new FolderDAOFileSystem(vault.toString());
        assertEquals(NFD_DISENO, dao.createFolder(new Folder(NFD_DISENO)));
    }

    @Test
    void noteNamesKeepNfdCombiningAccents() {
        NoteDAOFileSystem dao = new NoteDAOFileSystem(vault.toString());
        assertEquals(NFD_DISENO + ".md", dao.createNote(new Note(NFD_DISENO, "body")));
    }

    @Test
    void unsafeFileNameCharactersAreStillReplacedInFolderAndNoteNames() {
        FolderDAOFileSystem folders = new FolderDAOFileSystem(vault.toString());
        NoteDAOFileSystem notes = new NoteDAOFileSystem(vault.toString());
        assertEquals("a_b_c_d_e_", folders.createFolder(new Folder("a/b:c*d?e\"")));
        assertEquals("x_y_z_.md", notes.createNote(new Note("x\\y<z>", "body")));
    }

    @Test
    void renamingAFolderUsesTheSameRules() {
        FolderDAOFileSystem dao = new FolderDAOFileSystem(vault.toString());
        Folder folder = new Folder(dao.createFolder(new Folder("Viejo")), "Viejo");
        folder.setTitle("Año nuevo");
        dao.updateFolder(folder);
        assertEquals("Año nuevo", folder.getId());
        assertTrue(Files.isDirectory(vault.resolve("Año nuevo")));
    }
}
