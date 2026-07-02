package com.example.jylos.util;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.function.Function;

import org.jsoup.Jsoup;
import org.jsoup.helper.W3CDom;
import org.jsoup.nodes.Document;

import com.example.jylos.data.models.Note;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;

/**
 * Exports a note to standalone HTML or PDF.
 *
 * <ul>
 *   <li><b>HTML</b> — the same styled preview the app renders (self-contained, offline).</li>
 *   <li><b>PDF</b> — the note's Markdown is rendered to print-friendly XHTML and converted
 *       with openhtmltopdf (PDFBox). No scripts/JS are needed.</li>
 * </ul>
 *
 * @author Edu Díaz (RGiskard7)
 * @since 1.6.0
 */
public final class NoteExporter {

    private NoteExporter() {
    }

    /**
     * Writes the note as a self-contained, styled HTML document. Local images
     * referenced by the note are embedded (resolved against {@code baseDir}).
     *
     * @param baseDir folder to resolve relative image paths against, or {@code null}
     */
    public static void exportHtml(Note note, File target, java.nio.file.Path baseDir) throws Exception {
        exportHtml(note, target, baseDir, null);
    }

    /**
     * Writes the note as a self-contained, styled HTML document using the provided
     * embed resolver for {@code ![[transclusions]]}.
     */
    public static void exportHtml(Note note, File target, java.nio.file.Path baseDir,
            Function<String, String> embeddedContentResolver) throws Exception {
        String html = MarkdownPreview.buildPreviewHtml(content(note), false, null, baseDir, embeddedContentResolver);
        Files.writeString(target.toPath(), html, StandardCharsets.UTF_8);
    }

    /**
     * Renders the note to a print-friendly PDF.
     *
     * <p>Emojis are rendered as inline monochrome images (see
     * {@link MarkdownPreview#emojifyToImages}); the emoji font is deliberately kept out
     * of the CSS {@code font-family}, since registering it as a fallback made
     * openhtmltopdf use that font's oversized space glyph for every space.</p>
     *
     * @param baseUri base URI used to resolve relative resources (images) in the
     *                Markdown — should point at the note's source folder; may be null
     */
    public static void exportPdf(Note note, File target, String baseUri) throws Exception {
        String xhtml = buildPrintHtml(note);
        Document jsoupDoc = Jsoup.parse(xhtml);
        jsoupDoc.outputSettings()
                .syntax(Document.OutputSettings.Syntax.xml)
                .charset(StandardCharsets.UTF_8);
        org.w3c.dom.Document w3c = new W3CDom().fromJsoup(jsoupDoc);

        String resolvedBase = baseUri != null ? baseUri : target.toURI().toString();

        try (OutputStream os = new FileOutputStream(target)) {
            PdfRendererBuilder builder = new PdfRendererBuilder();
            builder.useFastMode();
            builder.withW3cDocument(w3c, resolvedBase);
            builder.toStream(os);
            builder.run();
        }
    }

    /** Print-oriented XHTML: rendered Markdown body + a clean, paper-friendly stylesheet. */
    private static String buildPrintHtml(Note note) {
        String title = note != null && note.getTitle() != null ? escape(note.getTitle()) : "";
        // Emojis as inline images (same approach as the preview): openhtmltopdf renders
        // <img> fine, and this avoids the emoji font's oversized space glyph issue.
        String body = MarkdownPreview.emojifyToImages(MarkdownProcessor.markdownToHtml(content(note)), false);
        return """
                <!DOCTYPE html>
                <html lang="en">
                <head>
                    <meta charset="UTF-8"/>
                    <title>%s</title>
                    <style>
                        @page { margin: 2cm; }
                        body { font-family: 'Helvetica', 'Arial', sans-serif; font-size: 11pt;
                               line-height: 1.5; color: #1a1a1a; text-align: left; }
                        p, li { text-align: left; }
                        h1 { font-size: 22pt; margin: 0 0 0.4em 0; border-bottom: 1px solid #ccc; padding-bottom: 4px; }
                        h2 { font-size: 17pt; margin: 1em 0 0.3em 0; border-bottom: 1px solid #e5e5e5; padding-bottom: 3px; }
                        h3 { font-size: 14pt; margin: 1em 0 0.3em 0; }
                        h4, h5, h6 { margin: 1em 0 0.3em 0; }
                        p { margin: 0.5em 0; }
                        a { color: #0b5cad; text-decoration: none; }
                        ul, ol { margin: 0.5em 0; padding-left: 1.6em; }
                        li { margin: 0.25em 0; }
                        blockquote { border-left: 3px solid #bbb; margin: 0.6em 0; padding: 0.2em 0 0.2em 14px;
                                     color: #555; }
                        code { font-family: 'Courier New', monospace; font-size: 10pt;
                               background: #f2f2f2; padding: 1px 4px; border-radius: 3px; }
                        pre { background: #f6f6f6; border: 1px solid #e0e0e0; border-radius: 4px;
                              padding: 10px; white-space: pre-wrap; word-wrap: break-word; }
                        pre code { background: none; padding: 0; }
                        table { border-collapse: collapse; width: 100%%; margin: 0.6em 0; }
                        th, td { border: 1px solid #ccc; padding: 6px 8px; text-align: left; }
                        th { background: #f0f0f0; }
                        img { max-width: 100%%; height: auto; }
                        img.emoji { height: 1.05em; width: auto; max-width: none; vertical-align: -0.12em; }
                        hr { border: none; border-top: 1px solid #ddd; margin: 1.2em 0; }
                    </style>
                </head>
                <body>
                %s
                </body>
                </html>
                """.formatted(title, body);
    }

    private static String content(Note note) {
        return note != null && note.getContent() != null ? note.getContent() : "";
    }

    private static String escape(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
