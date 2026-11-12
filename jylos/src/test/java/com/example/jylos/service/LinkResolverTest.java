package com.example.jylos.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;

/**
 * Link resolution as Obsidian does it: a link names a file by path, not by title, so two
 * notes with the same name in different folders are told apart, partial and relative paths
 * work, and aliases are a last resort.
 */
class LinkResolverTest {

    private static final LinkResolver RESOLVER = new LinkResolver(List.of(
            md("Proyectos/Reunión.md"),
            md("Personal/Reunión.md"),
            md("Proyectos/Archivo/Reunión.md"),
            md("Proyectos/Plan.md"),
            md("Proyectos/Sub/Hermana.md"),
            md("Inicio.md"),
            md("Árbol.md"),
            new LinkResolver.Entry("Adjuntos/foto.png", false),
            md("Ideas/Objetivo.md")),
            Map.of("mi alias", "Ideas/Objetivo.md"));

    @Test
    void aUniqueNameResolvesWherever() {
        assertEquals(Optional.of("Inicio.md"), RESOLVER.resolve("Inicio", "Proyectos/Plan.md"));
        assertEquals(Optional.of("Proyectos/Plan.md"), RESOLVER.resolve("Plan", "Inicio.md"));
    }

    @Test
    void aSharedNamePrefersTheSourcesFolderThenTheShortestPath() {
        assertEquals(Optional.of("Personal/Reunión.md"), RESOLVER.resolve("Reunión", "Personal/Diario.md"));
        assertEquals(Optional.of("Proyectos/Reunión.md"), RESOLVER.resolve("Reunión", "Proyectos/Plan.md"));
        // From the root no folder is "closer": the shortest path wins.
        String fromRoot = RESOLVER.resolve("Reunión", "Inicio.md").orElseThrow();
        assertEquals(true, fromRoot.equals("Proyectos/Reunión.md") || fromRoot.equals("Personal/Reunión.md"),
                "one of the two shortest paths, got " + fromRoot);
    }

    @Test
    void anExplicitOrPartialPathPicksThatFile() {
        assertEquals(Optional.of("Proyectos/Archivo/Reunión.md"),
                RESOLVER.resolve("Proyectos/Archivo/Reunión", "Personal/Diario.md"));
        assertEquals(Optional.of("Proyectos/Archivo/Reunión.md"),
                RESOLVER.resolve("Archivo/Reunión", "Personal/Diario.md"));
        assertEquals(Optional.of("Personal/Reunión.md"), RESOLVER.resolve("Personal/Reunión.md", "Inicio.md"));
    }

    @Test
    void relativePathsStartAtTheSourcesFolder() {
        assertEquals(Optional.of("Proyectos/Plan.md"), RESOLVER.resolve("../Plan", "Proyectos/Sub/Hermana.md"));
        assertEquals(Optional.of("Proyectos/Sub/Hermana.md"), RESOLVER.resolve("./Sub/Hermana", "Proyectos/Plan.md"));
    }

    @Test
    void headingAliasCaseAndUnicodeFormDoNotMatter() {
        assertEquals(Optional.of("Proyectos/Plan.md"), RESOLVER.resolve("plan#Objetivos|el plan", "Inicio.md"));
        // "Árbol" typed with a combining accent (NFD) still finds the NFC file name.
        assertEquals(Optional.of("Árbol.md"), RESOLVER.resolve("Árbol", "Inicio.md"));
    }

    @Test
    void anAliasIsTheLastResort() {
        assertEquals(Optional.of("Ideas/Objetivo.md"), RESOLVER.resolve("Mi alias", "Inicio.md"));
        assertEquals(Optional.of("Ideas/Objetivo.md"), RESOLVER.resolve("Objetivo", "Inicio.md"));
    }

    @Test
    void attachmentsResolveByTheirFullName() {
        assertEquals(Optional.of("Adjuntos/foto.png"), RESOLVER.resolve("foto.png", "Inicio.md"));
    }

    @Test
    void aLinkToNothingDoesNotResolveAndAHeadingOnlyLinkIsTheSourceItself() {
        assertEquals(Optional.empty(), RESOLVER.resolve("No existe", "Inicio.md"));
        assertEquals(Optional.empty(), RESOLVER.resolve("  ", "Inicio.md"));
        assertEquals(Optional.of("Proyectos/Plan.md"), RESOLVER.resolve("#Objetivos", "Proyectos/Plan.md"));
    }

    private static LinkResolver.Entry md(String path) {
        return new LinkResolver.Entry(path, true);
    }
}
