package com.example.jylos.ui.components;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import com.example.jylos.tests.FxTestSupport;

import javafx.application.Platform;

/**
 * A Markdown link clicked in the editor opens the note and heading it names, read as Obsidian
 * reads a link: {@code %20} is a space and a {@code +} is a {@code +}.
 */
class MarkdownLinkDecodingTest {

    @Test
    void aPlusInTheHeadingOfAMarkdownLinkIsAPlus() throws Exception {
        Assumptions.assumeTrue(FxTestSupport.isFxRuntimeAvailable());
        CompletableFuture<String> opened = new CompletableFuture<>();
        Platform.runLater(() -> {
            MarkdownEditorView editor = new MarkdownEditorView();
            editor.setLinkHandlers((title, newTab) -> opened.complete(title), url -> opened.complete("external:" + url));
            editor.new EditorBridge().openMarkdownLink("Lenguajes.md#C++%20moderno");
        });
        assertEquals("Lenguajes#C++ moderno", opened.get(30, TimeUnit.SECONDS));
    }
}
