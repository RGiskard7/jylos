package com.example.jylos.tests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

import com.example.jylos.data.models.Note;
import com.example.jylos.plugin.PreviewContext;
import com.example.jylos.plugin.PreviewEnhancer;
import com.example.jylos.util.MarkdownPreview;

/**
 * Contract for the per-note preview post-processing hook plugins use to render
 * generated content (query results, computed tables) into a note's preview.
 */
class PreviewEnhancerTransformTest {

    @Test
    void transformReceivesTheNoteBeingRendered() {
        Note note = new Note("id-1", "Research Log", "body");
        AtomicReference<PreviewContext> seen = new AtomicReference<>();

        PreviewEnhancer enhancer = new PreviewEnhancer() {
            @Override
            public String transformHtml(PreviewContext context, String html) {
                seen.set(context);
                return html;
            }
        };

        MarkdownPreview.buildPreviewHtml("Hello", true, List.of(enhancer), null, null,
                new PreviewContext(note, true));

        assertTrue(seen.get() != null, "transformHtml should be invoked when a context is supplied");
        assertEquals("Research Log", seen.get().note().getTitle(),
                "The enhancer must be told which note produced the HTML");
        assertTrue(seen.get().darkTheme(), "The active theme should reach the enhancer");
    }

    @Test
    void transformsChainInRegistrationOrder() {
        PreviewEnhancer first = new PreviewEnhancer() {
            @Override
            public String transformHtml(PreviewContext context, String html) {
                return html + "<!--first-->";
            }
        };
        PreviewEnhancer second = new PreviewEnhancer() {
            @Override
            public String transformHtml(PreviewContext context, String html) {
                return html.contains("<!--first-->") ? html + "<!--second-->" : html;
            }
        };

        String result = MarkdownPreview.buildPreviewHtml("Hello", false, List.of(first, second),
                null, null, new PreviewContext(new Note("id", "N", ""), false));

        assertTrue(result.contains("<!--first-->") && result.contains("<!--second-->"),
                "Each enhancer should see the previous enhancer's output");
    }

    @Test
    void failingTransformLeavesTheNoteContentIntact() {
        PreviewEnhancer broken = new PreviewEnhancer() {
            @Override
            public String transformHtml(PreviewContext context, String html) {
                throw new IllegalStateException("plugin bug");
            }
        };
        PreviewEnhancer nulling = new PreviewEnhancer() {
            @Override
            public String transformHtml(PreviewContext context, String html) {
                return null;
            }
        };

        String result = MarkdownPreview.buildPreviewHtml("Distinctive body text", false,
                List.of(broken, nulling), null, null,
                new PreviewContext(new Note("id", "N", ""), false));

        assertTrue(result.contains("Distinctive body text"),
                "A throwing or null-returning enhancer must not blank out the note");
    }

    @Test
    void transformRunsBeforeCodeBlockAssetsAreDecided() {
        // A plugin that replaces the note's only fenced block with generated markup should
        // not leave the preview shipping a syntax highlighter for code that is gone.
        PreviewEnhancer replacer = new PreviewEnhancer() {
            @Override
            public String transformHtml(PreviewContext context, String html) {
                return html.replaceAll("(?s)<pre><code.*?</code></pre>", "<table><tr><td>x</td></tr></table>");
            }
        };

        String source = """
                ```dataview
                LIST
                ```
                """;
        String result = MarkdownPreview.buildPreviewHtml(source, false, List.of(replacer), null, null,
                new PreviewContext(new Note("id", "N", ""), false));

        assertTrue(result.contains("<table>"), "The generated markup should be present");
        assertFalse(result.contains("highlightElement"),
                "highlight.js must not be injected once the code block has been replaced");
    }

    @Test
    void enhancersWithoutAContextAreLeftUntouched() {
        AtomicReference<Boolean> called = new AtomicReference<>(false);
        PreviewEnhancer enhancer = new PreviewEnhancer() {
            @Override
            public String transformHtml(PreviewContext context, String html) {
                called.set(true);
                return html;
            }
        };

        MarkdownPreview.buildPreviewHtml("Hello", false, List.of(enhancer));

        assertFalse(called.get(),
                "Call sites that render without an owning note should skip per-note transforms");
    }
}
