package com.example.jylos.tests;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import com.example.jylos.util.MarkdownPreview;

import javafx.application.Platform;
import javafx.concurrent.Worker;
import javafx.scene.Scene;
import javafx.scene.web.WebView;
import javafx.stage.Stage;

/**
 * Preview-pipeline guards: KaTeX is injected only for math, and emojis are
 * rasterised to inline images (they don't render via fonts in the WebView).
 */
class MarkdownPreviewTest {

    @Test
    void readableLineLengthAddsACenteredMaxWidthOnlyWhenEnabled() {
        String withoutOption = MarkdownPreview.buildPreviewHtml("Some note text.", false, null);
        assertFalse(withoutOption.contains("max-width: 700px"),
                "Default preview must not cap content width");

        String withOptionOff = MarkdownPreview.buildPreviewHtml("Some note text.", false, null, null, null, null, false);
        assertFalse(withOptionOff.contains("max-width: 700px"),
                "readableLineLength=false must not cap content width");

        String withOptionOn = MarkdownPreview.buildPreviewHtml("Some note text.", false, null, null, null, null, true);
        assertTrue(withOptionOn.contains("max-width: 700px"),
                "readableLineLength=true must cap the body to a centered, readable column");
        assertTrue(withOptionOn.contains("margin-left: auto") && withOptionOn.contains("margin-right: auto"),
                "the capped column must be centered via auto margins, not just narrowed");
    }

    @Test
    void contentFontSizeSetsTheBodyTextSizeOnlyWhenPositive() {
        String defaultCall = MarkdownPreview.buildPreviewHtml("Some note text.", false, null);
        assertFalse(defaultCall.contains("body { font-size:"),
                "existing 3-arg callers (tests, NoteExporter) must render exactly as before");

        String zeroMeansUnset = MarkdownPreview.buildPreviewHtml(
                "Some note text.", false, null, null, null, null, false, 0);
        assertFalse(zeroMeansUnset.contains("body { font-size:"),
                "contentFontSize=0 must leave the WebView's own default font size untouched");

        String withSize = MarkdownPreview.buildPreviewHtml(
                "Some note text.", false, null, null, null, null, false, 18);
        assertTrue(withSize.contains("body { font-size: 18px; }"),
                "a positive contentFontSize must set the body's text size explicitly");
    }

    @Test
    void injectsKatexOnlyWhenMathIsPresent() {
        String withMath = MarkdownPreview.buildPreviewHtml("Energy $E=mc^2$ here.", false, null);
        assertTrue(withMath.contains("katex"), "KaTeX assets should be injected when math is present");
        assertTrue(withMath.contains("renderMathInElement"), "KaTeX auto-render call should be present");

        String noMath = MarkdownPreview.buildPreviewHtml("Just plain text, no math.", false, null);
        assertFalse(noMath.contains("renderMathInElement"), "KaTeX must not be injected without math");
    }

    @Test
    void injectsHighlightJsOnlyWhenCodeBlocksArePresent() {
        String withCode = MarkdownPreview.buildPreviewHtml("""
                ```java
                System.out.println("hello");
                ```
                """, false, null);
        assertTrue(withCode.contains("highlightElement"), "highlight.js should be injected for fenced code blocks");

        String noCode = MarkdownPreview.buildPreviewHtml("Just plain text, no code block.", false, null);
        assertFalse(noCode.contains("highlightElement"), "highlight.js must not be injected without code blocks");
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

    @Test
    void rendersSoftBreaksAsHtmlBreaksForJavaFxWebView() {
        String html = MarkdownPreview.buildPreviewHtml("ubuntu\n    descripcion: Equipo", false, null);

        assertTrue(html.contains("ubuntu<br>descripcion: Equipo"),
                "Preview must not leave raw soft-break characters inside paragraph text.");
    }

    @Test
    void handlesSamePageAnchorLinksInJavaScriptInsteadOfRelyingOnWebKit() {
        // The page is loaded via WebEngine#loadContent, which has no real URL — WebKit's
        // native "#anchor" fragment jump can't be relied on for a document like that.
        // A hand-written or TOC-plugin table of contents must still work, so the click
        // handler has to do the scroll itself instead of letting the click pass through.
        String html = MarkdownPreview.buildPreviewHtml(
                "# Introducción\n\n[Introducción](#introducción)", false, null);
        assertTrue(html.contains("href.startsWith('#')"),
                "click handler must intercept same-page anchor links");
        assertTrue(html.contains("getElementById(decodeURIComponent(href.slice(1)))"),
                "anchor click must resolve the target heading by its id and scroll to it");
    }

    /**
     * Everything above only inspects the generated HTML string — it never proves a real
     * click in the real bundled WebKit actually scrolls anywhere. This loads the Preview
     * HTML into a real {@link WebView}, fires a real click on the TOC link the same way a
     * user's mouse would, and checks the page actually moved.
     */
    @Test
    void samePageAnchorClickActuallyScrollsInARealWebView() throws Exception {
        Assumptions.assumeTrue(FxTestSupport.isFxRuntimeAvailable());

        StringBuilder markdown = new StringBuilder("[Jump to target](#target)\n\n");
        for (int i = 0; i < 120; i++) {
            markdown.append("Padding paragraph number ").append(i).append(" to push the target below the fold.\n\n");
        }
        markdown.append("# Target\n\nEnd of document.\n");
        String html = MarkdownPreview.buildPreviewHtml(markdown.toString(), false, null);

        CountDownLatch clicked = new CountDownLatch(1);
        double[] scrollYAfterClick = { -1 };
        Platform.runLater(() -> {
            Stage stage = new Stage();
            WebView view = new WebView();
            stage.setScene(new Scene(view, 800, 600));
            stage.show();
            view.getEngine().getLoadWorker().stateProperty().addListener((obs, oldState, newState) -> {
                if (newState == Worker.State.SUCCEEDED) {
                    // A real click event, dispatched on the <a> element itself — exercises
                    // the exact document.addEventListener('click', ...) path a mouse click
                    // takes, not a direct call into the scroll logic.
                    view.getEngine().executeScript("document.querySelector('a[href=\"#target\"]').click();");
                    Object scrollY = view.getEngine().executeScript("window.scrollY");
                    scrollYAfterClick[0] = scrollY instanceof Number number ? number.doubleValue() : -1;
                    clicked.countDown();
                }
            });
            view.getEngine().loadContent(html, "text/html");
        });

        assertTrue(clicked.await(30, TimeUnit.SECONDS), "preview page never finished loading / click never ran");
        assertTrue(scrollYAfterClick[0] > 0,
                "clicking the TOC link should have scrolled the real WebView down; scrollY was "
                        + scrollYAfterClick[0]);
    }
}
