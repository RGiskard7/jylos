package com.example.jylos.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.example.jylos.data.dao.filesystem.FolderDAOFileSystem;
import com.example.jylos.data.dao.filesystem.NoteDAOFileSystem;
import com.example.jylos.data.models.interfaces.Component;
import com.example.jylos.event.EventBus;

/**
 * Emptying the trash deletes for good every note and folder in it, and nothing outside it,
 * and says what it deleted so the views can follow.
 */
class TrashServiceTest {

    @AfterEach
    void clearEventBus() {
        EventBus.getInstance().clear();
    }

    @Test
    void emptyingTheTrashDeletesWhatIsInItAndSaysWhat(@TempDir Path vault) throws Exception {
        write(vault, "Queda.md", "queda\n");
        write(vault, "Suelta.md", "suelta\n");
        write(vault, "Vieja/Dentro.md", "dentro\n");
        NoteDAOFileSystem noteDao = new NoteDAOFileSystem(vault.toString());
        FolderDAOFileSystem folderDao = new FolderDAOFileSystem(vault.toString());
        NoteService notes = new NoteService(noteDao, folderDao);
        FolderService folders = new FolderService(folderDao, noteDao);
        notes.moveToTrash("Suelta.md");
        folders.deleteFolder("Vieja");

        List<Component> deleted = new TrashService(notes, folders).emptyTrash();

        List<String> ids = deleted.stream().map(Component::getId).toList();
        assertEquals(List.of(".trash/Suelta.md", ".trash/Vieja", ".trash/Vieja/Dentro.md"), ids.stream().sorted().toList());
        assertEquals(".trash/Vieja", ids.get(ids.size() - 1), "the notes first, then the folders: " + ids);
        try (Stream<Path> left = Files.list(vault.resolve(".trash"))) {
            assertEquals(List.of(), left.toList(), "the trash is empty");
        }
        assertTrue(Files.exists(vault.resolve("Queda.md")), "what is outside the trash stays");
        assertEquals(List.of(), new TrashService(notes, folders).emptyTrash(), "nothing left to delete");
    }

    private static void write(Path vault, String path, String content) throws Exception {
        Path file = vault.resolve(path);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content, StandardCharsets.UTF_8);
    }
}
