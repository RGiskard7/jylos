package com.example.jylos.tests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.function.Function;

import javax.imageio.ImageIO;

import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
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

    /**
     * Links and embeds are printed as the reading view shows them: {@code [[Nota|texto]]} as its
     * text, not with its brackets, and {@code ![[Nota]]} with the embedded note's content.
     */
    @Test
    void thePdfShowsLinksAsTextAndEmbedsTheirNote(@TempDir Path dir) throws Exception {
        Note note = new Note("Exportada", "Ver [[Otra nota|la otra]] y [[Tercera]].\n\n![[Incrustada]]\n");
        File pdf = dir.resolve("nota.pdf").toFile();

        NoteExporter.exportPdf(note, pdf, null,
                link -> link.equals("Incrustada") ? "Texto de la incrustada" : null);

        String text;
        try (PDDocument document = PDDocument.load(pdf)) {
            text = new PDFTextStripper().getText(document);
        }
        assertFalse(text.contains("[["), "brackets printed: " + text);
        assertTrue(text.contains("la otra") && text.contains("Tercera"), text);
        assertTrue(text.contains("Texto de la incrustada"), "embed missing: " + text);
    }

    /** An image in another folder, written {@code ![[foto.png]]}, is found by name and printed. */
    @Test
    void anImageFoundByItsNameIsExported(@TempDir Path dir) throws Exception {
        Path vault = dir.resolve("vault");
        Path folder = Files.createDirectories(vault.resolve("Notas"));
        Path image = Files.createDirectories(vault.resolve("Attachments")).resolve("foto.png");
        ImageIO.write(new BufferedImage(40, 20, BufferedImage.TYPE_INT_RGB), "png", image.toFile());
        Function<String, Optional<Path>> byName = name -> "foto.png".equals(name) ? Optional.of(image) : Optional.empty();
        Note note = new Note("Exportada", "Antes\n\n![[foto.png|300]]\n\nDespués\n");

        File pdf = dir.resolve("nota.pdf").toFile();
        NoteExporter.exportPdf(note, pdf, null, null, null, folder, vault, byName);
        File html = dir.resolve("nota.html").toFile();
        NoteExporter.exportHtml(note, html, folder, null, vault, null, byName);

        int images = 0;
        String text;
        try (PDDocument document = PDDocument.load(pdf)) {
            for (PDPage page : document.getPages()) {
                for (COSName name : page.getResources().getXObjectNames()) {
                    if (page.getResources().isImageXObject(name)) {
                        images++;
                    }
                }
            }
            text = new PDFTextStripper().getText(document);
        }
        assertEquals(1, images, "the image printed once: " + text);
        assertFalse(text.toLowerCase().contains("foto.png"), "no box with its name instead: " + text);
        String page = Files.readString(html.toPath());
        assertTrue(page.contains("data:image/png;base64,") && page.contains("width=\"300\""), "HTML export: " + page);
    }
}
