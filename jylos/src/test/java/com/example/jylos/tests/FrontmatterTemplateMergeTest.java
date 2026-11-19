package com.example.jylos.tests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.example.jylos.data.dao.filesystem.FrontmatterHandler;
import com.example.jylos.data.models.Note;

/**
 * A template's properties, as Obsidian's Templates reads and inserts them: the text is split
 * after its properties, keeping the blank lines with the text; and inserting them in a note
 * that has its own adds the new ones at the end, joins lists, lets the template's value win
 * over one the note has, and leaves the note's when the template's is empty (null).
 */
class FrontmatterTemplateMergeTest {

    @Test
    void aTemplateIsSplitAfterItsProperties() {
        FrontmatterHandler.TemplateText template = FrontmatterHandler.splitTemplate(
                "---\ntags: [reunión]\nup: \"[[Índice]]\"\n---\n\n# {{title}}\n");

        assertEquals(Map.of("tags", List.of("reunión"), "up", "[[Índice]]"), template.properties());
        assertEquals("\n# {{title}}\n", template.content(), "the blank line stays with the text");
    }

    @Test
    void aTemplateWithoutPropertiesIsAllText() {
        FrontmatterHandler.TemplateText template = FrontmatterHandler.splitTemplate("# {{title}}\n---\n");

        assertEquals(Map.of(), template.properties());
        assertEquals("# {{title}}\n---\n", template.content());
    }

    @Test
    void propertiesThatAreNotYamlAreRefused() {
        assertThrows(IllegalArgumentException.class,
                () -> FrontmatterHandler.splitTemplate("---\nclave: [sin cerrar\n---\ntexto"));
    }

    @Test
    void insertingPropertiesMergesThemAsObsidianDoes() {
        String onDisk = "---\ntags:\n  - proyecto\npersonas:\n  - Ana\nestado: borrador\ncliente: ACME\nvacía: \"\"\n---\n\nTexto\n";
        Note note = FrontmatterHandler.parse(onDisk);
        Map<String, Object> template = new LinkedHashMap<>();
        template.put("tags", List.of("reunión", "proyecto"));
        template.put("personas", List.of("Luis", "Ana"));
        template.put("estado", "activo");
        template.put("nueva", "x");
        template.put("vacía", "lleno");
        template.put("cliente", null);

        FrontmatterHandler.mergeProperties(note, template);

        assertEquals("---\ntags:\n  - proyecto\n  - reunión\npersonas:\n  - Ana\n  - Luis\nestado: activo\ncliente: ACME\nvacía: lleno\nnueva: x\n---\n\nTexto\n",
                FrontmatterHandler.generate(note, onDisk));
    }

    /**
     * The names Jylos keeps for itself ({@code title}, {@code created}, {@code favorite}…) are
     * properties like any other in a template, as in Obsidian: the note keeps its name, dates
     * and marks; and what a template adds goes after the note's own, in the template's order.
     */
    @Test
    void aTemplatesPropertiesAreWrittenAsTheTemplateHasThem() {
        Note note = FrontmatterHandler.parse("---\nestado: hecho\n---\nTexto\n");
        note.setTitle("Nota");
        Map<String, Object> template = new LinkedHashMap<>();
        template.put("title", "Título de la Nota");
        template.put("description", "Breve");
        template.put("author", List.of("Edu"));
        template.put("status", "En progreso");
        template.put("category", null);
        template.put("favorite", true);
        template.put("created", "2026-01-01");
        template.put("tags", null);

        FrontmatterHandler.mergeProperties(note, template);

        assertEquals("Nota", note.getTitle(), "the note keeps its name");
        assertEquals(false, note.isFavorite(), "and its marks");
        assertEquals("---\nestado: hecho\ntitle: Título de la Nota\ndescription: Breve\nauthor:\n  - Edu\n"
                + "status: En progreso\ncategory:\nfavorite: true\ncreated: 2026-01-01\ntags:\n---\nTexto\n",
                FrontmatterHandler.generate(note, "---\nestado: hecho\n---\nTexto\n"));
    }

    @Test
    void aNoteWithoutPropertiesGetsTheTemplates() {
        Note note = FrontmatterHandler.parse("Texto\n");
        Map<String, Object> template = new LinkedHashMap<>();
        template.put("tags", List.of("diario"));
        template.put("ánimo", "bien");

        FrontmatterHandler.mergeProperties(note, template);

        assertEquals(Map.of("tags", List.of("diario"), "ánimo", "bien"), FrontmatterHandler.propertiesOf(note));
    }
}
