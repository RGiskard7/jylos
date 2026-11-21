package com.example.jylos.tests;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.example.jylos.data.dao.ExternalChanges;
import com.example.jylos.data.dao.filesystem.FolderDAOFileSystem;
import com.example.jylos.data.dao.filesystem.NoteDAOFileSystem;
import com.example.jylos.data.models.Folder;
import com.example.jylos.event.EventBus;
import com.example.jylos.service.NoteService;

/**
 * A folder added or removed outside the app reaches the folder list; a removed path that was
 * no folder (a note the app itself moved to the trash) does not count as a folder change.
 */
class ExternalFolderChangesTest {

    @TempDir
    Path vault;

    @AfterEach
    void clearEventBus() {
        EventBus.getInstance().clear();
    }

    @Test
    void aFolderAddedOrRemovedOutsideChangesTheFolderListAndNothingElseDoes() throws Exception {
        Files.writeString(vault.resolve("A.md"), "a\n");
        Files.createDirectories(vault.resolve("Vieja"));
        FolderDAOFileSystem folders = new FolderDAOFileSystem(vault.toString());
        NoteService notes = new NoteService(new NoteDAOFileSystem(vault.toString()), folders);

        Path nueva = Files.createDirectories(vault.resolve("Nueva"));
        ExternalChanges added = notes.applyExternalChanges(List.of(nueva));
        assertTrue(added.foldersMayHaveChanged(), "an empty folder created outside");
        assertTrue(folders.fetchAllFoldersAsList().stream().map(Folder::getTitle).anyMatch("Nueva"::equals));

        Files.delete(vault.resolve("Vieja"));
        assertTrue(notes.applyExternalChanges(List.of(vault.resolve("Vieja"))).foldersMayHaveChanged(),
                "a folder removed outside");

        Path gone = vault.resolve("NoEra");
        assertFalse(notes.applyExternalChanges(List.of(gone)).foldersMayHaveChanged(),
                "a missing name that was no folder changes nothing");
    }
}
