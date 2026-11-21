package com.example.jylos.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.example.jylos.data.dao.filesystem.FolderDAOFileSystem;
import com.example.jylos.data.dao.filesystem.NoteDAOFileSystem;

/**
 * An attachment named in a note ({@code ![[foto.png]]}) is found as Obsidian finds it: in the
 * note's folder first, otherwise the one with the shortest path in the vault; never a note, and
 * never outside the vault.
 */
class AttachmentPathTest {

    @TempDir
    Path vault;

    private NoteService notes() {
        return new NoteService(new NoteDAOFileSystem(vault.toString()), new FolderDAOFileSystem(vault.toString()));
    }

    private void file(String path) throws Exception {
        Path file = vault.resolve(path);
        Files.createDirectories(file.getParent());
        Files.write(file, new byte[] { 1, 2, 3 });
    }

    private Optional<String> found(String link, String from) {
        return notes().resolveAttachmentPath(link, from).map(p -> vault.relativize(p).toString().replace('\\', '/'));
    }

    @Test
    void anImageInAnotherFolderIsFoundByItsName() throws Exception {
        file("Notas/Nota.md");
        file("Attachments/foto.png");

        assertEquals(Optional.of("Attachments/foto.png"), found("foto.png", "Notas/Nota.md"));
    }

    @Test
    void theOneInTheNotesFolderComesFirstThenTheShortestPath() throws Exception {
        file("Notas/Nota.md");
        file("Notas/foto.png");
        file("A/foto.png");
        file("A/B/C/foto.png");
        file("Otra/Nota2.md");

        assertEquals(Optional.of("Notas/foto.png"), found("foto.png", "Notas/Nota.md"), "the note's folder");
        assertEquals(Optional.of("A/foto.png"), found("foto.png", "Otra/Nota2.md"), "the shortest path");
        assertEquals(Optional.of("A/B/C/foto.png"), found("B/C/foto.png", "Otra/Nota2.md"), "a path as written");
    }

    @Test
    void aNoteMissingFileOrPathOutsideTheVaultIsNotAnAttachment() throws Exception {
        file("Notas/Nota.md");
        file("Notas/foto.md");

        assertEquals(Optional.empty(), found("foto.md", "Notas/Nota.md"), "a note");
        assertEquals(Optional.empty(), found("nada.png", "Notas/Nota.md"), "nothing with that name");
        assertEquals(Optional.empty(), found("../../fuera.png", "Notas/Nota.md"), "outside the vault");
        assertEquals(Optional.empty(), found("", "Notas/Nota.md"));
    }
}
