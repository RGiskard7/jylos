package com.example.jylos.plugin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import com.example.jylos.tests.FxTestSupport;

import javafx.application.Platform;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.TextArea;
import javafx.scene.input.Clipboard;

/**
 * {@link PluginContext#showCopyableInfo}: a plugin (Dataview/Mermaid's "Insert Template",
 * MCP Server's "Connection info") hands the user a snippet meant to be copied elsewhere.
 * {@link PluginContext#showInfo}'s plain {@code setContentText} renders a {@link
 * javafx.scene.control.Label}, and JavaFX Labels have no text selection at all — no
 * drag-select, no Ctrl+C, nothing copies out no matter how the user tries. This drives
 * {@link PluginContext#buildCopyableInfoAlert} directly (not through a real click, which
 * would need a live Stage/Scene and a modal {@code showAndWait()} this test can't drive)
 * to prove the fix: a real, read-only {@link TextArea} carries the exact text, and the
 * "Copy" button actually reaches the system clipboard.
 */
class PluginContextTest {

    @Test
    void copyableInfoContentIsARealTextAreaNotAnUnselectableLabel() throws Exception {
        Assumptions.assumeTrue(FxTestSupport.isFxRuntimeAvailable());

        String snippet = "```dataview\nTABLE rating\nFROM #tag\n```";
        AtomicReference<Alert> alertRef = new AtomicReference<>();
        CountDownLatch done = new CountDownLatch(1);
        Platform.runLater(() -> {
            alertRef.set(PluginContext.buildCopyableInfoAlert("Template", "Copy this:", snippet));
            done.countDown();
        });
        assertTrue(done.await(10, TimeUnit.SECONDS), "buildCopyableInfoAlert did not complete on the FX thread");

        Alert alert = alertRef.get();
        Object content = alert.getDialogPane().getContent();
        TextArea textArea = assertInstanceOf(TextArea.class, content,
                "content must be a real TextArea (selectable/copyable), not a Label");
        assertEquals(snippet, textArea.getText(), "the TextArea must carry the exact content passed in");
        assertFalse(textArea.isEditable(), "read-only — this is a snippet to copy out, not a field to edit in place");
    }

    @Test
    void copyButtonActuallyPutsTheContentOnTheSystemClipboard() throws Exception {
        Assumptions.assumeTrue(FxTestSupport.isFxRuntimeAvailable());

        String snippet = "URL: http://127.0.0.1:18845/mcp";
        AtomicReference<Boolean> clipboardHasSnippet = new AtomicReference<>(false);
        CountDownLatch done = new CountDownLatch(1);
        Platform.runLater(() -> {
            Alert alert = PluginContext.buildCopyableInfoAlert("MCP Server", "Running", snippet);

            // Poison the clipboard first — if the button's handler never actually
            // ran (or copied the wrong thing), this stale value would still be there
            // and the assertion below would catch it, instead of trivially passing
            // because the clipboard happened to be empty.
            javafx.scene.input.ClipboardContent poison = new javafx.scene.input.ClipboardContent();
            poison.putString("unrelated stale clipboard content");
            Clipboard.getSystemClipboard().setContent(poison);

            ButtonType copyButtonType = alert.getDialogPane().getButtonTypes().stream()
                    .filter(bt -> "Copy".equals(bt.getText()))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("no 'Copy' button registered"));
            Button copyButton = (Button) alert.getDialogPane().lookupButton(copyButtonType);
            copyButton.fire(); // exercises the exact same ActionEvent path a real click does

            String onClipboard = Clipboard.getSystemClipboard().getString();
            clipboardHasSnippet.set(snippet.equals(onClipboard));
            done.countDown();
        });
        assertTrue(done.await(10, TimeUnit.SECONDS), "copy button test did not complete on the FX thread");
        assertTrue(clipboardHasSnippet.get(), "clicking 'Copy' must put the exact snippet on the system clipboard");
    }
}
