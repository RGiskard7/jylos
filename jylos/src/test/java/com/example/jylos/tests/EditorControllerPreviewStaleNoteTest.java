package com.example.jylos.tests;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import com.example.jylos.data.models.Note;
import com.example.jylos.plugin.PreviewContext;
import com.example.jylos.plugin.PreviewEnhancer;
import com.example.jylos.ui.controller.EditorController;

import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.layout.VBox;
import javafx.scene.web.WebView;
import javafx.stage.Stage;

/**
 * A render that blocks (in practice, reading an image or an embedded note from a cloud
 * folder such as iCloud that has not downloaded it yet) must not keep the preview of the
 * next note from appearing, and the preview must never show one note's content under
 * another note: reported on Windows with a vault in iCloud Drive, where opening note B
 * from a link showed note A's text in the preview while the editor showed B.
 */
class EditorControllerPreviewStaleNoteTest {

    private static final long WAIT_SECONDS = 5;

    @Test
    void aBlockedRenderOfTheOldNoteDoesNotDelayTheNewNotesPreview() throws Exception {
        Assumptions.assumeTrue(FxTestSupport.isFxRuntimeAvailable());
        Fixture fixture = Fixture.open();
        try {
            fixture.show(fixture.noteA);
            assertTrue(fixture.waitForPreview(body -> body.contains("Texto de A")),
                    "Note A should render normally first");

            // A re-render of A gets stuck, as when a plugin registration re-renders the
            // open note and that render waits on a cloud file.
            fixture.blocker.blockNote("a.md");
            fixture.refresh();
            assertTrue(fixture.blocker.awaitBlocked(), "the re-render of A should be stuck");

            fixture.show(fixture.noteB);
            assertTrue(fixture.waitForPreview(body -> body.contains("Texto de B")),
                    "Note B's preview must appear while A's stuck render is still running; preview was: "
                            + fixture.previewText());
        } finally {
            fixture.close();
        }
    }

    @Test
    void whileTheNewNoteIsStillRenderingThePreviewDoesNotShowTheOldNote() throws Exception {
        Assumptions.assumeTrue(FxTestSupport.isFxRuntimeAvailable());
        Fixture fixture = Fixture.open();
        try {
            fixture.show(fixture.noteA);
            assertTrue(fixture.waitForPreview(body -> body.contains("Texto de A")),
                    "Note A should render normally first");

            // B's own render is the slow one.
            fixture.blocker.blockNote("b.md");
            fixture.show(fixture.noteB);
            assertTrue(fixture.blocker.awaitBlocked(), "B's render should be stuck");

            assertTrue(fixture.waitForPreview(body -> !body.contains("Texto de A")),
                    "With note B open, the preview must not keep showing note A; preview was: "
                            + fixture.previewText());
            assertFalse(fixture.previewText().contains("Texto de B"), "B has not rendered yet");
        } finally {
            fixture.close();
        }
    }

    /** Blocks {@code transformHtml} for one note until released. */
    private static final class BlockingEnhancer implements PreviewEnhancer {
        private volatile String blockedNoteId;
        private final CountDownLatch blocked = new CountDownLatch(1);
        private final CountDownLatch release = new CountDownLatch(1);

        void blockNote(String noteId) {
            blockedNoteId = noteId;
        }

        boolean awaitBlocked() throws InterruptedException {
            return blocked.await(WAIT_SECONDS, TimeUnit.SECONDS);
        }

        void releaseAll() {
            blockedNoteId = null;
            release.countDown();
        }

        @Override
        public String transformHtml(PreviewContext context, String html) {
            String id = context != null && context.note() != null ? context.note().getId() : null;
            if (id != null && id.equals(blockedNoteId)) {
                blocked.countDown();
                try {
                    release.await(60, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    // A cancelled render is interrupted; a real blocking file read would
                    // ignore that, so keep waiting like one.
                    try {
                        release.await(60, TimeUnit.SECONDS);
                    } catch (InterruptedException ignored) {
                        Thread.currentThread().interrupt();
                    }
                }
            }
            return html;
        }
    }

    private static final class Fixture {
        final BlockingEnhancer blocker = new BlockingEnhancer();
        final Note noteA = note("a.md", "A", "Texto de A");
        final Note noteB = note("b.md", "B", "Texto de B");
        EditorController controller;
        WebView webView;
        Stage stage;

        static Fixture open() throws Exception {
            Fixture fixture = new Fixture();
            onFx(() -> {
                fixture.controller = new EditorController();
                fixture.webView = new WebView();
                VBox previewPane = new VBox(fixture.webView);
                set(fixture.controller, "previewWebView", fixture.webView);
                set(fixture.controller, "previewPane", previewPane);
                fixture.stage = new Stage();
                fixture.stage.setScene(new Scene(previewPane, 600, 400));
                fixture.stage.show();
                fixture.controller.registerPreviewEnhancer("blocker", fixture.blocker);
            });
            return fixture;
        }

        void show(Note note) throws Exception {
            onFx(() -> {
                set(controller, "currentNote", note);
                controller.refreshPreview(false);
            });
        }

        void refresh() throws Exception {
            onFx(() -> controller.refreshPreview(false));
        }

        String previewText() {
            AtomicReference<String> text = new AtomicReference<>("");
            try {
                onFx(() -> {
                    Object body = webView.getEngine().executeScript(
                            "document.body ? document.body.innerText : ''");
                    text.set(body != null ? body.toString() : "");
                });
            } catch (Exception e) {
                return "<unreadable: " + e + ">";
            }
            return text.get();
        }

        boolean waitForPreview(Predicate<String> condition) throws InterruptedException {
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(WAIT_SECONDS);
            while (System.nanoTime() < deadline) {
                if (condition.test(previewText())) {
                    return true;
                }
                Thread.sleep(50);
            }
            return false;
        }

        void close() throws Exception {
            blocker.releaseAll();
            onFx(() -> {
                controller.teardown();
                stage.close();
            });
        }

        private static Note note(String id, String title, String content) {
            Note note = new Note(title, content);
            note.setId(id);
            return note;
        }
    }

    private interface FxAction {
        void run() throws Exception;
    }

    private static void onFx(FxAction action) throws Exception {
        CountDownLatch done = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Platform.runLater(() -> {
            try {
                action.run();
            } catch (Throwable t) {
                failure.set(t);
            } finally {
                done.countDown();
            }
        });
        assertTrue(done.await(30, TimeUnit.SECONDS), "FX action timed out");
        if (failure.get() != null) {
            throw new AssertionError(failure.get());
        }
    }

    private static void set(Object target, String name, Object value) throws Exception {
        Field field = EditorController.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }
}
