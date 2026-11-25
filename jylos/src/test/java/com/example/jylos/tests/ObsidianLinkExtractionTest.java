package com.example.jylos.tests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.example.jylos.data.dao.filesystem.FrontmatterHandler;
import com.example.jylos.data.dao.filesystem.NoteMetadataCache;
import com.example.jylos.service.LinkResolver;
import com.example.jylos.util.WikiLinkResolver;

/**
 * Links are read as Obsidian reads them: a dot in a note's name is part of the name, only the
 * extensions of the files Obsidian opens make a link point to a file, a {@code +} is a
 * {@code +}, a Markdown link may wrap its path in {@code < >}, and a {@code ../} link that is
 * not there is looked for in the note's own folder.
 */
class ObsidianLinkExtractionTest {

    @Test
    void aDotInTheNameIsPartOfTheName() {
        String text = "[[12.12a1]] [[compendio_version_5.0]] [[Python vs. Java]] "
                + "![[Mapa mental.excalidraw|500]] [[Criterios generales.]]";
        assertEquals(List.of("12.12a1", "compendio_version_5.0", "Python vs. Java", "Mapa mental.excalidraw",
                "Criterios generales."), WikiLinkResolver.extractRawLinks(text));
    }

    @Test
    void aLinkToAFileObsidianOpensIsNotALinkToANote() {
        String text = "[[foto.png]] ![[img.webp]] [[audio.ogg]] [[nota de voz.m4a]] [[doc.pdf]] [[video.mp4]] "
                + "[[Lienzo.canvas]] [t](captura.JPG) [[Nota]]";
        assertEquals(List.of("Nota"), WikiLinkResolver.extractRawLinks(text));
    }

    @Test
    void aPlusInALinkIsAPlus() {
        String text = "[[AutoGen + LangChain = Chatbot]] [t](C+D.md) [t](Mi%20nota.md) [t](E+F%20G.md)";
        assertEquals(List.of("AutoGen + LangChain = Chatbot", "C+D", "Mi nota", "E+F G"),
                WikiLinkResolver.extractRawLinks(text));
    }

    @Test
    void aMarkdownLinkMayWrapItsPathInAngleBrackets() {
        String text = "[Uncle Bob](<../01 - Uncle bob metodología.md>) y [otra](<Carpeta/Otra nota.md#Sección>)";
        assertEquals(List.of("../01 - Uncle bob metodología", "Carpeta/Otra nota"),
                WikiLinkResolver.extractRawLinks(text));
    }

    @Test
    void aMarkdownLinkWithSpacesOrAColonInItsPathIsNotALink() {
        String text = "[a](01 - API_SIMULATIONS.md) [b](/Users/x/app/server.py:1) [c](C:/Users/x/Idea.md) "
                + "[d](<Con espacios.md>) [e](Sin%20espacios.md) [f](Nota.md#Paso: uno)";
        assertEquals(List.of("Con espacios", "Sin espacios", "Nota"), WikiLinkResolver.extractRawLinks(text));
    }

    @Test
    void aLinkInPropertiesCountsOnlyWhenQuoted() {
        String file = "---\nup: \"[[A]]\"\nsin_comillas:\n  - [[B]]\nen_linea: [[C]]\n"
                + "con_comillas:\n  - \"[[D]]\"\n---\n[[E]]\n";
        assertEquals(List.of("A", "D", "E"), FrontmatterHandler.linksOf(file));
    }

    @Test
    void aParentLinkThatIsNotThereIsLookedForInTheNotesOwnFolder() {
        LinkResolver resolver = new LinkResolver(List.of(
                new LinkResolver.Entry("016 - CI-CD/CICD_GUIA.md", true),
                new LinkResolver.Entry("016 - CI-CD/CICD_REPLICATION_GUIDE.md", true),
                new LinkResolver.Entry("Raíz.md", true),
                new LinkResolver.Entry("016 - CI-CD/Raíz.md", true)), Map.of());

        assertEquals(Optional.of("016 - CI-CD/CICD_REPLICATION_GUIDE.md"),
                resolver.resolve("../CICD_REPLICATION_GUIDE", "016 - CI-CD/CICD_GUIA.md"), "not one folder up: its own folder");
        assertEquals(Optional.of("Raíz.md"), resolver.resolve("../Raíz", "016 - CI-CD/CICD_GUIA.md"),
                "one folder up when it is there");
        assertEquals(Optional.empty(), resolver.resolve("../Nada", "016 - CI-CD/CICD_GUIA.md"));
    }

    @Test
    void linksCachedWithTheEarlierRulesAreReadAgain(@TempDir Path dir) throws Exception {
        Path cacheFile = dir.resolve("links.json");
        // A long note's links as the earlier rules found them: [[12.12a1]] was left out.
        Files.writeString(cacheFile, "{\"Larga.md\":{\"modified\":10,\"size\":20,\"links\":[],\"format\":2}}",
                StandardCharsets.UTF_8);

        assertTrue(NoteMetadataCache.load(cacheFile).get("Larga.md", 10, 20).isEmpty());
    }
}
