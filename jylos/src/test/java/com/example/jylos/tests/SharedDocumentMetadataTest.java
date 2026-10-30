package com.example.jylos.tests;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.example.jylos.data.dao.filesystem.FactoryDAOFileSystem;
import com.example.jylos.data.dao.filesystem.NoteDAOFileSystem;
import com.example.jylos.data.dao.interfaces.FolderDAO;
import com.example.jylos.data.dao.interfaces.NoteDAO;
import com.example.jylos.data.models.Note;

/**
 * Favorite/pinned/private flags of binary attachments live in one sidecar file,
 * {@code .jylos/document-metadata.json}, written by both the note DAO (flag changes) and
 * the folder DAO (folder moves, trash, permanent delete). Neither may overwrite the
 * sidecar from a stale in-memory copy and silently drop what the other just wrote.
 */
class SharedDocumentMetadataTest {

    @TempDir
    Path vault;

    @Test
    void folderOperationsDoNotDropAttachmentFlagsWrittenByTheNoteDao() throws IOException {
        Files.write(vault.resolve("a.pdf"), "%PDF-1.4 a".getBytes());
        Files.createDirectories(vault.resolve("carpeta"));
        Files.write(vault.resolve("carpeta").resolve("b.pdf"), "%PDF-1.4 b".getBytes());

        FactoryDAOFileSystem factory = new FactoryDAOFileSystem(vault.toString());
        NoteDAO notes = factory.getNoteDAO();
        FolderDAO folders = factory.getFolderDAO();

        markFavorite(notes, "carpeta/b.pdf");
        folders.deleteFolder("carpeta");          // moves b.pdf (and its metadata) to the trash
        markFavorite(notes, "a.pdf");             // the note DAO writes again after the move
        folders.permanentlyDeleteFolder(".trash/carpeta");

        Note reopened = new NoteDAOFileSystem(vault.toString()).getNoteById("a.pdf");
        assertNotNull(reopened);
        assertTrue(reopened.isFavorite(), "a.pdf must still be a favorite after the folder operations");
        String sidecar = Files.readString(vault.resolve(".jylos").resolve("document-metadata.json"));
        assertFalse(sidecar.contains("carpeta/b.pdf"), "no entry may survive for a file that no longer exists: " + sidecar);
    }

    private static void markFavorite(NoteDAO notes, String id) {
        Note note = notes.getNoteById(id);
        assertNotNull(note, "attachment not found: " + id);
        note.setFavorite(true);
        notes.updateNote(note);
    }
}
