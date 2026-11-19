package com.example.jylos.tests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.example.jylos.data.dao.filesystem.FolderDAOFileSystem;
import com.example.jylos.data.dao.filesystem.NoteDAOFileSystem;
import com.example.jylos.data.models.Note;
import com.example.jylos.service.EncryptionService;
import com.example.jylos.service.NoteService;

/**
 * A canvas can be private, as a note: its file is stored encrypted, it reads back as its
 * canvas while unlocked and as the lock while locked, and made public again it is a plain
 * canvas file once more.
 */
class PrivateCanvasTest {

    private static final String PASSWORD = "clave-de-prueba-canvas-privado";
    private static final String CANVAS = "{\"nodes\":[{\"id\":\"a\",\"type\":\"text\",\"text\":\"secreto\","
            + "\"x\":0,\"y\":0,\"width\":100,\"height\":50}],\"edges\":[]}";

    @AfterEach
    void lock() {
        EncryptionService.getInstance().lock();
    }

    @Test
    void aCanvasIsStoredEncryptedAndReadsBackWhileUnlocked(@TempDir Path vault) throws Exception {
        Path file = vault.resolve("Mapa.canvas");
        Files.writeString(file, CANVAS, StandardCharsets.UTF_8);
        EncryptionService.getInstance().configure(PASSWORD.toCharArray());
        NoteService notes = service(vault);

        Note canvas = notes.getNoteById("Mapa.canvas").orElseThrow();
        canvas.setPrivate(true);
        notes.updateNote(canvas);

        String onDisk = Files.readString(file, StandardCharsets.UTF_8);
        assertTrue(EncryptionService.isEncrypted(onDisk), "encrypted on disk: " + onDisk);
        assertFalse(onDisk.contains("secreto"), onDisk);

        Note unlocked = service(vault).getNoteById("Mapa.canvas").orElseThrow();
        assertTrue(unlocked.isPrivate());
        assertTrue(unlocked.getContent().contains("secreto"), "read back while unlocked: " + unlocked.getContent());

        EncryptionService.getInstance().lock();
        assertEquals(NoteService.LOCKED_PLACEHOLDER, service(vault).getNoteById("Mapa.canvas").orElseThrow().getContent());

        EncryptionService.getInstance().unlock(PASSWORD.toCharArray());
        Note again = service(vault).getNoteById("Mapa.canvas").orElseThrow();
        again.setPrivate(false);
        service(vault).updateNote(again);
        String plain = Files.readString(file, StandardCharsets.UTF_8);
        assertTrue(plain.contains("\"nodes\"") && plain.contains("secreto"), "a plain canvas again: " + plain);
    }

    private static NoteService service(Path vault) {
        return new NoteService(new NoteDAOFileSystem(vault.toString()), new FolderDAOFileSystem(vault.toString()));
    }
}
