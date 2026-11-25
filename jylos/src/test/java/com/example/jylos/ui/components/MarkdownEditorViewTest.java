package com.example.jylos.ui.components;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.example.jylos.tests.FxTestSupport;
import com.example.jylos.ui.DeadKeyRepeats;

import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.event.Event;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.ContextMenuEvent;
import javafx.scene.input.InputMethodEvent;
import javafx.scene.input.InputMethodHighlight;
import javafx.scene.input.InputMethodTextRun;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.PickResult;
import javafx.scene.layout.StackPane;
import javafx.scene.web.WebView;
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
                        Number headings = (Number) editor.page().evaluate("document.querySelectorAll('.cm-live-heading').length");
                        Number tasks = (Number) editor.page().evaluate("document.querySelectorAll('.cm-live-task').length");
                        Number tables = (Number) editor.page().evaluate("document.querySelectorAll('.cm-live-table-row').length");
                        Number links = (Number) editor.page().evaluate("document.querySelectorAll('.cm-live-link').length");
                        Number images = (Number) editor.page().evaluate("document.querySelectorAll('.cm-live-image').length");
                        assertTrue(headings.intValue() > 0);
                        assertTrue(tasks.intValue() > 0);
                        assertTrue(tables.intValue() > 0);
                        assertTrue(links.intValue() >= 2);
                        assertEquals(1, images.intValue(),
                                "Lezer URL nodes must support image targets containing parentheses.");

                        String renderedMarkdown = editor.getText();
                        editor.setEditable(false);
                        editor.page().evaluate("document.querySelector('.cm-live-task').click()");
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
                            assertEquals("[[Target]] transformed", editor.page().evaluate("window.javaEditor.transformInsertion('[[Target]]')"));
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
                                    new PickResult(editor.page().node(), 20, 20));
                            Event.fireEvent(editor.page().node(), request);
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
                            // JavaFX does not close a menu on a press over the node it was opened
                            // on, and here that node is the whole editor.
                            Event.fireEvent(editor.page().node(), new MouseEvent(MouseEvent.MOUSE_PRESSED,
                                    30, 30, screenX, screenY, MouseButton.PRIMARY, 1, false, false, false, false,
                                    true, false, false, false, false, true, new PickResult(editor.page().node(), 30, 30)));
                            assertFalse(menu.isShowing(), "A click in the editor must close its menu.");

                            editor.setText("read only");
                            editor.setEditable(false);
                            assertFalse((Boolean) editor.page().evaluate("window.JylosEditor.isEditable()"));
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
                            new ArrayList<>(Window.getWindows()).stream()
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
                editor.setLinkHandlers((title, newTab) -> internal.add(title), external::set);
                stage.setScene(new Scene(new StackPane(editor), 800, 600));
                stage.show();
                editor.whenReady(() -> Platform.runLater(() -> {
                    try {
                        editor.page().evaluate(
                                "window.javaEditor.openMarkdownLink('folder/Target%20Note.md')");
                        editor.page().evaluate(
                                "window.javaEditor.openNote('folder/Wiki%20Target.md#Heading')");
                        editor.page().evaluate(
                                "window.javaEditor.openMarkdownLink('https://example.com')");
                        Platform.runLater(() -> {
                            try {
                                // The folder is kept so the handler can tell same-named notes apart,
                                // and the heading so the note opens there.
                                assertEquals(List.of("folder/Target Note", "folder/Wiki Target#Heading"), internal);
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
        Event.fireEvent(editor.page().node(), event);
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
            Number highlightedTokens = (Number) editor.page().evaluate("Array.from(document.querySelectorAll('.cm-line'))[1]"
                            + ".querySelectorAll('span').length");
            if (highlightedTokens.intValue() < 2 && attemptsRemaining > 0) {
                PauseTransition retry = new PauseTransition(Duration.millis(50));
                retry.setOnFinished(event -> verifyFencedCodeWhenHighlighted(editor, stage,
                        finished, failure, fencedCode, attemptsRemaining - 1));
                retry.play();
                return;
            }

            String renderedEditor = String.valueOf(editor.page().evaluate("document.querySelector('.cm-content').innerHTML"));
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

    // ── Where a note is shown ────────────────────────────────────────────────

    private static String lines(String name, int count) {
        StringBuilder text = new StringBuilder();
        for (int i = 1; i <= count; i++) {
            text.append(name).append(" line ").append(i).append("\n\n");
        }
        return text.toString();
    }

    /** The editor page's own scroll, read from the page itself (not through the editor's API). */
    private static double scrollTop(MarkdownEditorView editor) {
        for (Node node : editor.lookupAll("*")) {
            if (node instanceof WebView web) {
                Object top = web.getEngine().executeScript("document.querySelector('.cm-scroller').scrollTop");
                return ((Number) top).doubleValue();
            }
        }
        throw new AssertionError("no editor page");
    }

    private static void waitFor(String what, BooleanSupplier condition) throws Exception {
        for (int i = 0; i < 100; i++) {
            if (FxTestSupport.onFx(condition::getAsBoolean)) {
                return;
            }
            Thread.sleep(50);
        }
        throw new AssertionError("condition not reached in time: " + what);
    }

    private static MarkdownEditorView shownEditor(String text) throws Exception {
        MarkdownEditorView editor = FxTestSupport.onFx(() -> {
            DeadKeyRepeats.installEverywhere(); // as the app does when it starts
            MarkdownEditorView view = new MarkdownEditorView();
            view.setEditable(true);
            Stage stage = new Stage();
            stage.setScene(new Scene(new StackPane(view), 800, 600));
            stage.show();
            view.setText(text);
            return view;
        });
        CountDownLatch ready = new CountDownLatch(1);
        FxTestSupport.onFx(() -> {
            editor.whenReady(ready::countDown);
            return null;
        });
        assertTrue(ready.await(EDITOR_LOAD_TIMEOUT_SECONDS, TimeUnit.SECONDS), "the editor page did not load");
        return editor;
    }

    /** A note loaded in place of one scrolled down starts at its top, with the caret at its start. */
    @Test
    void aNoteShownWithNoPositionStartsAtItsTop() throws Exception {
        Assumptions.assumeTrue(fxRuntimeAvailable);
        String first = lines("first", 300);
        MarkdownEditorView editor = shownEditor(first);
        FxTestSupport.onFx(() -> {
            editor.scrollToPosition(first.indexOf("first line 250"));
            return null;
        });
        waitFor("scrolled down", () -> scrollTop(editor) > 2000);

        FxTestSupport.onFx(() -> {
            editor.setText(lines("second", 300));
            editor.showViewPosition(null);
            return null;
        });
        Thread.sleep(300); // a few frames: CodeMirror measures and scrolls on the next ones

        assertEquals(List.of(0.0, 0), FxTestSupport.onFx(() -> List.of(scrollTop(editor), editor.getCaretPosition())));
    }

    /** Where a note was left (first line in sight, selection) is where it shows again after another note. */
    @Test
    void aNoteShownAtItsPositionShowsTheSameLineAndSelectionAgain() throws Exception {
        Assumptions.assumeTrue(fxRuntimeAvailable);
        String first = lines("first", 300);
        MarkdownEditorView editor = shownEditor(first);
        int caret = first.indexOf("first line 180");
        FxTestSupport.onFx(() -> {
            editor.scrollToPosition(caret);
            return null;
        });
        waitFor("scrolled down", () -> scrollTop(editor) > 2000);
        Thread.sleep(200);
        double leftAt = FxTestSupport.onFx(() -> scrollTop(editor));
        NotePosition left = FxTestSupport.onFx(() -> editor.viewPosition("first.md"));

        FxTestSupport.onFx(() -> {
            editor.setText(lines("second", 300));
            editor.showViewPosition(null);
            return null;
        });
        waitFor("the other note at its top", () -> scrollTop(editor) == 0);
        FxTestSupport.onFx(() -> {
            editor.setText(first);
            editor.showViewPosition(left);
            return null;
        });
        waitFor("back where it was", () -> Math.abs(scrollTop(editor) - leftAt) <= 2);
        NotePosition back = FxTestSupport.onFx(() -> editor.viewPosition("first.md"));

        assertNotNull(left);
        assertTrue(left.editorTop() > 0 && left.editorTop() < caret, "the first line in sight, above the caret: " + left);
        assertTrue(left.editorOffset() >= 0 && left.editorOffset() < 100, "less than a line past its top: " + left);
        assertEquals(List.of(caret, caret), List.of(left.selectionAnchor(), left.selectionHead()));
        assertEquals(List.of(left.editorTop(), caret, caret),
                List.of(back.editorTop(), back.selectionAnchor(), back.selectionHead()));
        assertEquals(left.editorOffset(), back.editorOffset(), 2.0);
    }

    /** A note left at its very top shows at its very top again, the space above its first line included. */
    @Test
    void aNoteLeftAtItsTopShowsAtItsTopAgain() throws Exception {
        Assumptions.assumeTrue(fxRuntimeAvailable);
        String first = lines("first", 300);
        MarkdownEditorView editor = shownEditor(first);
        Thread.sleep(200);
        NotePosition left = FxTestSupport.onFx(() -> editor.viewPosition("first.md"));
        FxTestSupport.onFx(() -> {
            editor.setText(lines("second", 300));
            editor.scrollToPosition(4000);
            return null;
        });
        waitFor("the other note scrolled down", () -> scrollTop(editor) > 2000);

        FxTestSupport.onFx(() -> {
            editor.setText(first);
            editor.showViewPosition(left);
            return null;
        });
        Thread.sleep(300);

        assertEquals(0.0, FxTestSupport.onFx(() -> scrollTop(editor)));
    }

    /**
     * Going to a heading (a link to it, the outline) puts it at the top of the editor, as the
     * reading view does, not just somewhere in sight (at the bottom, coming from above).
     */
    @Test
    void goingToAHeadingPutsItAtTheTop() throws Exception {
        Assumptions.assumeTrue(fxRuntimeAvailable);
        String text = lines("before", 150) + "## Middle\n\n" + lines("after", 150);
        MarkdownEditorView editor = shownEditor(text);
        int heading = text.indexOf("## Middle");
        FxTestSupport.onFx(() -> {
            editor.scrollToPosition(heading);
            return null;
        });
        waitFor("scrolled down", () -> scrollTop(editor) > 2000);
        Thread.sleep(300);

        double fromTop = FxTestSupport.onFx(() -> {
            for (Node node : editor.lookupAll("*")) {
                if (node instanceof WebView web) {
                    return ((Number) web.getEngine().executeScript("(function(){"
                            + "var top=document.querySelector('.cm-scroller').getBoundingClientRect().top;"
                            + "var lines=document.querySelectorAll('.cm-line');"
                            + "for(var i=0;i<lines.length;i++){if(lines[i].textContent.indexOf('Middle')>=0)"
                            + "return lines[i].getBoundingClientRect().top-top;}return -1000;})()")).doubleValue();
                }
            }
            throw new AssertionError("no editor page");
        });

        assertTrue(fromTop >= 0 && fromTop <= 10, "the heading should be at the top of the editor, is at " + fromTop);
        assertEquals(heading, (int) FxTestSupport.onFx(editor::getCaretPosition));
    }

    /** An input method event as JavaFX gives it to the editor page: text being composed, text committed. */
    private static InputMethodEvent im(String composed, String committed) {
        List<InputMethodTextRun> runs = composed.isEmpty() ? List.of()
                : List.of(new InputMethodTextRun(composed, InputMethodHighlight.UNSELECTED_RAW));
        return new InputMethodEvent(InputMethodEvent.INPUT_METHOD_TEXT_CHANGED, runs, committed, composed.length());
    }

    /**
     * A dead key pressed again and again (the grave accent, to open a code block on a Spanish
     * keyboard) types one mark per press, as in any other app. On macOS, JavaFX gives the page
     * every other press as a new composition without committing the one pending, recorded
     * here as it came from a real keyboard: the page replaced the pending mark, and each such
     * press was lost.
     */
    @Test
    void aDeadKeyPressedAgainAndAgainTypesOneMarkPerPress() throws Exception {
        Assumptions.assumeTrue(fxRuntimeAvailable);
        MarkdownEditorView editor = shownEditor("");
        FxTestSupport.onFx(() -> {
            editor.requestFocus();
            return null;
        });
        Thread.sleep(300);
        List<InputMethodEvent> presses = List.of(
                im("`", ""),                  // 1st press: pending
                im("", "`"), im("`", ""),     // 2nd: the 1st committed, the 2nd pending
                im("`", ""),                  // 3rd: pending again, the 2nd never committed
                im("", "`"), im("`", ""),     // 4th
                im("`", ""),                  // 5th
                im("", "`"), im("", "\r"));  // Enter commits what is pending, then the line break, as recorded
        for (InputMethodEvent event : presses) {
            FxTestSupport.onFx(() -> {
                for (Node node : editor.lookupAll("*")) {
                    if (node instanceof WebView web) {
                        Event.fireEvent(web, event);
                    }
                }
                return null;
            });
            Thread.sleep(60);
        }
        Thread.sleep(300);

        String typed = FxTestSupport.onFx(editor::getText);
        assertEquals("`````\n", typed, "typed: " + typed.replace("\n", "\\n"));
    }

    /**
     * Text an input method composes and recomposes (Japanese, say), the same text twice too (the
     * caret moved in it), is not taken for repeated dead keys: nothing is committed before its time.
     */
    @Test
    void aCompositionThatChangesIsStillOneComposition() throws Exception {
        Assumptions.assumeTrue(fxRuntimeAvailable);
        MarkdownEditorView editor = shownEditor("");
        FxTestSupport.onFx(() -> {
            editor.requestFocus();
            return null;
        });
        Thread.sleep(300);
        for (InputMethodEvent event : List.of(im("k", ""), im("か", ""), im("かn", ""), im("かん", ""),
                im("かん", ""), im("", "缶"))) {
            FxTestSupport.onFx(() -> {
                for (Node node : editor.lookupAll("*")) {
                    if (node instanceof WebView web) {
                        Event.fireEvent(web, event);
                    }
                }
                return null;
            });
            Thread.sleep(60);
        }
        Thread.sleep(300);

        assertEquals("缶", FxTestSupport.onFx(editor::getText));
    }

    /** The circumflex macOS composes (a spacing modifier letter, not a symbol) repeats like the grave accent. */
    @Test
    void theCircumflexDeadKeyRepeatsToo() throws Exception {
        Assumptions.assumeTrue(fxRuntimeAvailable);
        MarkdownEditorView editor = shownEditor("");
        FxTestSupport.onFx(() -> {
            editor.requestFocus();
            return null;
        });
        Thread.sleep(300);
        for (InputMethodEvent event : List.of(im("ˆ", ""), im("", "ˆ"), im("ˆ", ""), im("ˆ", ""), im("", "ˆ"))) {
            FxTestSupport.onFx(() -> {
                for (Node node : editor.lookupAll("*")) {
                    if (node instanceof WebView web) {
                        Event.fireEvent(web, event);
                    }
                }
                return null;
            });
            Thread.sleep(60);
        }
        Thread.sleep(300);

        assertEquals("ˆˆˆ", FxTestSupport.onFx(editor::getText));
    }

    /**
     * On layouts such as U.S. International the apostrophe and the double quote are dead keys
     * too: pressed again and again they type one mark per press, like the accents.
     */
    @Test
    void theApostropheAndQuoteDeadKeysRepeatToo() throws Exception {
        Assumptions.assumeTrue(fxRuntimeAvailable);
        MarkdownEditorView editor = shownEditor("");
        FxTestSupport.onFx(() -> {
            editor.requestFocus();
            return null;
        });
        Thread.sleep(300);
        for (InputMethodEvent event : List.of(
                im("'", ""), im("", "'"), im("'", ""), im("'", ""), im("", "'"),
                im("\"", ""), im("", "\""), im("\"", ""), im("\"", ""), im("", "\""))) {
            FxTestSupport.onFx(() -> {
                for (Node node : editor.lookupAll("*")) {
                    if (node instanceof WebView web) {
                        Event.fireEvent(web, event);
                    }
                }
                return null;
            });
            Thread.sleep(60);
        }
        Thread.sleep(300);

        assertEquals("\'\'\'\"\"\"", FxTestSupport.onFx(editor::getText));
    }
}
