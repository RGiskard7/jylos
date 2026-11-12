package com.example.jylos.tests;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.file.Path;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.example.jylos.data.models.Note;
import com.example.jylos.util.NoteExporter;

/**
 * A PDF export shows what the reading view shows: comments stay hidden (they may hold
 * things the author never meant to share), footnotes are listed, callouts have their title
 * and content without the {@code [!type]} marker, and folded callouts print their content.
 */
class PdfExportReadingSyntaxTest {

    @Test
    void thePdfHidesCommentsAndShowsFootnotesAndCallouts(@TempDir Path dir) throws Exception {
        Note note = new Note("Exportada", String.join("\n",
                "Visible %%secreto privado%% fin.",
                "",
                "Dato con nota[^1].",
                "",
                "> [!tip]- Consejo plegado",
                "> Contenido del consejo",
                "",
                "Párrafo marcado ^marca",
                "",
                "[^1]: Texto al pie."));
        File pdf = dir.resolve("nota.pdf").toFile();

        NoteExporter.exportPdf(note, pdf, null);

        String text;
        try (PDDocument document = PDDocument.load(pdf)) {
            text = new PDFTextStripper().getText(document);
        }
        assertFalse(text.contains("secreto"), "comment exported: " + text);
        assertTrue(text.contains("Visible") && text.contains("fin."), text);
        assertTrue(text.contains("Texto al pie."), "footnote missing: " + text);
        assertTrue(text.contains("Consejo plegado") && text.contains("Contenido del consejo"), text);
        assertFalse(text.contains("[!tip]"), text);
        assertFalse(text.contains("^marca"), text);
    }
}
