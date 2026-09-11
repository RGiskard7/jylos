package com.example.jylos.plugin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import com.example.jylos.tests.FxTestSupport;

import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.DialogPane;
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

    /** A minimal context — everything a plugin normally gets wired to is null, which every
     *  method under test here tolerates (they only touch {@code pluginId} and JavaFX). Test-only
     *  ids, deliberately unlike any real built-in plugin id ("publish", "mcp-server", …) — {@link
     *  PluginContext#getPluginPreferences()} reads real, machine-persistent {@link Preferences},
     *  shared with whatever the real app has stored there under the same id. */
    private static PluginContext testContext(String pluginId) {
        return new PluginContext(pluginId, null, null, null, null, null, null, null, null, null, null, null, null,
                null, null);
    }

    @Test
    void applyThemeActuallyThemesTheDialogPane() throws Exception {
        Assumptions.assumeTrue(FxTestSupport.isFxRuntimeAvailable());

        PluginContext context = testContext("test-apply-theme");
        AtomicReference<DialogPane> paneRef = new AtomicReference<>();
        CountDownLatch done = new CountDownLatch(1);
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            context.applyTheme(alert.getDialogPane());
            paneRef.set(alert.getDialogPane());
            done.countDown();
        });
        assertTrue(done.await(10, TimeUnit.SECONDS), "applyTheme did not complete on the FX thread");

        // UiDialogs.apply's own, already-established side effect: it marks the pane with
        // "root-container" so the app's theme variables resolve on it. If applyTheme ever
        // stopped delegating to UiDialogs.apply, this marker would not appear.
        assertTrue(paneRef.get().getStyleClass().contains("root-container"),
                "applyTheme(DialogPane) must apply the same theming UiDialogs.apply(...) does");
    }

    @Test
    void applyThemeOnASceneActuallyThemesItsRoot() throws Exception {
        Assumptions.assumeTrue(FxTestSupport.isFxRuntimeAvailable());

        PluginContext context = testContext("test-apply-theme-scene");
        AtomicReference<javafx.scene.Parent> rootRef = new AtomicReference<>();
        CountDownLatch done = new CountDownLatch(1);
        Platform.runLater(() -> {
            javafx.scene.layout.VBox root = new javafx.scene.layout.VBox();
            javafx.scene.Scene scene = new javafx.scene.Scene(root, 200, 100);
            context.applyTheme(scene);
            rootRef.set(scene.getRoot());
            done.countDown();
        });
        assertTrue(done.await(10, TimeUnit.SECONDS), "applyTheme(Scene) did not complete on the FX thread");

        // Same marker UiDialogs.apply(Scene) uses on the root, per applyRootContext.
        assertTrue(rootRef.get().getStyleClass().contains("root"),
                "applyTheme(Scene) must theme the scene's root the same way UiDialogs.apply(Scene) does");
    }

    @Test
    void pluginPreferencesAreNamespacedPerPluginAndDoNotLeakIntoTheHostsOwnNode() throws Exception {
        PluginContext contextA = testContext("test-prefs-plugin-a");
        PluginContext contextB = testContext("test-prefs-plugin-b");
        try {
            contextA.getPluginPreferences().put("greeting", "hello from A");
            contextB.getPluginPreferences().put("greeting", "hello from B");

            assertEquals("hello from A", contextA.getPluginPreferences().get("greeting", null),
                    "plugin A reads back its own value");
            assertEquals("hello from B", contextB.getPluginPreferences().get("greeting", null),
                    "plugin B reads back its OWN value, not plugin A's — different node per plugin id");

            // The host's own bookkeeping node (PluginManager's enable/disable flags) must never
            // see a key a plugin wrote through its own scoped node — they are parent and child,
            // not the same node.
            Preferences hostNode = Preferences.userNodeForPackage(PluginManager.class);
            assertNull(hostNode.get("greeting", null),
                    "a plugin's own preference key must not be visible on the host's own node");
        } finally {
            removeQuietly(contextA.getPluginPreferences());
            removeQuietly(contextB.getPluginPreferences());
        }
    }

    private static void removeQuietly(Preferences node) {
        try {
            node.removeNode();
        } catch (BackingStoreException e) {
            // Best-effort cleanup of a real OS-backed preferences store; leaving a stray
            // test-only node behind is harmless (its id can never collide with a real plugin).
        }
    }

    @Test
    void runWithProgressRunsTheTaskOffTheFxThreadAndDeliversTheResultToOnSuccess() throws Exception {
        Assumptions.assumeTrue(FxTestSupport.isFxRuntimeAvailable());

        PluginContext context = testContext("test-run-with-progress-success");
        AtomicBoolean ranOffFxThread = new AtomicBoolean(false);
        Task<String> task = new Task<>() {
            @Override
            protected String call() {
                ranOffFxThread.set(!Platform.isFxApplicationThread());
                updateProgress(1, 1);
                return "export finished";
            }
        };

        AtomicReference<String> delivered = new AtomicReference<>();
        CountDownLatch done = new CountDownLatch(1);
        Platform.runLater(() -> context.runWithProgress("Title", "Header", task,
                result -> {
                    delivered.set(result);
                    done.countDown();
                },
                ex -> done.countDown()));

        assertTrue(done.await(10, TimeUnit.SECONDS), "onSuccess never fired");
        assertTrue(ranOffFxThread.get(), "the task's call() must run off the FX Application Thread");
        assertEquals("export finished", delivered.get(), "onSuccess must receive the task's actual result");
    }

    @Test
    void runWithProgressDeliversTheExceptionToOnFailureWhenTheTaskThrows() throws Exception {
        Assumptions.assumeTrue(FxTestSupport.isFxRuntimeAvailable());

        PluginContext context = testContext("test-run-with-progress-failure");
        Task<String> task = new Task<>() {
            @Override
            protected String call() throws Exception {
                throw new IllegalStateException("simulated export failure");
            }
        };

        AtomicReference<Throwable> caught = new AtomicReference<>();
        AtomicBoolean successCalled = new AtomicBoolean(false);
        CountDownLatch done = new CountDownLatch(1);
        Platform.runLater(() -> context.runWithProgress("Title", "Header", task,
                result -> successCalled.set(true),
                ex -> {
                    caught.set(ex);
                    done.countDown();
                }));

        assertTrue(done.await(10, TimeUnit.SECONDS), "onFailure never fired");
        assertFalse(successCalled.get(), "onSuccess must not fire when the task failed");
        assertNotNull(caught.get(), "onFailure must receive the task's actual exception");
        assertEquals("simulated export failure", caught.get().getMessage());
    }
}
