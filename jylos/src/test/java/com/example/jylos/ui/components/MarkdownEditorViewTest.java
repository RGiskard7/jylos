package com.example.jylos.ui.components;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.example.jylos.tests.FxTestSupport;

import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.event.Event;
import javafx.scene.Scene;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.ContextMenuEvent;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.PickResult;
import javafx.scene.layout.StackPane;
import javafx.stage.PopupWindow;
import javafx.stage.Stage;
import javafx.stage.Window;
import javafx.util.Duration;

class MarkdownEditorViewTest {

    private static final int EDITOR_LOAD_TIMEOUT_SECONDS = 60;
    private static final int LANGUAGE_HIGHLIGHT_ATTEMPTS = 40;

    private static boolean fxRuntimeAvailable;

    @BeforeAll
    static void initFxRuntime() {
        fxRuntimeAvailable = FxTestSupport.isFxRuntimeAvailable();
    }

    @Test
    void documentChangesAndHistoryShouldStayInsideCodeMirror() throws Exception {
        Assumptions.assumeTrue(fxRuntimeAvailable);

        CountDownLatch finished = new CountDownLatch(1);
        AssertionError[] failure = new AssertionError[1];
        Platform.runLater(() -> {
            Stage stage = new Stage();
            try {
                MarkdownEditorView editor = new MarkdownEditorView();
                editor.setEditable(true);
                stage.setScene(new Scene(new StackPane(editor), 800, 600));
                stage.show();
                editor.setText("alpha");
                editor.setText("alpha beta");
                editor.whenReady(() -> Platform.runLater(() -> {
                    try {
                        assertEquals("alpha beta", editor.getText());
                        editor.replaceRange(10, 10, " gamma", 16);
                        assertEquals("alpha beta gamma", editor.getText());
                        assertTrue(editor.undo());
                        assertEquals("alpha beta", editor.getText());
                        assertTrue(editor.redo());
                        assertEquals("alpha beta gamma", editor.getText());
                        editor.setLivePreviewEnabled(false);
                        assertFalse(editor.isLivePreviewEnabled());
                        assertEquals("alpha beta gamma", editor.getText());
                        editor.setLivePreviewEnabled(true);
                        assertTrue(editor.isLivePreviewEnabled());
                        assertEquals("alpha beta gamma", editor.getText());
                        editor.replaceDocument("ALPHA BETA GAMMA");
                        assertEquals("ALPHA BETA GAMMA", editor.getText());
                        assertTrue(editor.undo());
                        assertEquals("alpha beta gamma", editor.getText());

                        editor.setText("# Heading\n\n- [ ] task\n\n| A | B |\n|---|---|\n| 1 | 2 |"
                                + "\n\n[Project](Folder/A(B).md \"Title\")\n\n<https://example.com>"
                                + "\n\n![Diagram](images/A(B).png)");
                        Number headings = (Number) editor.getWebView().getEngine()
                                .executeScript("document.querySelectorAll('.cm-live-heading').length");
                        Number tasks = (Number) editor.getWebView().getEngine()
                                .executeScript("document.querySelectorAll('.cm-live-task').length");
                        Number tables = (Number) editor.getWebView().getEngine()
                                .executeScript("document.querySelectorAll('.cm-live-table-row').length");
                        Number links = (Number) editor.getWebView().getEngine()
                                .executeScript("document.querySelectorAll('.cm-live-link').length");
                        Number images = (Number) editor.getWebView().getEngine()
                                .executeScript("document.querySelectorAll('.cm-live-image').length");
                        assertTrue(headings.intValue() > 0);
                        assertTrue(tasks.intValue() > 0);
                        assertTrue(tables.intValue() > 0);
                        assertTrue(links.intValue() >= 2);
                        assertEquals(1, images.intValue(),
                                "Lezer URL nodes must support image targets containing parentheses.");

                        String renderedMarkdown = editor.getText();
                        editor.setEditable(false);
                        editor.getWebView().getEngine()
                                .executeScript("document.querySelector('.cm-live-task').click()");
                        assertEquals(renderedMarkdown, editor.getText(),
                                "Interactive Live Preview widgets must honor read-only mode.");

                    } catch (AssertionError | RuntimeException error) {
                        failure[0] = error instanceof AssertionError assertion
                                ? assertion : new AssertionError(error);
                    } finally {
                        stage.close();
                        finished.countDown();
                    }
                }));
            } catch (AssertionError error) {
                failure[0] = error;
                stage.close();
                finished.countDown();
            } catch (RuntimeException error) {
                failure[0] = new AssertionError(error);
                stage.close();
                finished.countDown();
            }
        });

        assertTrue(finished.await(EDITOR_LOAD_TIMEOUT_SECONDS, TimeUnit.SECONDS),
                "CodeMirror bridge check timed out");
        if (failure[0] != null) throw failure[0];
    }

