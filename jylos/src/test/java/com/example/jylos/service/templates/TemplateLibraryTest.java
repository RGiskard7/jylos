package com.example.jylos.service.templates;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * Which notes are templates: those in the vault's templates folder, its subfolders included,
 * as in Obsidian, which also refuses the vault's root and a folder that is not there; with no
 * folder chosen, the notes in a folder named Templates, as Jylos has always done.
 */
class TemplateLibraryTest {

    private static final List<String> FOLDERS = List.of("Creación de notas", "Creación de notas/Plantillas",
            "Creación de notas/Plantillas/Trabajo", "Diario", "Archivo/templates");
    private static final List<String> NOTES = List.of("Creación de notas/Plantillas/diaria.md",
            "Creación de notas/Plantillas/Trabajo/acta.md", "Creación de notas/Plantillas/foto.png",
            "Creación de notas/otra.md", "Diario/2026-11-19.md", "Archivo/templates/vieja.md", "Inicio.md");

    @Test
    void theTemplatesAreTheMarkdownNotesOfItsFolderAndSubfolders() {
        TemplateLibrary.Listing listing = TemplateLibrary.list("Creación de notas/Plantillas/", FOLDERS, NOTES);

        assertNull(listing.problem());
        assertEquals("Creación de notas/Plantillas", listing.folder());
        assertEquals(List.of(new TemplateLibrary.Template("Creación de notas/Plantillas/diaria.md", "diaria"),
                new TemplateLibrary.Template("Creación de notas/Plantillas/Trabajo/acta.md", "Trabajo/acta")),
                listing.templates());
    }

    @Test
    void theVaultsRootAndAMissingFolderAreRefused() {
        assertEquals(TemplateLibrary.Problem.ROOT_FOLDER, TemplateLibrary.list("/", FOLDERS, NOTES).problem());
        assertEquals(TemplateLibrary.Problem.FOLDER_NOT_FOUND, TemplateLibrary.list("Plantillas", FOLDERS, NOTES).problem());
    }

    @Test
    void withNoFolderChosenAFolderNamedTemplatesIsUsed() {
        TemplateLibrary.Listing listing = TemplateLibrary.list("", FOLDERS, NOTES);

        assertNull(listing.problem());
        assertEquals(List.of(new TemplateLibrary.Template("Archivo/templates/vieja.md", "vieja")), listing.templates());
    }

    @Test
    void withNoFolderChosenAndNoneNamedTemplatesThereIsNone() {
        assertEquals(TemplateLibrary.Problem.NO_FOLDER,
                TemplateLibrary.list("  ", List.of("Diario"), List.of("Diario/x.md")).problem());
    }

    @Test
    void aTemplateIsFoundByItsName() {
        TemplateLibrary.Listing listing = TemplateLibrary.list("Creación de notas/Plantillas", FOLDERS, NOTES);

        assertEquals("Creación de notas/Plantillas/Trabajo/acta.md", listing.find("Trabajo/acta").orElseThrow().path());
        assertEquals("Creación de notas/Plantillas/diaria.md", listing.find("DIARIA").orElseThrow().path(),
                "in any case, as Obsidian");
        assertEquals("Creación de notas/Plantillas/diaria.md", listing.find("diaria.md").orElseThrow().path());
        assertTrue(listing.find("otra").isEmpty(), "a note outside the folder is not a template");
    }
}
