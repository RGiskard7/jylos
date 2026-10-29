package com.example.jylos.tests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.example.jylos.data.dao.filesystem.FolderDAOFileSystem;
import com.example.jylos.data.dao.filesystem.NoteDAOFileSystem;
import com.example.jylos.data.models.Folder;
import com.example.jylos.data.models.Note;

/**
 * Hidden entries ({@code .trash}, {@code .git}, {@code .obsidian}) are skipped *inside* the
 * vault. Whether a directory <em>above</em> the vault happens to be hidden must not matter:
 * the packaged Linux app keeps its data under {@code ~/.config/Jylos}, and users keep
 * vaults in places like {@code ~/.notes}.
 */
class HiddenAncestorVaultTest {

    @TempDir
    Path sandbox;

    @Test
    void vaultInsideAHiddenDirectoryListsItsNotesAndFolders() throws IOException {
        Path vault = createVault(sandbox.resolve(".hidden-parent").resolve("vault"));

        NoteDAOFileSystem noteDAO = new NoteDAOFileSystem(vault.toString());
        FolderDAOFileSystem folderDAO = new FolderDAOFileSystem(vault.toString());

        assertEquals(Set.of("root.md", "Carpeta/nested.md"), ids(noteDAO.fetchAllNotes()));
        assertEquals(List.of("Carpeta"), folderDAO.fetchAllFoldersAsList().stream().map(Folder::getId).toList());
    }

    @Test
    void hiddenEntriesInsideTheVaultAreStillSkipped() throws IOException {
        Path vault = createVault(sandbox.resolve(".hidden-parent").resolve("vault"));
        Files.createDirectories(vault.resolve(".obsidian"));
        Files.writeString(vault.resolve(".obsidian").resolve("config.md"), "x", StandardCharsets.UTF_8);
        Files.createDirectories(vault.resolve(".trash"));
        Files.writeString(vault.resolve(".trash").resolve("deleted.md"), "x", StandardCharsets.UTF_8);
        Files.createDirectories(vault.resolve("Carpeta").resolve(".git"));
        Files.writeString(vault.resolve("Carpeta").resolve(".git").resolve("HEAD.md"), "x", StandardCharsets.UTF_8);

        NoteDAOFileSystem noteDAO = new NoteDAOFileSystem(vault.toString());
        FolderDAOFileSystem folderDAO = new FolderDAOFileSystem(vault.toString());

        Set<String> noteIds = ids(noteDAO.fetchAllNotes());
        assertEquals(Set.of("root.md", "Carpeta/nested.md"), noteIds);
        List<String> folderIds = folderDAO.fetchAllFoldersAsList().stream().map(Folder::getId).toList();
        assertEquals(List.of("Carpeta"), folderIds);
        assertTrue(folderIds.stream().noneMatch(id -> id.contains(".")), "no hidden folder may be listed");
    }

    private static Path createVault(Path vault) throws IOException {
        Files.createDirectories(vault.resolve("Carpeta"));
        Files.writeString(vault.resolve("root.md"), "root\n", StandardCharsets.UTF_8);
        Files.writeString(vault.resolve("Carpeta").resolve("nested.md"), "nested\n", StandardCharsets.UTF_8);
        return vault;
    }

    private static Set<String> ids(List<Note> notes) {
        return notes.stream().map(Note::getId).collect(Collectors.toSet());
    }
}