    @Test
    void fencedCodeShouldLoadLanguageHighlightingAndRemainEditable() throws Exception {
        Assumptions.assumeTrue(fxRuntimeAvailable);

        CountDownLatch finished = new CountDownLatch(1);
        AssertionError[] failure = new AssertionError[1];
        Platform.runLater(() -> {
            Stage stage = new Stage();
            try {
                MarkdownEditorView editor = new MarkdownEditorView();
                editor.setEditable(true);
                stage.setScene(new Scene(new StackPane(editor), 800, 600));
                stage.show();
                editor.whenReady(() -> {
                    String fencedCode = "```java\npublic class Example { }\n```";
                    editor.setText(fencedCode);
                    verifyFencedCodeWhenHighlighted(editor, stage, finished, failure,
                            fencedCode, LANGUAGE_HIGHLIGHT_ATTEMPTS);
                });
            } catch (RuntimeException error) {
                failure[0] = new AssertionError(error);
                stage.close();
                finished.countDown();
            }
        });

        assertTrue(finished.await(EDITOR_LOAD_TIMEOUT_SECONDS, TimeUnit.SECONDS),
                "Fenced-code editor check timed out");
        if (failure[0] != null) throw failure[0];
    }

    @Test
    void desktopClipboardShortcutsAndContextMenuShouldOperateOnCodeMirror() throws Exception {
        Assumptions.assumeTrue(fxRuntimeAvailable);

        CountDownLatch finished = new CountDownLatch(1);
        AssertionError[] failure = new AssertionError[1];
        Platform.runLater(() -> {
            Stage stage = new Stage();
            try {
                MarkdownEditorView editor = new MarkdownEditorView();
                editor.setEditable(true);
                AtomicInteger transformedInsertions = new AtomicInteger();
                editor.setInsertionTransformer(text -> {
                    transformedInsertions.incrementAndGet();
                    return text + " transformed";
                });
                stage.setScene(new Scene(new StackPane(editor), 800, 600));
                stage.show();
                editor.whenReady(() -> {
                    editor.setText("alpha");
                    setClipboard(" beta");
                    editor.requestFocus();
                    Platform.runLater(() -> {
                        try {
                            fireShortcut(editor, KeyCode.V, false);
                            assertEquals(" betaalpha", editor.getText(),
                                    "The platform paste shortcut must insert clipboard text exactly once.");
                            assertEquals(0, transformedInsertions.get(),
                                    "Plain paste must not invoke programmatic snippet hooks.");
                            assertEquals("[[Target]] transformed", editor.getWebView().getEngine()
                                    .executeScript("window.javaEditor.transformInsertion('[[Target]]')"));
                            assertEquals(1, transformedInsertions.get(),
                                    "Semantic insertions must cross the editor-hook bridge exactly once.");

                            editor.selectAll();
                            fireShortcut(editor, KeyCode.C, false);
                            assertEquals(" betaalpha", Clipboard.getSystemClipboard().getString());

                            fireShortcut(editor, KeyCode.X, false);
                            assertEquals("", editor.getText());
                            fireShortcut(editor, KeyCode.Z, false);
                            assertEquals(" betaalpha", editor.getText());
                            fireShortcut(editor, KeyCode.Z, true);
                            assertEquals("", editor.getText());

                            double screenX = stage.getX() + 40;
                            double screenY = stage.getY() + 80;
                            ContextMenuEvent request = new ContextMenuEvent(
                                    ContextMenuEvent.CONTEXT_MENU_REQUESTED,
                                    20, 20, screenX, screenY, false,
                                    new PickResult(editor.getWebView(), 20, 20));
                            Event.fireEvent(editor.getWebView(), request);
                            ContextMenu menu = Window.getWindows().stream()
                                    .filter(ContextMenu.class::isInstance)
                                    .map(ContextMenu.class::cast)
                                    .filter(Window::isShowing)
                                    .findFirst()
                                    .orElse(null);
                            assertTrue(menu != null,
                                    "Right-click must show the JavaFX editor context menu.");
                            assertEquals(List.of("Undo", "Redo", "Cut", "Copy", "Paste", "Select All"),
                                    menu.getItems().stream()
                                            .filter(item -> item.getText() != null && !item.getText().isEmpty())
                                            .map(MenuItem::getText)
                                            .toList());

                            editor.setText("read only");
                            editor.setEditable(false);
                            assertFalse((Boolean) editor.getWebView().getEngine()
                                    .executeScript("window.JylosEditor.isEditable()"));
                            editor.selectAll();
                            setClipboard("replacement");
                            editor.paste();
                            editor.cut();
                            assertFalse(editor.undo());
                            assertEquals("read only", editor.getText(),
                                    "Read-only mode must reject document-changing commands.");
                            editor.copy();
                            assertEquals("read only", Clipboard.getSystemClipboard().getString(),
                                    "Read-only mode must still allow copying.");

                            editor.replaceDocument("synchronized");
                            assertEquals("synchronized", editor.getText(),
                                    "Controller synchronization must remain available in read-only presentation.");
                        } catch (AssertionError | RuntimeException error) {
                            failure[0] = error instanceof AssertionError assertion
                                    ? assertion : new AssertionError(error);
                        } finally {
                            new java.util.ArrayList<>(Window.getWindows()).stream()
                                    .filter(window -> window instanceof PopupWindow)
                                    .forEach(Window::hide);
                            stage.close();
                            finished.countDown();
                        }
                    });
                });
            } catch (RuntimeException error) {
                failure[0] = new AssertionError(error);
                stage.close();
                finished.countDown();
            }
        });

        assertTrue(finished.await(EDITOR_LOAD_TIMEOUT_SECONDS, TimeUnit.SECONDS),
                "Desktop editor interaction check timed out");
        if (failure[0] != null) throw failure[0];
    }

