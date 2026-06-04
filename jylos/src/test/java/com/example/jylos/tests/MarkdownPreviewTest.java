package com.example.jylos.tests;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.example.jylos.util.MarkdownPreview;

/**
 * Preview-pipeline guards: KaTeX is injected only for math, and emojis are
 * rasterised to inline images (they don't render via fonts in the WebView).
 */
class MarkdownPreviewTest {

    @Test
    void injectsKatexOnlyWhenMathIsPresent() {
        String withMath = MarkdownPreview.buildPreviewHtml("Energy $E=mc^2$ here.", false, null);
        assertTrue(withMath.contains("katex"), "KaTeX assets should be injected when math is present");
        assertTrue(withMath.contains("renderMathInElement"), "KaTeX auto-render call should be present");

        String noMath = MarkdownPreview.buildPreviewHtml("Just plain text, no math.", false, null);
        assertFalse(noMath.contains("renderMathInElement"), "KaTeX must not be injected without math");
    }

    @Test
    void emojisAreInlinedAsImages() {
        String html = MarkdownPreview.buildPreviewHtml("Launch 🚀 now", false, null);
        assertTrue(html.contains("data:image/png;base64,"),
                "emoji should be rasterised to an inline image data URI");
        assertTrue(html.contains("class=\"emoji\""), "emoji image should carry the emoji class");
    }

    @Test
    void plainTextHasNoEmojiImages() {
        String html = MarkdownPreview.buildPreviewHtml("nothing special here", false, null);
        assertFalse(html.contains("class=\"emoji\""), "no emoji image for plain text");
    }
}
