package com.example.jylos.data.dao.filesystem;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * Guards the cache against old answers. An entry stays valid while its file does not change,
 * across app versions: if how links, tags or properties are found changes, entries written
 * with the old rules would keep giving the old result. This test pins what is found today;
 * when it fails because the rules changed on purpose, bump {@link NoteMetadataCache#FORMAT}
 * (so every note is read again once) and update both expectations here.
 */
class NoteMetadataCacheFormatTest {

    private static final String FILE = """
            ---
            up: "[[Índice#Parte|índice]]"
            relacionadas:
              - "[[B]]"
            portada: "![[portada.png]]"
            sinComillas: [[NoCuenta]]
            tags:
              - propia
            ---
            Texto con [[Destino]], [[Otra#Sección|alias]], [md](Carpeta/Nota.md), [web](https://x.org/#ancla)
            y ![[foto.png|300]], ![[doc.pdf#page=2]], ![[Lienzo.canvas]], ![[Incrustada]].
            #etiqueta #anidada/sub #con-guion. #123 no, &#35; no, https://x.org/#no
            `[[EnCodigo]] #encodigo`
            ```
            [[EnBloque]] #enbloque
            ```
            Final #final
            """;

    @Test
    void whatTheCacheFindsInAFileIsPinnedToItsFormat() {
        NoteMetadataCache.Metadata found = NoteMetadataCache.Metadata.of(FILE);

        assertEquals(2, NoteMetadataCache.FORMAT, "bumped: update the expectations below to the new rules");
        assertEquals(List.of("Índice", "B", "Destino", "Otra", "Carpeta/Nota", "Incrustada"), found.links(),
                "links changed: bump NoteMetadataCache.FORMAT");
        assertEquals(List.of("foto.png", "doc.pdf", "Lienzo.canvas"), found.fileLinks(),
                "file links changed: bump NoteMetadataCache.FORMAT");
        // #123 is not a tag, as in Obsidian (FORMAT 2; FORMAT 1 took it as one).
        assertEquals(List.of("etiqueta", "anidada/sub", "con-guion", "final"), found.inlineTags(),
                "tags changed: bump NoteMetadataCache.FORMAT");
        assertEquals(FILE.substring(4, FILE.indexOf("---\nTexto")), found.yaml(),
                "properties block changed: bump NoteMetadataCache.FORMAT");
        assertEquals(FILE.substring(FILE.indexOf("Texto")), found.preview());
    }
}