    @Test
    void livePreviewLinksShouldUseInternalAndExternalHandlers() throws Exception {
        Assumptions.assumeTrue(fxRuntimeAvailable);

        CountDownLatch finished = new CountDownLatch(1);
        AssertionError[] failure = new AssertionError[1];
        Platform.runLater(() -> {
            Stage stage = new Stage();
            try {
                MarkdownEditorView editor = new MarkdownEditorView();
                List<String> internal = new ArrayList<>();
                AtomicReference<String> external = new AtomicReference<>();
                editor.setLinkHandlers(internal::add, external::set);
                stage.setScene(new Scene(new StackPane(editor), 800, 600));
                stage.show();
                editor.whenReady(() -> Platform.runLater(() -> {
                    try {
                        editor.getWebView().getEngine().executeScript(
                                "window.javaEditor.openMarkdownLink('folder/Target%20Note.md')");
                        editor.getWebView().getEngine().executeScript(
                                "window.javaEditor.openNote('folder/Wiki%20Target.md#Heading')");
                        editor.getWebView().getEngine().executeScript(
                                "window.javaEditor.openMarkdownLink('https://example.com')");
                        Platform.runLater(() -> {
                            try {
                                assertEquals(List.of("Target Note", "Wiki Target"), internal);
                                assertEquals("https://example.com", external.get());
                            } catch (AssertionError error) {
                                failure[0] = error;
                            } finally {
                                stage.close();
                                finished.countDown();
                            }
                        });
                    } catch (RuntimeException error) {
                        failure[0] = new AssertionError(error);
                        stage.close();
                        finished.countDown();
                    }
                }));
            } catch (RuntimeException error) {
                failure[0] = new AssertionError(error);
                stage.close();
                finished.countDown();
            }
        });

        assertTrue(finished.await(EDITOR_LOAD_TIMEOUT_SECONDS, TimeUnit.SECONDS),
                "Live Preview link routing check timed out");
        if (failure[0] != null) throw failure[0];
    }

    private static void fireShortcut(MarkdownEditorView editor, KeyCode code, boolean shift) {
        KeyEvent event = new KeyEvent(KeyEvent.KEY_PRESSED, "", "", code,
                shift, true, false, true);
        Event.fireEvent(editor.getWebView(), event);
    }

    private static void setClipboard(String text) {
        ClipboardContent content = new ClipboardContent();
        content.putString(text);
        Clipboard.getSystemClipboard().setContent(content);
    }

    private static void verifyFencedCodeWhenHighlighted(MarkdownEditorView editor,
            Stage stage, CountDownLatch finished, AssertionError[] failure,
            String fencedCode, int attemptsRemaining) {
        try {
            Number highlightedTokens = (Number) editor.getWebView().getEngine()
                    .executeScript("Array.from(document.querySelectorAll('.cm-line'))[1]"
                            + ".querySelectorAll('span').length");
            if (highlightedTokens.intValue() < 2 && attemptsRemaining > 0) {
                PauseTransition retry = new PauseTransition(Duration.millis(50));
                retry.setOnFinished(event -> verifyFencedCodeWhenHighlighted(editor, stage,
                        finished, failure, fencedCode, attemptsRemaining - 1));
                retry.play();
                return;
            }

            String renderedEditor = String.valueOf(editor.getWebView().getEngine()
                    .executeScript("document.querySelector('.cm-content').innerHTML"));
            assertTrue(highlightedTokens.intValue() >= 2,
                    "Fenced language blocks should use CodeMirror syntax highlighting. DOM: "
                            + renderedEditor);

            editor.setText("```java\npublic class Example { }\n``");
            int closingFence = editor.getText().length();
            editor.replaceRange(closingFence, closingFence, "`", closingFence + 1);
            assertEquals(fencedCode, editor.getText(),
                    "Completing a fenced block must remain a normal, stable edit.");
        } catch (AssertionError | RuntimeException error) {
            failure[0] = error instanceof AssertionError assertion
                    ? assertion : new AssertionError(error);
        }
        stage.close();
        finished.countDown();
    }
}
