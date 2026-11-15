package com.example.jylos.ui.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.example.jylos.data.dao.filesystem.FolderDAOFileSystem;
import com.example.jylos.data.dao.filesystem.NoteDAOFileSystem;
import com.example.jylos.data.models.Note;
import com.example.jylos.event.EventBus;
import com.example.jylos.service.NoteService;

/**
 * A link on a Kanban card opens the note it names as any link does: by its path, so
 * {@code [[Personal/Reunión]]} opens that one of two notes called "Reunión".
 */
class KanbanCardLinkTest {

    @AfterEach
    void clearEventBus() {
        EventBus.getInstance().clear();
    }

    @Test
    void aCardLinkOpensTheNoteItsPathNames(@TempDir Path vault) throws Exception {
        write(vault, "Proyectos/Reunión.md", "uno\n");
        write(vault, "Personal/Reunión.md", "dos\n");
        NoteService notes = new NoteService(new NoteDAOFileSystem(vault.toString()),
                new FolderDAOFileSystem(vault.toString()));
        List<String> opened = new ArrayList<>();
        TabsSupport overlay = new TabsSupport();
        overlay.wire(null, null, null, notes, () -> false, key -> key, status -> { }, () -> null,
                new TabsSupport.Host(() -> true, id -> { }, note -> { }, note -> opened.add(note.getId()),
                        () -> { }, () -> { }),
                note -> { }, note -> { });

        overlay.openLinkedNote("Personal/Reunión");
        overlay.openLinkedNote("Proyectos/Reunión");

        assertEquals(List.of("Personal/Reunión.md", "Proyectos/Reunión.md"), opened);
    }

    private static void write(Path vault, String path, String content) throws Exception {
        Path file = vault.resolve(path);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content, StandardCharsets.UTF_8);
    }
}
