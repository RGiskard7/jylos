package com.example.jylos.ui.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.example.jylos.ui.controller.CssSnippetCatalog.SnippetDescriptor;

/**
 * Snippet names are turned into {@code file:} stylesheet URLs and layered onto the
 * live scene, so the name rule is a small security boundary: it must reject
 * anything that is not a plain {@code .css} filename inside the snippet folder.
 */
class CssSnippetCatalogTest {

    @Test
    void acceptsPlainCssFilenames() {
        assertTrue(CssSnippetCatalog.isValidSnippetName("tweaks.css"));
        assertTrue(CssSnippetCatalog.isValidSnippetName("My Snippet 2.css"));
        assertTrue(CssSnippetCatalog.isValidSnippetName("dark_extra-1.css"));
    }

    @Test
    void rejectsPathsAndNonCssNames() {
        assertFalse(CssSnippetCatalog.isValidSnippetName(null));
        assertFalse(CssSnippetCatalog.isValidSnippetName(""));
        assertFalse(CssSnippetCatalog.isValidSnippetName("notes.txt"));
        assertFalse(CssSnippetCatalog.isValidSnippetName("../escape.css"));   // traversal
        assertFalse(CssSnippetCatalog.isValidSnippetName("sub/dir.css"));      // path segment
        assertFalse(CssSnippetCatalog.isValidSnippetName("a\\b.css"));         // windows path
        assertFalse(CssSnippetCatalog.isValidSnippetName("weird;rule.css"));   // unsafe char
    }

    @Test
    void scanListsOnlyValidCssSortedByName(@TempDir Path dir) throws Exception {
        write(dir, "beta.css", "body{}");
        write(dir, "alpha.css", "body{}");
        write(dir, "notes.md", "# nope");          // ignored: not .css
        Files.createDirectory(dir.resolve("nested"));  // ignored: directory

        List<SnippetDescriptor> found = CssSnippetCatalog.scanSnippets(List.of(dir));

        assertEquals(2, found.size());
        assertEquals("alpha.css", found.get(0).name());
        assertEquals("beta.css", found.get(1).name());
        assertTrue(found.get(0).cssUri().startsWith("file:"));
    }

    @Test
    void scanDeduplicatesByNameFirstDirectoryWins(@TempDir Path primary, @TempDir Path secondary) throws Exception {
        write(primary, "shared.css", "body{}");
        write(secondary, "shared.css", "body{}");
        write(secondary, "only-secondary.css", "body{}");

        List<SnippetDescriptor> found = CssSnippetCatalog.scanSnippets(List.of(primary, secondary));

        assertEquals(2, found.size());
        // The primary directory's copy of "shared.css" must shadow the secondary one.
        assertTrue(found.stream().anyMatch(s -> s.cssUri().contains(primary.getFileName().toString())));
        assertFalse(found.stream().anyMatch(s ->
                s.name().equals("shared.css") && s.cssUri().contains(secondary.getFileName().toString())));
    }

    @Test
    void resolveEnabledKeepsOrderAndSkipsMissing() {
        List<SnippetDescriptor> available = List.of(
                new SnippetDescriptor("a.css", "file:/a.css"),
                new SnippetDescriptor("b.css", "file:/b.css"),
                new SnippetDescriptor("c.css", "file:/c.css"));

        List<String> uris = CssSnippetCatalog.resolveEnabled(available, Set.of("c.css", "a.css", "ghost.css"));

        // Catalog order is preserved (a before c) and the unknown name is dropped.
        assertEquals(List.of("file:/a.css", "file:/c.css"), uris);
    }

    @Test
    void resolveEnabledEmptyWhenNothingEnabled() {
        List<SnippetDescriptor> available = List.of(new SnippetDescriptor("a.css", "file:/a.css"));
        assertTrue(CssSnippetCatalog.resolveEnabled(available, Set.of()).isEmpty());
        assertTrue(CssSnippetCatalog.resolveEnabled(available, null).isEmpty());
    }

    private static void write(Path dir, String name, String content) throws Exception {
        Files.write(dir.resolve(name), content.getBytes(StandardCharsets.UTF_8));
    }
}
