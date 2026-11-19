package com.example.jylos.ui.controller;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.example.jylos.data.dao.filesystem.FolderDAOFileSystem;
import com.example.jylos.data.dao.filesystem.NoteDAOFileSystem;
import com.example.jylos.data.models.Note;
import com.example.jylos.event.EventBus;
import com.example.jylos.service.FolderService;
import com.example.jylos.service.NoteService;

/**
 * A note embedded in an exported note is found as the reading view finds it: by the path
 * written in the embed, from the exported note's own folder, not by its title.
 */
class ExportEmbedsTest {

    @AfterEach
    void clearEventBus() {
        EventBus.getInstance().clear();
    }

    @Test
    void anEmbedIsTheNoteItsPathNamesFromTheExportedNotesFolder(@TempDir Path vault, @TempDir Path out)
            throws Exception {
        write(vault, "Proyectos/Reunión.md", "Acta de proyectos\n");
        write(vault, "Personal/Reunión.md", "Cosas personales\n");
        // Personal/Reunión.md is the shorter path: only the source's folder picks Proyectos/.
        write(vault, "Proyectos/Acta.md", "![[Reunión]]\n\nVer [[./Reunión]]\n");
        write(vault, "Indice.md", "![[Personal/Reunión]]\n");

        NoteDAOFileSystem noteDao = new NoteDAOFileSystem(vault.toString());
        FolderDAOFileSystem folderDao = new FolderDAOFileSystem(vault.toString());
        NoteService notes = new NoteService(noteDao, folderDao);
        DocumentSupport documents = new DocumentSupport();
        documents.wire(notes, new FolderService(folderDao, noteDao));

        String acta = export(documents, notes, "Proyectos/Acta.md", out.resolve("acta.html").toFile());
        assertTrue(acta.contains("Acta de proyectos"), "the Reunión beside the exported note");
        assertFalse(acta.contains("Cosas personales"));
        assertTrue(acta.contains("class=\"wikilink\""), "a link relative to the note's folder resolves");
        assertFalse(acta.contains("class=\"wikilink wikilink-new\""), "and is not shown as a note to create");

        String indice = export(documents, notes, "Indice.md", out.resolve("indice.html").toFile());
        assertTrue(indice.contains("Cosas personales"), "the folder path written in the embed");
    }

    @Test
    void aPdfEmbedsTheSameNote(@TempDir Path vault, @TempDir Path out) throws Exception {
        write(vault, "Proyectos/Reunión.md", "Acta de proyectos\n");
        write(vault, "Personal/Reunión.md", "Cosas personales\n");
        write(vault, "Proyectos/Acta.md", "![[Reunión]]\n");
        NoteDAOFileSystem noteDao = new NoteDAOFileSystem(vault.toString());
        FolderDAOFileSystem folderDao = new FolderDAOFileSystem(vault.toString());
        NoteService notes = new NoteService(noteDao, folderDao);
        DocumentSupport documents = new DocumentSupport();
        documents.wire(notes, new FolderService(folderDao, noteDao));

        File pdf = out.resolve("acta.pdf").toFile();
        assertTrue(documents.exportNote(notes.getNoteById("Proyectos/Acta.md").orElseThrow(), pdf).success());
        String text;
        try (org.apache.pdfbox.pdmodel.PDDocument document = org.apache.pdfbox.pdmodel.PDDocument.load(pdf)) {
            text = new org.apache.pdfbox.text.PDFTextStripper().getText(document);
        }
        assertTrue(text.contains("Acta de proyectos"), "the Reunión beside the exported note: " + text);
        assertFalse(text.contains("Cosas personales"), text);
    }

    private static String export(DocumentSupport documents, NoteService notes, String id, File target)
            throws Exception {
        Note note = notes.getNoteById(id).orElseThrow();
        assertTrue(documents.exportNote(note, target).success(), "exported");
        return Files.readString(target.toPath(), StandardCharsets.UTF_8);
    }

    private static void write(Path vault, String path, String content) throws Exception {
        Path file = vault.resolve(path);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content, StandardCharsets.UTF_8);
    }
}
