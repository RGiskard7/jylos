package com.example.jylos.tests;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.example.jylos.util.WikiLinkResolver;

/**
 * Contract tests for {@link WikiLinkResolver}.
 *
 * <p>Covers all supported Obsidian-compatible link formats:
 * <ul>
 *   <li>Basic wiki-link {@code [[Title]]}</li>
 *   <li>Alias {@code [[Title|Display]]}</li>
 *   <li>Heading anchor {@code [[Title#Heading]]}</li>
 *   <li>Heading + alias {@code [[Title#Heading|Alias]]}</li>
 *   <li>Path prefix {@code [[folder/Title]]}</li>
 *   <li>{@code .md} extension stripping</li>
 *   <li>Markdown internal links {@code [label](Title.md)}</li>
 *   <li>URL-encoded Markdown links {@code [label](Title%20With%20Spaces)}</li>
 *   <li>Broken-link styling, null/empty inputs, URL encoding</li>
 * </ul>
 */
class WikiLinkResolverTest {

    // ── Basic wiki-links ─────────────────────────────────────────────────

    @Test
    void resolvesSimpleWikiLink() {
        String result = WikiLinkResolver.resolve("See [[My Note]] for details", Set.of("My Note"));
        assertTrue(result.contains(
                "<a class=\"wikilink\" href=\"jylos://open-note/My%20Note\" data-target=\"My Note\">My Note</a>"),
                "Expected HTML link, got: " + result);
        assertFalse(result.contains("[["), "Raw WikiLink syntax must be replaced");
    }

    @Test
    void resolvesWikiLinkWithCustomAlias() {
        String result = WikiLinkResolver.resolve("[[Technical Docs|the docs]]", Set.of("Technical Docs"));
        assertTrue(result.contains("data-target=\"Technical Docs\">the docs</a>"),
                "Expected alias as label, got: " + result);
    }

    @Test
    void resolvesMultipleWikiLinks() {
        String result = WikiLinkResolver.resolve("[[First]] and [[Second]]", Set.of("First", "Second"));
        assertTrue(result.contains("data-target=\"First\">First</a>"));
        assertTrue(result.contains("data-target=\"Second\">Second</a>"));
    }

    @Test
    void extractIgnoresHttpWikiLinks() {
        List<String> targets = WikiLinkResolver.extractLinkTargets(
                "[[Valid]] [[http://evil.com]] [[https://x.org/a]]");
        assertEquals(List.of("Valid"), targets);
    }

    @Test
    void brokenLinksGetWikilinkNewClass() {
        String result = WikiLinkResolver.resolve("[[Nonexistent]]", Set.of("Other Note"));
        assertTrue(result.contains("class=\"wikilink wikilink-new\""),
                "Broken link must carry wikilink-new CSS class");
    }

    // ── Heading anchors ──────────────────────────────────────────────────

    @Test
    void resolvesWikiLinkWithHeading() {
        String result = WikiLinkResolver.resolve("[[Note Title#Introduction]]", Set.of("Note Title"));
        // href must contain the note title as first segment
        assertTrue(result.contains("jylos://open-note/Note%20Title"),
                "href must contain the note title, got: " + result);
        // heading encoded after |
        assertTrue(result.contains("Introduction"), "Heading must appear in href or label");
        // default label: "Title › Heading"
        assertTrue(result.contains("Note Title"), "Note title must be in label");
    }

    @Test
    void resolvesWikiLinkWithHeadingAndAlias() {
        String result = WikiLinkResolver.resolve("[[Note#Intro|Click here]]", Set.of("Note"));
        assertTrue(result.contains(">Click here</a>"), "Alias must override the heading label");
    }

    // ── Path / .md extension ─────────────────────────────────────────────

    @Test
    void stripsMdExtension() {
        String result = WikiLinkResolver.resolve("[[Three laws of motion.md]]",
                Set.of("Three laws of motion"));
        assertTrue(result.contains("wikilink\""),
                ".md extension must be stripped and note found as existing");
        assertTrue(result.contains("data-target=\"Three laws of motion\""),
                "data-target must use title without .md, got: " + result);
    }

    @Test
    void resolvesFolderPrefixedLink() {
        // Only the last segment matters for resolution
        String result = WikiLinkResolver.resolve("[[Daily Notes/2026-05-31]]", Set.of("2026-05-31"));
        assertTrue(result.contains("wikilink\""),
                "Folder-prefixed link must resolve using basename, got: " + result);
        assertTrue(result.contains("data-target=\"2026-05-31\""));
    }

    // ── Markdown internal links ──────────────────────────────────────────

    @Test
    void resolvesMarkdownInternalLinkWithMdExtension() {
        String result = WikiLinkResolver.resolve(
                "[Three laws](Three%20laws%20of%20motion.md)",
                Set.of("Three laws of motion"));
        assertTrue(result.contains("jylos://open-note/"),
                "Markdown .md link to known note must be converted, got: " + result);
        assertTrue(result.contains(">Three laws</a>"),
                "Original label must be preserved");
    }

    @Test
    void resolvesMarkdownInternalLinkWithoutExtension() {
        String result = WikiLinkResolver.resolve(
                "[My note](My%20Note)", Set.of("My Note"));
        assertTrue(result.contains("jylos://open-note/My%20Note"),
                "URL-encoded markdown link must resolve, got: " + result);
    }

    @Test
    void doesNotConvertExternalHttpLinks() {
        String md = "[GitHub](https://github.com)";
        String result = WikiLinkResolver.resolve(md, Set.of("github.com"));
        assertEquals(md, result, "External https links must be left unchanged");
    }

    @Test
    void doesNotConvertUnknownMarkdownLinks() {
        // link target is not in the knownTitles set
        String md = "[unknown](some-unknown-page)";
        String result = WikiLinkResolver.resolve(md, Set.of("Other Note"));
        assertEquals(md, result, "Markdown link to unknown note must be unchanged");
    }

    // ── Edge cases / utilities ───────────────────────────────────────────

    @Test
    void handlesNullInput() {
        assertEquals("", WikiLinkResolver.resolve(null, Set.of()));
    }

    @Test
    void handlesEmptyInput() {
        assertEquals("", WikiLinkResolver.resolve("", Set.of()));
    }

    @Test
    void preservesNonLinkContent() {
        String plain = "Normal text without links";
        assertEquals(plain, WikiLinkResolver.resolve(plain, Set.of()));
    }

    @Test
    void encodesSpecialCharactersInUrl() {
        String result = WikiLinkResolver.resolve("[[Multi Word Title]]", Set.of());
        assertTrue(result.contains("jylos://open-note/Multi%20Word%20Title"),
                "Spaces must be %-encoded in the href");
    }

    // ── extractTitle helper ──────────────────────────────────────────────

    @Test
    void extractTitleStripsMdExtension() {
        assertEquals("Note", WikiLinkResolver.extractTitle("Note.md"));
        assertEquals("Note", WikiLinkResolver.extractTitle("Note.MD"));
    }

    @Test
    void extractTitleUsesFinalPathSegment() {
        assertEquals("Note", WikiLinkResolver.extractTitle("folder/sub/Note"));
        assertEquals("Note", WikiLinkResolver.extractTitle("folder/sub/Note.md"));
    }

    @Test
    void extractTitleHandlesBlank() {
        assertEquals("", WikiLinkResolver.extractTitle(null));
        assertEquals("", WikiLinkResolver.extractTitle(""));
    }
}
