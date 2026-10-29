package com.example.jylos.tests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.example.jylos.data.dao.filesystem.FolderDAOFileSystem;
import com.example.jylos.data.dao.filesystem.NoteDAOFileSystem;
import com.example.jylos.data.models.Folder;
import com.example.jylos.data.models.Note;
import com.example.jylos.exceptions.InvalidParameterException;

/**
 * A filesystem vault identifies notes and folders by their path relative to the vault
 * root. Those ids can arrive from outside the app (MCP clients, plugins), so an id that
 * escapes the root with {@code ../} must never read, write, move or delete anything outside
 * the vault — while a {@code ..} that stays inside the vault keeps working.
 */
class VaultBoundaryTest {

    private static final String SECRET = "TOP SECRET OUTSIDE VAULT\n";

    @TempDir
    Path sandbox;

    private Path vault;
    private Path outsideFile;
    private Path outsideDir;
    private NoteDAOFileSystem noteDAO;
    private FolderDAOFileSystem folderDAO;

    @BeforeEach
    void setUp() throws IOException {
        vault = Files.createDirectories(sandbox.resolve("vault"));
        Files.createDirectories(vault.resolve("sub"));
        Files.writeString(vault.resolve("nota.md"), "inside\n", StandardCharsets.UTF_8);
        outsideFile = sandbox.resolve("outside.md");
        Files.writeString(outsideFile, SECRET, StandardCharsets.UTF_8);
        outsideDir = Files.createDirectories(sandbox.resolve("outsideDir"));
        Files.writeString(outsideDir.resolve("keep.md"), SECRET, StandardCharsets.UTF_8);
        noteDAO = new NoteDAOFileSystem(vault.toString());
        folderDAO = new FolderDAOFileSystem(vault.toString());
    }

    // ── Notes: reads ─────────────────────────────────────────────────────────

    @Test
    void getNoteByIdDoesNotReadFilesOutsideTheVault() {
        assertNull(noteDAO.getNoteById("../outside.md"));
    }

    @Test
    void resolveFilePathDoesNotResolveOutsideTheVault() {
        assertTrue(noteDAO.resolveFilePath("../outside.md").isEmpty());
    }

    @Test
    void dotDotThatStaysInsideTheVaultStillResolves() {
        Note note = noteDAO.getNoteById("sub/../nota.md");
        assertNotNull(note);
        assertEquals("inside", note.getContent().trim());
    }

    // ── Notes: mutations ─────────────────────────────────────────────────────

    @Test
    void updateNoteRefusesIdOutsideTheVaultAndLeavesTheFileUntouched() throws IOException {
        Note note = new Note("../outside.md", "outside", "OVERWRITTEN");
        assertThrows(InvalidParameterException.class, () -> noteDAO.updateNote(note));
        assertOutsideFileIntact();
    }

    @Test
    void deleteNoteRefusesIdOutsideTheVault() throws IOException {
        assertThrows(InvalidParameterException.class, () -> noteDAO.deleteNote("../outside.md"));
        assertOutsideFileIntact();
    }

    @Test
    void permanentlyDeleteNoteRefusesIdOutsideTheVault() throws IOException {
        assertThrows(InvalidParameterException.class, () -> noteDAO.permanentlyDeleteNote("../outside.md"));
        assertOutsideFileIntact();
    }

    @Test
    void restoreNoteRefusesIdOutsideTheVault() throws IOException {
        assertThrows(InvalidParameterException.class, () -> noteDAO.restoreNote("../outside.md"));
        assertOutsideFileIntact();
    }

    @Test
    void createNoteIgnoresAParentFolderOutsideTheVault() throws IOException {
        Note note = new Note("../outsideDir/Nueva", "Nueva", "body");
        String id = noteDAO.createNote(note);
        assertEquals("Nueva.md", id);
        assertTrue(Files.isRegularFile(vault.resolve("Nueva.md")));
        assertEquals(1, countEntries(outsideDir), "nothing may be written into a directory outside the vault");
    }

    // ── Folders ──────────────────────────────────────────────────────────────

    @Test
    void getFolderByIdDoesNotResolveDirectoriesOutsideTheVault() {
        assertNull(folderDAO.getFolderById("../outsideDir"));
    }

    @Test
    void deleteFolderRefusesIdOutsideTheVault() throws IOException {
        assertThrows(InvalidParameterException.class, () -> folderDAO.deleteFolder("../outsideDir"));
        assertOutsideDirIntact();
    }

    @Test
    void permanentlyDeleteFolderRefusesIdOutsideTheVault() throws IOException {
        assertThrows(InvalidParameterException.class, () -> folderDAO.permanentlyDeleteFolder("../outsideDir"));
        assertOutsideDirIntact();
    }

    @Test
    void restoreFolderRefusesIdOutsideTheVault() throws IOException {
        assertThrows(InvalidParameterException.class, () -> folderDAO.restoreFolder("../outsideDir"));
        assertOutsideDirIntact();
    }

    @Test
    void addNoteRefusesToMoveAFileFromOutsideTheVaultIntoAFolder() throws IOException {
        Folder sub = folderDAO.getFolderById("sub");
        Note intruder = new Note("../outside.md", "outside", "");
        assertThrows(InvalidParameterException.class, () -> folderDAO.addNote(sub, intruder));
        assertOutsideFileIntact();
        assertEquals(0, countEntries(vault.resolve("sub")));
    }

    @Test
    void moveNoteToRootRefusesAFileFromOutsideTheVault() throws IOException {
        Note intruder = new Note("../outside.md", "outside", "");
        assertThrows(InvalidParameterException.class, () -> folderDAO.moveNoteToRoot(intruder));
        assertOutsideFileIntact();
    }

    @Test
    void legitimateFolderOperationsStillWork() {
        Note moved = noteDAO.getNoteById("nota.md");
        folderDAO.addNote(folderDAO.getFolderById("sub"), moved);
        assertEquals("sub/nota.md", moved.getId());
        assertTrue(Files.isRegularFile(vault.resolve("sub/nota.md")));
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private void assertOutsideFileIntact() throws IOException {
        assertTrue(Files.isRegularFile(outsideFile), "file outside the vault must still exist");
        assertEquals(SECRET, Files.readString(outsideFile, StandardCharsets.UTF_8),
                "file outside the vault must not be modified");
    }

    private void assertOutsideDirIntact() throws IOException {
        assertTrue(Files.isDirectory(outsideDir), "directory outside the vault must still exist");
        assertEquals(SECRET, Files.readString(outsideDir.resolve("keep.md"), StandardCharsets.UTF_8));
    }

    private static long countEntries(Path dir) throws IOException {
        try (Stream<Path> entries = Files.list(dir)) {
            return entries.count();
        }
    }
}
