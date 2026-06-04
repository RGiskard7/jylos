package com.example.jylos.tests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.example.jylos.util.WikiLinkResolver;

/**
 * The knowledge graph must contain ONLY note→note links (wiki-links and internal
 * Markdown links). This locks {@link WikiLinkResolver#extractLinkTargets} so URLs,
 * anchors, inline #tags, mailto and attachments can never leak back in as nodes.
 */
class WikiLinkTargetTest {

    @Test
    void extractsOnlyInternalNoteTargets() {
        String md = String.join("\n",
                "Wiki: [[Note B]] and [[folder/Note C#Heading|alias]]",
                "Unresolved: [[Ghost Note]]",
                "Markdown internal: [ok](Other Note.md)",
                "External: [g](https://google.com) [w](www.example.com)",
                "Anchor: [s](#section)   Mail: [m](mailto:a@b.com)",
                "Image: ![p](diagram.png)   Attachment: [d](report.pdf)",
                "Inline tags: #proyecto #dfsdf");

        List<String> targets = WikiLinkResolver.extractLinkTargets(md);

        assertEquals(List.of("Note B", "Note C", "Ghost Note", "Other Note"), targets,
                "only wiki-links and internal markdown links, bare titles, in order");

        // None of the noise may ever become a graph node:
        for (String junk : List.of("google.com", "www.example.com", "section", "a@b.com",
                "diagram", "diagram.png", "report", "report.pdf", "proyecto", "dfsdf", "#")) {
            assertFalse(targets.contains(junk), "graph must not contain junk target: " + junk);
        }
    }

    @Test
    void internalNoteTargetRejectsNoise() {
        // Accepts real internal references → bare title.
        assertEquals("Note B", WikiLinkResolver.internalNoteTarget("Note B"));
        assertEquals("Note C", WikiLinkResolver.internalNoteTarget("folder/Note C.md"));

        // Rejects every kind of non-note reference.
        for (String junk : List.of("#section", "https://x.com", "http://x.com", "www.x.com",
                "mailto:a@b.com", "tel:123", "diagram.png", "report.pdf", "jylos://open-note/x",
                "a://b", "#")) {
            assertTrue(WikiLinkResolver.internalNoteTarget(junk) == null,
                    "must reject: " + junk);
        }
    }
}
