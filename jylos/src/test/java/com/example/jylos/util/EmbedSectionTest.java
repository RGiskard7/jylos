package com.example.jylos.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/**
 * {@code ![[Note#Heading]]} embeds the same heading {@code [[Note#Heading]]} jumps to, found by
 * the same rules: a {@code #} line inside code is not a heading, a heading may be indented up
 * to three spaces and may close with {@code #}s; and a {@code ^id} inside code marks nothing.
 */
class EmbedSectionTest {

    @Test
    void aCommentInACodeBlockNeitherStartsNorEndsTheSection() {
        String note = "# Guía\n```bash\n# Instalar\nfalso\n```\n## Instalar\nPasos:\n```bash\n"
                + "# instalar dependencias\nnpm i\n```\nFin\n## Otro\nx\n";

        assertEquals("## Instalar\nPasos:\n```bash\n# instalar dependencias\nnpm i\n```\nFin\n",
                Transclusion.extractSection(note, "Instalar"));
        assertEquals(note.indexOf("## Instalar"), NoteAnchors.locate(note, "Instalar").orElseThrow().offset(),
                "the link jumps to the same heading");
    }

    @Test
    void aHeadingMayBeIndentedAndClosedWithHashes() {
        assertEquals("## Uso ##\nTexto\n", Transclusion.extractSection("## Uso ##\nTexto\n# Otro\n", "Uso"));
        assertEquals("   ## Notas\nTexto\n", Transclusion.extractSection("   ## Notas\nTexto\n", "Notas"));
    }

    @Test
    void aBlockIdInsideCodeMarksNothing() {
        String note = "```\nnpm i ^paso\n```\n\nEl paso real ^paso\n";

        assertEquals("El paso real\n", Transclusion.extractBlock(note, "paso"));
    }
}
