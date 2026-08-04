package com.example.jylos.tests;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

/** Verifies that the offline CodeMirror editor resources form a complete bundle. */
class MarkdownEditorAssetsTest {

    @Test
    void editorTemplateShouldEmbedTheGeneratedBundle() throws IOException {
        String html = resource("/com/example/jylos/ui/editor/editor.html");
        String bundle = resource("/com/example/jylos/ui/editor/editor.bundle.js");

        assertTrue(html.contains("/*__JYLOS_EDITOR_BUNDLE__*/"));
        assertFalse(html.contains("id=\"editor-context-menu\""),
                "Desktop context menus belong to JavaFX, not the embedded HTML page.");
        assertFalse(bundle.isBlank());
        assertTrue(bundle.contains("JylosEditor"));
        assertTrue(bundle.contains("setDocument"));
        assertTrue(bundle.contains("replaceDocument"));
        assertTrue(bundle.contains("setAutocompleteTitles"));
        assertTrue(bundle.contains("setLivePreviewEnabled"));
        assertTrue(bundle.contains("setEditable"));
        assertTrue(bundle.contains("isEditable"));
        assertTrue(bundle.contains("hasEditorFocus"));
        assertTrue(bundle.contains("openMarkdownLink"));
        assertTrue(bundle.contains("cm-live-heading"));
    }

    private String resource(String path) throws IOException {
        try (InputStream stream = getClass().getResourceAsStream(path)) {
            assertNotNull(stream, "Missing editor resource: " + path);
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
